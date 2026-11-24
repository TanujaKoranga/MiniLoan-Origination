package com.miniloan.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Disbursement(Long disbursementId, long applicationId, String reference,
                           BigDecimal amount, LocalDateTime disbursedAt) { }
