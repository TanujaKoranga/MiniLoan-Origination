package com.miniloan.console;

import com.miniloan.model.*;
import com.miniloan.utility.IndianCurrencyFormatter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** All formatted console output. Currency always uses Indian grouping. */
public final class ConsolePrinter {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private ConsolePrinter() { }

    static String ts(LocalDateTime t) { return t == null ? "-" : t.format(TS); }
    static String money(BigDecimal v) { return v == null ? "-" : IndianCurrencyFormatter.format(v); }
    private static String nz(Object o) { return o == null ? "-" : o.toString(); }
    private static void kv(String k, Object v) { System.out.printf("  %-26s: %s%n", k, nz(v)); }

    public static void table(List<String> headers, List<List<String>> rows) {
        int[] w = new int[headers.size()];
        for (int i = 0; i < w.length; i++) w[i] = headers.get(i).length();
        for (List<String> r : rows) for (int i = 0; i < w.length; i++) w[i] = Math.max(w[i], r.get(i).length());
        StringBuilder line = new StringBuilder("  ");
        for (int i = 0; i < w.length; i++) line.append(String.format("%-" + w[i] + "s", headers.get(i))).append("  ");
        System.out.println(line);
        System.out.println("  " + "-".repeat(Math.max(1, line.length() - 2)));
        for (List<String> r : rows) {
            StringBuilder sb = new StringBuilder("  ");
            for (int i = 0; i < w.length; i++) sb.append(String.format("%-" + w[i] + "s", r.get(i))).append("  ");
            System.out.println(sb);
        }
        System.out.println("  (" + rows.size() + " row" + (rows.size() == 1 ? "" : "s") + ")");
    }

    public static void customer(Customer c) {
        kv("Customer ID", c.customerId());
        kv("Name", c.name());
        kv("Date of birth", c.dateOfBirth());
        kv("Mobile", c.mobile());
        kv("Address", c.address());
        kv("KYC details", c.kycDetails());
    }

    public static void customers(List<Customer> list) {
        List<List<String>> rows = new ArrayList<>();
        for (Customer c : list) rows.add(List.of(String.valueOf(c.customerId()), c.name(), c.mobile(), c.dateOfBirth().toString(), c.address()));
        table(List.of("ID", "Name", "Mobile", "DOB", "Address"), rows);
    }

    public static void applicationList(List<LoanApplication> list) {
        List<List<String>> rows = new ArrayList<>();
        for (LoanApplication a : list)
            rows.add(List.of(String.valueOf(a.getApplicationId()), a.getApplicationNumber(), String.valueOf(a.getCustomerId()),
                    a.getProduct().name(), IndianCurrencyFormatter.plain(a.getRequestedAmount()), String.valueOf(a.getTenureMonths()),
                    a.getProposedEmi() == null ? "-" : IndianCurrencyFormatter.plain(a.getProposedEmi()), a.getStatus().name()));
        table(List.of("ID", "Application No", "Cust", "Product", "Amount", "Months", "EMI", "Status"), rows);
    }

    public static void applicationDetails(ApplicationDetails d) {
        LoanApplication a = d.application();
        Customer c = d.customer();
        System.out.println("\n=== Application " + a.getApplicationNumber() + " (id " + a.getApplicationId() + ") ===");
        kv("Status", a.getStatus());
        kv("Customer", "#" + c.customerId() + " " + c.name() + " | mobile " + c.mobile() + " | DOB " + c.dateOfBirth());
        kv("Product", a.getProduct() + " @ " + a.getAnnualInterestRate() + "% p.a.");
        kv("Branch", a.getBranchCode() + " - " + a.getBranchName());
        kv("Requested amount", money(a.getRequestedAmount()));
        kv("Tenure", a.getTenureMonths() + " months");
        kv("Monthly net income", money(a.getMonthlyIncome()));
        kv("Existing obligations", money(a.getExistingMonthlyObligations()));
        kv("Co-borrower", a.getCoBorrowerName());
        kv("Proposed EMI", money(a.getProposedEmi()));
        kv("FOIR", a.getFoirPercentage() == null ? null : a.getFoirPercentage() + "%");
        kv("Eligible", a.getEligible() == null ? "not evaluated yet" : (a.getEligible() ? "YES" : "NO"));
        kv("Eligibility notes", a.getEligibilityReasons());
        kv("Created at", ts(a.getCreatedAt()));
        kv("Submitted at", ts(a.getSubmittedAt()));
        kv("Decided at", ts(a.getDecidedAt()));
        kv("Reviewer", a.getReviewerCode());
        kv("Decision reason", a.getDecisionReason());
        kv("Application form", a.getApplicationFormPath());
        if (d.disbursement() != null) {
            Disbursement x = d.disbursement();
            kv("Disbursement", money(x.amount()) + " | ref " + x.reference() + " | " + ts(x.disbursedAt()));
        }
        System.out.println("\n  --- Audit history ---");
        List<List<String>> rows = new ArrayList<>();
        for (ApplicationHistory h : d.history())
            rows.add(List.of(ts(h.performedAt()), h.action(), h.oldStatus() == null ? "-" : h.oldStatus().name(),
                    h.newStatus().name(), h.performedBy(), nz(h.remarks())));
        table(List.of("When", "Action", "From", "To", "By", "Remarks"), rows);
    }

    public static void reasons(String reasons) {
        if (reasons == null) return;
        for (String r : reasons.split(" \\| ")) System.out.println("   - " + r);
    }

    public static void receipt(DisbursementReceipt r) {
        System.out.println("\n=== DISBURSEMENT CONFIRMATION ===");
        kv("Application number", r.applicationNumber());
        kv("Customer", r.customerName());
        kv("Amount disbursed", money(r.amount()));
        kv("Reference", r.reference());
        kv("Date", ts(r.disbursedAt()));
    }

    public static void report(ReportResult r) {
        System.out.println("\n### " + r.title());
        List<List<String>> rows = new ArrayList<>();
        for (List<Object> row : r.rows()) {
            List<String> cells = new ArrayList<>();
            for (Object o : row) cells.add(o == null ? "-" : (o instanceof BigDecimal bd ? IndianCurrencyFormatter.plain(bd) : o.toString()));
            rows.add(cells);
        }
        table(r.columns(), rows);
    }

    public static void runs(List<UtilityRun> runs) {
        List<List<String>> rows = new ArrayList<>();
        for (UtilityRun u : runs)
            rows.add(List.of(String.valueOf(u.runId()), u.utilityName(), ts(u.startedAt()), u.result(),
                    String.valueOf(u.applicationsProcessed()), String.valueOf(u.alertsCreated()), nz(u.errorMessage())));
        table(List.of("Run", "Utility", "Started", "Result", "Processed", "Alerts", "Error"), rows);
    }

    public static void alerts(List<SystemAlert> alerts) {
        List<List<String>> rows = new ArrayList<>();
        for (SystemAlert a : alerts)
            rows.add(List.of(String.valueOf(a.alertId()), String.valueOf(a.applicationId()), a.alertType(), ts(a.createdAt())));
        table(List.of("Alert", "Application ID", "Type", "Created"), rows);
    }

    public static void coreRecords(List<CoreBankingRecord> list) {
        List<List<String>> rows = new ArrayList<>();
        for (CoreBankingRecord r : list)
            rows.add(List.of(String.valueOf(r.coreId()), String.valueOf(r.applicationId()), r.customerName(), String.valueOf(r.dob()),
                    IndianCurrencyFormatter.plain(r.approvedAmount()), IndianCurrencyFormatter.plain(r.disbursedAmount()), String.valueOf(r.disbursedDate())));
        table(List.of("Core", "App ID", "Customer", "DOB", "Approved", "Disbursed", "Date"), rows);
    }
}
