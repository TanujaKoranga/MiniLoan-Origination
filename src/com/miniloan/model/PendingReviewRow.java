package com.miniloan.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** UNDER_REVIEW application plus how long it has been waiting (as of the business date). */
public record PendingReviewRow(long applicationId, String applicationNumber, String customerName,
                               Product product, BigDecimal requestedAmount,
                               LocalDateTime submittedAt, int daysWaiting) { }
