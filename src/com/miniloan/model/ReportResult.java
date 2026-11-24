package com.miniloan.model;

import java.util.List;

/** Raw result of a report query. BigDecimal cells should be shown with IndianCurrencyFormatter. */
public record ReportResult(String title, List<String> columns, List<List<Object>> rows) { }
