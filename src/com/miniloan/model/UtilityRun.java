package com.miniloan.model;

import java.time.LocalDateTime;

public record UtilityRun(Long runId, String utilityName, LocalDateTime startedAt, LocalDateTime completedAt,
                         String result, int applicationsProcessed, int alertsCreated, String errorMessage) {
    public static final String OVERDUE_REVIEW = "OVERDUE_REVIEW_UTILITY";
    public static final String CORE_DATA_PUSH = "CORE_DATA_PUSH_UTILITY";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED  = "FAILED";
    public static final String PARTIAL = "PARTIAL";
}
