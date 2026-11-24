package com.miniloan.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DisbursementReceipt(String applicationNumber, String customerName, BigDecimal amount,
                                  String reference, LocalDateTime disbursedAt) { }
