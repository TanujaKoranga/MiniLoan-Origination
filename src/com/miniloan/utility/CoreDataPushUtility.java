package com.miniloan.utility;

import com.miniloan.dao.*;
import com.miniloan.model.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Pushes DISBURSED applications to ng_core_banking, then DISBURSED -> CORE_BANKING_PUSH -> EXIT.
 * ONE transaction PER application (insert core row + 2 status updates + 2 history rows), so a failure
 * rolls back that application completely and never blocks the others. Duplicates are impossible:
 * the candidate query excludes pushed applications and ng_core_banking.application_id is UNIQUE.
 */
public class CoreDataPushUtility {
    public static final String ACTOR = "SYSTEM:CORE_DATA_PUSH";

    private final BusinessDateProvider clock;
    private final TransactionManager txm;
    private final LoanApplicationDao appDao;
    private final CustomerDao customerDao;
    private final DisbursementDao disbursementDao;
    private final CoreBankingDao coreDao;
    private final ApplicationHistoryDao historyDao;
    private final UtilityRunDao runDao;
    private volatile boolean simulateFailure;

    public CoreDataPushUtility(BusinessDateProvider clock, TransactionManager txm, LoanApplicationDao appDao,
                               CustomerDao customerDao, DisbursementDao disbursementDao, CoreBankingDao coreDao,
                               ApplicationHistoryDao historyDao, UtilityRunDao runDao) {
        this.clock = clock;
        this.txm = txm;
        this.appDao = appDao;
        this.customerDao = customerDao;
        this.disbursementDao = disbursementDao;
        this.coreDao = coreDao;
        this.historyDao = historyDao;
        this.runDao = runDao;
    }

    /** TEST HOOK (rollback scenario): fail after the core row + first status change, before EXIT. */
    public void setSimulateFailure(boolean on) { this.simulateFailure = on; }

    /**
     * @param manualTrigger true = always log a utility_run row; false (scheduler) = log only when there
     *                      was something to push or something failed, so idle polls don't flood the table.
     */
    public synchronized Optional<UtilityRun> runOnce(boolean manualTrigger) {
        LocalDateTime started = clock.now();
        int pushed = 0, failed = 0;
        StringBuilder errors = new StringBuilder();
        int candidatesFound = 0;
        String result;
        try {
            List<Long> ids = txm.read(conn -> appDao.findDisbursedNotPushedIds(conn));
            candidatesFound = ids.size();
            for (long id : ids) {
                try {
                    Boolean ok = txm.inTransaction(conn -> pushOne(conn, id));
                    if (Boolean.TRUE.equals(ok)) pushed++;
                } catch (RuntimeException e) {
                    failed++;
                    errors.append("App ").append(id).append(": ").append(e.getMessage()).append("; ");
                }
            }
            result = failed == 0 ? UtilityRun.SUCCESS : (pushed > 0 ? UtilityRun.PARTIAL : UtilityRun.FAILED);
        } catch (RuntimeException e) {
            result = UtilityRun.FAILED;
            errors.append(e.getMessage());
            failed = Math.max(failed, 1);
        }

        if (!manualTrigger && candidatesFound == 0 && failed == 0) return Optional.empty();

        UtilityRun run = new UtilityRun(null, UtilityRun.CORE_DATA_PUSH, started, clock.now(), result, pushed, 0,
                errors.length() == 0 ? null : errors.toString());
        try {
            return Optional.of(txm.inTransaction(conn -> runDao.insert(conn, run)));
        } catch (RuntimeException e) {
            System.err.println("Could not log utility_run: " + e.getMessage());
            return Optional.of(run);
        }
    }

    private boolean pushOne(Connection conn, long appId) throws SQLException {
        LoanApplication app = appDao.findByIdForUpdate(conn, appId)
                .orElseThrow(() -> new IllegalStateException("Application not found: " + appId));
        if (app.getStatus() != ApplicationStatus.DISBURSED) return false;          // changed meanwhile
        if (coreDao.existsForApplication(conn, appId)) return false;               // already pushed
        Customer cust = customerDao.findById(conn, app.getCustomerId())
                .orElseThrow(() -> new IllegalStateException("Customer missing for application " + appId));
        Disbursement disb = disbursementDao.findByApplicationId(conn, appId)
                .orElseThrow(() -> new IllegalStateException("No disbursement record for application " + appId));

        CoreBankingRecord rec = new CoreBankingRecord(null, cust.customerId(), appId, cust.name(), cust.address(),
                cust.dateOfBirth(), cust.kycDetails(), app.getRequestedAmount(), disb.amount(),
                disb.disbursedAt().toLocalDate());
        if (!coreDao.insertIfAbsent(conn, rec)) return false;

        transition(conn, appId, ApplicationStatus.DISBURSED, ApplicationStatus.CORE_BANKING_PUSH,
                "CORE_BANKING_PUSH", "Pushed to ng_core_banking");
        if (simulateFailure)
            throw new IllegalStateException("Simulated failure after core insert (transaction will roll back)");
        transition(conn, appId, ApplicationStatus.CORE_BANKING_PUSH, ApplicationStatus.EXIT,
                "EXIT", "Automatic exit after successful core banking push");
        return true;
    }

    private void transition(Connection conn, long appId, ApplicationStatus from, ApplicationStatus to,
                            String action, String remarks) throws SQLException {
        if (!from.canTransitionTo(to)) throw new InvalidStateTransitionException(from, to);
        if (!appDao.updateStatus(conn, appId, from, to))
            throw new IllegalStateException("Concurrent modification: application " + appId + " was not in " + from);
        historyDao.insert(conn, new ApplicationHistory(null, appId, action, from, to, ACTOR, clock.now(), remarks));
    }
}
