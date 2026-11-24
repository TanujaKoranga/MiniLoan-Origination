package com.miniloan.model;

import java.time.LocalDateTime;

/** oldStatus is null for the initial creation entry. */
public record ApplicationHistory(Long historyId, long applicationId, String action,
                                 ApplicationStatus oldStatus, ApplicationStatus newStatus,
                                 String performedBy, LocalDateTime performedAt, String remarks) { }
