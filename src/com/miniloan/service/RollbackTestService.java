package com.miniloan.service;

import com.miniloan.dao.*;
import com.miniloan.model.*;
import com.miniloan.utility.BusinessDateProvider;
import com.miniloan.utility.CoreDataPushUtility;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Controlled Transaction Rollback Test (scenario 9). Creates its own clearly-named test customer, then:
 *   A) disbursement with an injected failure  -> nothing may change (status, disbursement, history)
 *   B) core-banking push with an injected failure -> nothing may change (status, ng_core_banking, history)
 *   C) both retried without failure -> they succeed (proves the rollback left no half-written state).
 * Test data stays in the DB (customer name starts with "ROLLBACK TEST").
 */
public class RollbackTestService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final BusinessDateProvider clock;
    private final TransactionManager txm;
    private final CustomerService customers;
    private final ApplicationService applications;
    private final CoreDataPushUtility corePush;
    private final LoanApplicationDao appDao;
    private final DisbursementDao disbursementDao;
    private final CoreBankingDao coreDao;
    private final ApplicationHistoryDao historyDao;

    public RollbackTestService(BusinessDateProvider clock, TransactionManager txm, CustomerService customers,
                               ApplicationService applications, CoreDataPushUtility corePush, LoanApplicationDao appDao,
                               DisbursementDao disbursementDao, CoreBankingDao coreDao, ApplicationHistoryDao historyDao) {
        this.clock = clock;
        this.txm = txm;
        this.customers = customers;
        this.applications = applications;
        this.corePush = corePush;
        this.appDao = appDao;
        this.disbursementDao = disbursementDao;
        this.coreDao = coreDao;
        this.historyDao = historyDao;
    }

    private record Snap(ApplicationStatus status, int history, boolean hasDisbursement, int disbursementsTotal,
                        boolean inCore, int coreTotal) { }

    private Snap snap(long appId) {
        return txm.read(c -> new Snap(
                appDao.findById(c, appId).orElseThrow().getStatus(),
                historyDao.countByApplication(c, appId),
                disbursementDao.findByApplicationId(c, appId).isPresent(),
                disbursementDao.countAll(c),
                coreDao.existsForApplication(c, appId),
                coreDao.countAll(c)));
    }

    public List<String> run() {
        List<String> out = new ArrayList<>();
        boolean[] ok = {true};
        String stamp = clock.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Customer cust = createTestCustomer(stamp);
        out.add("Created test customer #" + cust.customerId() + " " + cust.name() + " (mobile " + cust.mobile() + ")");

        String branch = applications.listBranches().get(0).branchCode();
        LoanApplication app = applications.createDraft(cust.customerId(), Product.PERSONAL, branch,
                new BigDecimal("100000"), 12, new BigDecimal("50000"), BigDecimal.ZERO, null);
        app = applications.submit(app.getApplicationId());
        check(out, ok, "Test application " + app.getApplicationNumber() + " submitted -> UNDER_REVIEW", app.getStatus() == ApplicationStatus.UNDER_REVIEW);
        if (app.getStatus() != ApplicationStatus.UNDER_REVIEW) return out;
        app = applications.approve(app.getApplicationId(), "ROLLBACK-TEST");
        long id = app.getApplicationId();
        check(out, ok, "Approved -> APPROVED (EMI " + app.getProposedEmi() + ")", app.getStatus() == ApplicationStatus.APPROVED);

        // ---- A) disbursement failure
        Snap a0 = snap(id);
        out.add("A) Disbursing with an INJECTED failure (after disbursement insert + status update + history write) ...");
        try {
            applications.disburse(id, null, true);
            check(out, ok, "   the injected failure should have aborted the transaction", false);
        } catch (RuntimeException e) {
            out.add("   caught as expected: " + e.getMessage());
        }
        Snap a1 = snap(id);
        check(out, ok, "   status still APPROVED", a1.status() == ApplicationStatus.APPROVED);
        check(out, ok, "   no disbursement row for the application", !a1.hasDisbursement());
        check(out, ok, "   total disbursement rows unchanged (" + a0.disbursementsTotal() + ")", a1.disbursementsTotal() == a0.disbursementsTotal());
        check(out, ok, "   no extra application_history row (" + a0.history() + ")", a1.history() == a0.history());

        // ---- A2) normal disbursement now works
        DisbursementReceipt r = applications.disburse(id, null);
        out.add("   retry without failure: disbursed, reference " + r.reference());
        Snap b0 = snap(id);
        check(out, ok, "   status DISBURSED, 1 disbursement row, +1 history row",
                b0.status() == ApplicationStatus.DISBURSED && b0.hasDisbursement() && b0.history() == a0.history() + 1);

        // ---- B) core push failure
        out.add("B) Core data push with an INJECTED failure (after ng_core_banking insert + first status change) ...");
        corePush.setSimulateFailure(true);
        try {
            Optional<UtilityRun> run = corePush.runOnce(true);
            out.add("   utility_run result: " + run.map(UtilityRun::result).orElse("n/a")
                    + (run.map(UtilityRun::errorMessage).orElse(null) != null ? " | " + run.get().errorMessage() : ""));
        } finally {
            corePush.setSimulateFailure(false);
        }
        Snap b1 = snap(id);
        check(out, ok, "   status still DISBURSED (not CORE_BANKING_PUSH / EXIT)", b1.status() == ApplicationStatus.DISBURSED);
        check(out, ok, "   no ng_core_banking row for the application", !b1.inCore());
        check(out, ok, "   ng_core_banking total unchanged (" + b0.coreTotal() + ")", b1.coreTotal() == b0.coreTotal());
        check(out, ok, "   no extra application_history row (" + b0.history() + ")", b1.history() == b0.history());

        // ---- C) retry works
        corePush.runOnce(true);
        Snap c1 = snap(id);
        check(out, ok, "C) retry without failure: status EXIT, exactly 1 core row, +2 history rows (CORE_BANKING_PUSH, EXIT)",
                c1.status() == ApplicationStatus.EXIT && c1.inCore() && c1.history() == b0.history() + 2);

        out.add(ok[0] ? "RESULT: ALL CHECKS PASSED - failed transactions left no partial data."
                      : "RESULT: SOME CHECKS FAILED - see FAIL lines above.");
        return out;
    }

    private Customer createTestCustomer(String stamp) {
        for (int i = 0; i < 20; i++) {
            String mobile = "9" + String.format("%09d", RANDOM.nextInt(1_000_000_000));
            try {
                return customers.create("ROLLBACK TEST " + stamp, clock.today().minusYears(35), mobile,
                        "Test Address", "TEST-KYC");
            } catch (BusinessException e) {
                if (!e.getMessage().contains("already registered")) throw e;
            }
        }
        throw new BusinessException("Could not allocate a unique mobile number for the test customer.");
    }

    private static void check(List<String> out, boolean[] ok, String text, boolean pass) {
        out.add((pass ? "[PASS] " : "[FAIL] ") + text);
        if (!pass) ok[0] = false;
    }
}
