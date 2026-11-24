package com.miniloan.service;

import com.miniloan.dao.ReportDao;
import com.miniloan.dao.TransactionManager;
import com.miniloan.model.BusinessException;
import com.miniloan.model.ReportQuery;
import com.miniloan.model.ReportResult;
import com.miniloan.utility.ConfigLoader;

import java.io.IOException;
import java.util.*;

/** Runs the 6 reports defined in reports.sql (re-read on every call, so SQL edits need no restart). */
public class ReportService {
    private final ConfigLoader config;
    private final TransactionManager txm;
    private final ReportDao reportDao;

    public ReportService(ConfigLoader config, TransactionManager txm, ReportDao reportDao) {
        this.config = config;
        this.txm = txm;
        this.reportDao = reportDao;
    }

    /** reportId ("1".."6") -> its query blocks, in file order. */
    public Map<String, List<ReportQuery>> groups() {
        try {
            Map<String, List<ReportQuery>> out = new LinkedHashMap<>();
            for (ReportQuery q : reportDao.loadQueries(config.reportsFile()))
                out.computeIfAbsent(q.reportId(), k -> new ArrayList<>()).add(q);
            return out;
        } catch (IOException e) {
            throw new BusinessException("Cannot read reports file '" + config.reportsFile().toAbsolutePath() + "': " + e.getMessage(), e);
        }
    }

    public List<String> distinctParams(List<ReportQuery> group) {
        Set<String> names = new LinkedHashSet<>();
        for (ReportQuery q : group) names.addAll(q.params());
        return new ArrayList<>(names);
    }

    public List<ReportResult> run(List<ReportQuery> group, Map<String, String> values) {
        return txm.read(c -> {
            List<ReportResult> results = new ArrayList<>();
            for (ReportQuery q : group) results.add(reportDao.execute(c, q, values));
            return results;
        });
    }
}
