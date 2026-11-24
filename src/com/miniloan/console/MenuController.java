package com.miniloan.console;

import com.miniloan.dao.ReportDao;
import com.miniloan.model.*;
import com.miniloan.service.*;
import com.miniloan.utility.BusinessDateProvider;
import com.miniloan.utility.ConfigLoader;
import com.miniloan.utility.CoreDataPushUtility;
import com.miniloan.utility.OverdueReviewUtility;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Menu-driven console UI. Every option is wrapped so errors print a message and return to the menu. */
public class MenuController {
    private final ConsoleIO io;
    private final ConfigLoader config;
    private final BusinessDateProvider clock;
    private final CustomerService customers;
    private final ApplicationService applications;
    private final ReportService reports;
    private final MonitorService monitor;
    private final RollbackTestService rollbackTest;
    private final OverdueReviewUtility overdue;
    private final CoreDataPushUtility corePush;

    public MenuController(ConsoleIO io, ConfigLoader config, BusinessDateProvider clock, CustomerService customers,
                          ApplicationService applications, ReportService reports, MonitorService monitor,
                          RollbackTestService rollbackTest, OverdueReviewUtility overdue, CoreDataPushUtility corePush) {
        this.io = io;
        this.config = config;
        this.clock = clock;
        this.customers = customers;
        this.applications = applications;
        this.reports = reports;
        this.monitor = monitor;
        this.rollbackTest = rollbackTest;
        this.overdue = overdue;
        this.corePush = corePush;
    }

    public void run() {
        while (true) {
            printMenu();
            String choice = io.line("Choose an option: ").trim();
            if (choice.equals("0")) return;
            try {
                switch (choice) {
                    case "1" -> createCustomer();
                    case "2" -> searchCustomer();
                    case "3" -> createApplication();
                    case "4" -> editDraft();
                    case "5" -> submitApplication();
                    case "6" -> viewApplication();
                    case "7" -> listApplications();
                    case "8" -> approve();
                    case "9" -> reject();
                    case "10" -> disburse();
                    case "11" -> runOverdue();
                    case "12" -> runCorePush();
                    case "13" -> runReports();
                    case "14" -> businessDate();
                    case "15" -> runRollbackTest();
                    case "16" -> monitorViews();
                    case "17" -> { config.reload(); System.out.println("config.properties reloaded (limits, FOIR, schedule values)."); }
                    default -> System.out.println("Invalid option.");
                }
            } catch (InputClosedException e) {
                throw e;
            } catch (BusinessException e) {
                System.out.println("\nERROR: " + e.getMessage());
            } catch (DataAccessException e) {
                System.out.println("\nDATABASE ERROR: " + e.getMessage());
            } catch (RuntimeException e) {
                System.out.println("\nUNEXPECTED ERROR: " + e);
            }
        }
    }

    private void printMenu() {
        System.out.println("\n==================== MINI LOAN ORIGINATION ====================");
        System.out.println(" Business date: " + clock.today() + (clock.isOverridden() ? "  (set from menu)" : ""));
        System.out.println(" 1.  Create customer              9.  Reviewer: REJECT application");
        System.out.println(" 2.  Search customer             10.  Disburse APPROVED application");
        System.out.println(" 3.  Create loan application     11.  Run Overdue Utility now");
        System.out.println(" 4.  Edit DRAFT application      12.  Run Core Data Push now");
        System.out.println(" 5.  Submit application          13.  Reports (6 SQL reports)");
        System.out.println(" 6.  View application + history  14.  Set / clear business date");
        System.out.println(" 7.  List applications           15.  Controlled transaction rollback test");
        System.out.println(" 8.  Reviewer: APPROVE           16.  View utility runs / alerts / core records");
        System.out.println("                                 17.  Reload config.properties");
        System.out.println(" 0.  Exit");
        System.out.println("===============================================================");
    }

    // ------------------------------------------------------------------ customers
    private void createCustomer() {
        System.out.println("\n-- Create customer --");
        String name = io.required("Full name");
        LocalDate dob = io.date("Date of birth (yyyy-MM-dd)", null);
        String mobile = io.required("Mobile (10 digits)");
        String address = io.optional("Address (optional)");
        String kyc = io.optional("KYC details (optional)");
        Customer c = customers.create(name, dob, mobile, address, kyc);
        System.out.println("\nCustomer created:");
        ConsolePrinter.customer(c);
    }

    private void searchCustomer() {
        String term = io.required("Search by customer ID, name or mobile");
        List<Customer> found = customers.search(term);
        if (found.isEmpty()) System.out.println("No customers found."); else ConsolePrinter.customers(found);
    }

    // ------------------------------------------------------------------ application capture
    private void createApplication() {
        System.out.println("\n-- Create loan application (DRAFT) --");
        long customerId = io.longValue("Customer ID");
        Customer c = customers.require(customerId);
        System.out.println("  Customer: " + c.name() + " | mobile " + c.mobile() + " | DOB " + c.dateOfBirth());
        Product product = io.choice("Product (PERSONAL / EDUCATION)", Product.class, null);
        String branch = chooseBranch(null);
        BigDecimal amount = io.decimal("Requested amount", null);
        int tenure = io.integer("Tenure in months", null);
        BigDecimal income = io.decimal("Monthly net income", null);
        BigDecimal obligations = io.decimal("Existing monthly obligations", BigDecimal.ZERO);
        String co = io.optional("Co-borrower name (optional; not allowed for EDUCATION)");
        LoanApplication a = applications.createDraft(customerId, product, branch, amount, tenure, income, obligations, co);
        System.out.println("\nDraft created: " + a.getApplicationNumber() + " (id " + a.getApplicationId() + "), rate "
                + a.getAnnualInterestRate() + "% p.a. Use option 5 to submit it.");
    }

    private String chooseBranch(String def) {
        List<Branch> branches = applications.listBranches();
        System.out.println("  Branches:");
        for (Branch b : branches) System.out.println("    " + b.branchCode() + "  " + b.branchName());
        return io.text("Branch code", def).toUpperCase();
    }

    private void editDraft() {
        System.out.println("\n-- Edit DRAFT application --");
        LoanApplication a = applications.resolve(io.required("Application ID or number"));
        if (a.getStatus() != ApplicationStatus.DRAFT)
            throw new BusinessException(a.getApplicationNumber() + " is " + a.getStatus() + ". Only DRAFT applications can be edited.");
        System.out.println("  Press Enter to keep the value in [brackets].");
        String branch = chooseBranch(a.getBranchCode());
        BigDecimal amount = io.decimal("Requested amount", a.getRequestedAmount());
        int tenure = io.integer("Tenure in months", a.getTenureMonths());
        BigDecimal income = io.decimal("Monthly net income", a.getMonthlyIncome());
        BigDecimal obligations = io.decimal("Existing monthly obligations", a.getExistingMonthlyObligations());
        String co = io.line("Co-borrower [" + (a.getCoBorrowerName() == null ? "none" : a.getCoBorrowerName()) + "] (Enter = keep, '-' = remove): ").trim();
        String newCo = co.isEmpty() ? a.getCoBorrowerName() : (co.equals("-") ? null : co);
        applications.updateDraft(a.getApplicationId(), branch, amount, tenure, income, obligations, newCo);
        System.out.println("\nDraft " + a.getApplicationNumber() + " updated.");
    }

    // ------------------------------------------------------------------ submit / view / list
    private void submitApplication() {
        System.out.println("\n-- Submit application --");
        LoanApplication a = applications.resolve(io.required("Application ID or number"));
        LoanApplication r = applications.submit(a.getApplicationId());
        System.out.println("\n" + r.getApplicationNumber() + " -> " + r.getStatus());
        System.out.println("  EMI  : " + ConsolePrinter.money(r.getProposedEmi()));
        System.out.println("  FOIR : " + r.getFoirPercentage() + "%");
        if (r.getStatus() == ApplicationStatus.REJECTED) {
            System.out.println("  REJECTED on submission. Failed rules:");
            ConsolePrinter.reasons(r.getEligibilityReasons());
        } else {
            System.out.println("  Eligible - now UNDER_REVIEW (awaiting reviewer decision).");
        }
    }

    private void viewApplication() {
        LoanApplication a = applications.resolve(io.required("Application ID or number"));
        ConsolePrinter.applicationDetails(applications.getDetails(a.getApplicationId()));
    }

    private void listApplications() {
        Product p = io.optionalChoice("Filter by product (blank = all)", Product.class);
        ApplicationStatus s = io.optionalChoice("Filter by status (blank = all)", ApplicationStatus.class);
        List<LoanApplication> list = applications.list(p, s);
        if (list.isEmpty()) System.out.println("No applications found."); else ConsolePrinter.applicationList(list);
    }

    // ------------------------------------------------------------------ reviewer + disbursement
    private void approve() {
        System.out.println("\n-- Reviewer: approve --");
        LoanApplication a = applications.resolve(io.required("Application ID or number"));
        String reviewer = io.text("Reviewer code", config.defaultReviewerCode());
        LoanApplication r = applications.approve(a.getApplicationId(), reviewer);
        System.out.println("\n" + r.getApplicationNumber() + " -> APPROVED (amount and tenure unchanged).");
        System.out.println("  Application Form generated: " + r.getApplicationFormPath());
    }

    private void reject() {
        System.out.println("\n-- Reviewer: reject --");
        LoanApplication a = applications.resolve(io.required("Application ID or number"));
        String reviewer = io.text("Reviewer code", config.defaultReviewerCode());
        String reason = io.optional("Rejection reason (mandatory)");
        LoanApplication r = applications.reject(a.getApplicationId(), reviewer, reason);
        System.out.println("\n" + r.getApplicationNumber() + " -> REJECTED.");
    }

    private void disburse() {
        System.out.println("\n-- Simulated disbursement --");
        LoanApplication a = applications.resolve(io.required("Application ID or number"));
        String ref = io.optional("Disbursement reference (blank = auto-generate)");
        DisbursementReceipt r = applications.disburse(a.getApplicationId(), ref);
        ConsolePrinter.receipt(r);
        System.out.println("  (The Core Data Push utility will move it to CORE_BANKING_PUSH -> EXIT automatically.)");
    }

    // ------------------------------------------------------------------ utilities
    private void runOverdue() {
        System.out.println("\nRunning Overdue Review Utility as of business date " + clock.today() + " ...");
        UtilityRun r = overdue.runOnce();
        System.out.println("  Result             : " + r.result());
        System.out.println("  Pending (UNDER_REVIEW): " + r.applicationsProcessed());
        System.out.println("  New alerts created : " + r.alertsCreated());
        System.out.println("  CSV                : " + (overdue.getLastCsvPath() == null ? "-" : overdue.getLastCsvPath().toAbsolutePath()));
        if (r.errorMessage() != null) System.out.println("  Error              : " + r.errorMessage());
    }

    private void runCorePush() {
        System.out.println("\nRunning Core Data Push ...");
        Optional<UtilityRun> r = corePush.runOnce(true);
        r.ifPresent(x -> {
            System.out.println("  Result  : " + x.result());
            System.out.println("  Pushed  : " + x.applicationsProcessed());
            if (x.errorMessage() != null) System.out.println("  Errors  : " + x.errorMessage());
        });
    }

    // ------------------------------------------------------------------ reports
    private void runReports() {
        Map<String, List<ReportQuery>> groups = reports.groups();
        System.out.println("\n-- Reports --");
        for (Map.Entry<String, List<ReportQuery>> e : groups.entrySet())
            System.out.println("  " + e.getKey() + ". " + e.getValue().get(0).title());
        String id = io.required("Report number");
        List<ReportQuery> group = groups.get(id.trim());
        if (group == null) throw new BusinessException("Unknown report '" + id + "'.");
        Map<String, String> values = new HashMap<>();
        LocalDate today = clock.today();
        for (String p : reports.distinctParams(group)) {
            switch (p) {
                case "product" -> values.put(p, io.optional("Filter by product PERSONAL / EDUCATION (blank = all)"));
                case "status" -> values.put(p, io.optional("Filter by status (blank = all)"));
                case "granularity" -> values.put(p, io.text("Period: DAILY / MONTHLY / QUARTERLY", "MONTHLY"));
                case "from_date" -> values.put(p, io.date("From date (yyyy-MM-dd)", today.withDayOfMonth(1)).toString());
                case "to_date" -> values.put(p, io.date("To date (yyyy-MM-dd)", today).toString());
                case "as_of_date" -> values.put(p, io.date("As-of date (yyyy-MM-dd)", today).toString());
                default -> values.put(p, io.optional(p));
            }
        }
        for (ReportResult r : reports.run(group, values)) ConsolePrinter.report(r);
    }

    // ------------------------------------------------------------------ settings, tests, monitoring
    private void businessDate() {
        System.out.println("\nCurrent business date: " + clock.today() + (clock.isOverridden() ? " (menu override)" : " (config / today)"));
        String s = io.line("New date yyyy-MM-dd, or 'clear' to go back to config/today (Enter = no change): ").trim();
        if (s.isEmpty()) return;
        if (s.equalsIgnoreCase("clear")) { clock.clearOverride(); }
        else {
            try { clock.setOverride(LocalDate.parse(s)); }
            catch (RuntimeException e) { throw new BusinessException("Invalid date '" + s + "'. Use yyyy-MM-dd."); }
        }
        System.out.println("Business date is now " + clock.today());
    }

    private void runRollbackTest() {
        System.out.println("\nThis creates a clearly-named test customer + application and deliberately fails");
        System.out.println("a disbursement and a core-banking push to prove full rollback.");
        if (!io.confirm("Run the controlled rollback test?")) return;
        for (String line : rollbackTest.run()) System.out.println("  " + line);
    }

    private void monitorViews() {
        System.out.println("\n-- Recent utility runs --");
        ConsolePrinter.runs(monitor.recentRuns(10));
        System.out.println("\n-- System alerts --");
        ConsolePrinter.alerts(monitor.alerts());
        System.out.println("\n-- ng_core_banking --");
        ConsolePrinter.coreRecords(monitor.coreRecords());
    }
}
