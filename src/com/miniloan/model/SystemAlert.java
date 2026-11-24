package com.miniloan.model;

import java.time.LocalDateTime;

public record SystemAlert(Long alertId, long applicationId, String alertType, LocalDateTime createdAt) {
    public static final String REVIEW_OVERDUE = "REVIEW_OVERDUE";
}
