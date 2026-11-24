package com.miniloan.service;

import com.miniloan.dao.ApplicationHistoryDao;
import com.miniloan.model.ApplicationHistory;
import com.miniloan.model.ApplicationStatus;
import com.miniloan.model.InvalidStateTransitionException;
import com.miniloan.utility.BusinessDateProvider;

import java.sql.Connection;
import java.sql.SQLException;

/** Enforces the lifecycle and writes application_history on the CALLER'S transaction. */
public class ApplicationStateMachine {
    private final ApplicationHistoryDao historyDao;
    private final BusinessDateProvider clock;

    public ApplicationStateMachine(ApplicationHistoryDao historyDao, BusinessDateProvider clock) {
        this.historyDao = historyDao;
        this.clock = clock;
    }

    public void assertTransition(ApplicationStatus from, ApplicationStatus to) {
        if (!from.canTransitionTo(to)) throw new InvalidStateTransitionException(from, to);
    }

    public void log(Connection c, long applicationId, String action, ApplicationStatus oldS, ApplicationStatus newS,
                    String actor, String remarks) throws SQLException {
        String r = remarks != null && remarks.length() > 1000 ? remarks.substring(0, 1000) : remarks;
        historyDao.insert(c, new ApplicationHistory(null, applicationId, action, oldS, newS, actor, clock.now(), r));
    }
}
