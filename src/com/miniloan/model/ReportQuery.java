package com.miniloan.model;

import java.util.List;

/** One SQL block parsed from reports.sql. params has one name per '?', in bind order. */
public record ReportQuery(String reportId, int part, String title, List<String> params, String sql) { }
