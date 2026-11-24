package com.miniloan.utility;

import com.miniloan.dao.*;
import com.miniloan.model.PendingReviewRow;
import com.miniloan.model.SystemAlert;
import com.miniloan.model.UtilityRun;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Finds UNDER_REVIEW applications waiting MORE than overdue.days (business-date based; exactly 2 days is
 * not overdue, 3 is), inserts REVIEW_OVERDUE alerts idempotently (UNIQUE(application_id, alert_type)),
 * exports pending_reviews_YYYYMMDD.csv and logs the run in utility_run.
 * Safe to call repeatedly, from the scheduler or the console.
 */
public class OverdueReviewUtility {
    private final ConfigLoader config;
    private final BusinessDateProvider clock;
    private final TransactionManager txm;
    private final LoanApplicationDao appDao;
    private final SystemAlertDao alertDao;
    private final UtilityRunDao runDao;
    private final CsvExporter csv;
    private volatile Path lastCsvPath;

    public OverdueReviewUtility(ConfigLoader config, BusinessDateProvider clock, TransactionManager txm,
                                LoanApplicationDao appDao, SystemAlertDao alertDao, UtilityRunDao runDao, CsvExporter csv) {
        this.config = config;
        this.clock = clock;
        this.txm = txm;
        this.appDao = appDao;
        this.alertDao = alertDao;
        this.runDao = runDao;
        this.csv = csv;
    }

    public Path getLastCsvPath() { return lastCsvPath; }

    /** Never throws: failures are recorded in the returned/persisted UtilityRun. */
    public synchronized UtilityRun runOnce() {
        LocalDateTime started = clock.now();
        LocalDate businessDate = clock.today();
        int pending = 0;
        int[] newAlerts = {0};
        String result = UtilityRun.SUCCESS;
        String error = null;
        lastCsvPath = null;

        try {
            int overdueDays = config.overdueDays();
            // 1) alerts: one transaction, idempotent
            List<PendingReviewRow> rows = txm.inTransaction(conn -> {
                List<PendingReviewRow> r = appDao.findPendingReviews(conn, businessDate);
                for (PendingReviewRow row : r) {
                    if (row.daysWaiting() > overdueDays
                            && alertDao.insertIfAbsent(conn, row.applicationId(), SystemAlert.REVIEW_OVERDUE, clock.now())) {
                        newAlerts[0]++;
                    }
                }
                return r;
            });
            pending = rows.size();
            // 2) CSV (file I/O is outside the DB transaction)
            try {
                lastCsvPath = csv.exportPendingReviews(rows, overdueDays, businessDate, config.outputDir());
            } catch (IOException e) {
                result = UtilityRun.PARTIAL;
                error = "Alerts saved but CSV export failed: " + e.getMessage();
            }
        } catch (RuntimeException e) {
            result = UtilityRun.FAILED;
            error = e.getMessage();
        }

        UtilityRun run = new UtilityRun(null, UtilityRun.OVERDUE_REVIEW, started, clock.now(), result, pending, newAlerts[0], error);
        try {
            return txm.inTransaction(conn -> runDao.insert(conn, run));
        } catch (RuntimeException e) {
            System.err.println("Could not log utility_run: " + e.getMessage());
            return run;
        }
    }
}
