package com.miniloan.service;

import com.miniloan.dao.*;
import com.miniloan.model.CoreBankingRecord;
import com.miniloan.model.SystemAlert;
import com.miniloan.model.UtilityRun;

import java.util.List;

/** Read-only views for utility runs, system alerts and ng_core_banking rows. */
public class MonitorService {
    private final TransactionManager txm;
    private final UtilityRunDao runDao;
    private final SystemAlertDao alertDao;
    private final CoreBankingDao coreDao;

    public MonitorService(TransactionManager txm, UtilityRunDao runDao, SystemAlertDao alertDao, CoreBankingDao coreDao) {
        this.txm = txm;
        this.runDao = runDao;
        this.alertDao = alertDao;
        this.coreDao = coreDao;
    }

    public List<UtilityRun> recentRuns(int limit) { return txm.read(c -> runDao.findRecent(c, limit)); }
    public List<SystemAlert> alerts() { return txm.read(alertDao::findAll); }
    public List<CoreBankingRecord> coreRecords() { return txm.read(coreDao::findAll); }
}
