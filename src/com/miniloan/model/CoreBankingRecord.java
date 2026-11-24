package com.miniloan.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CoreBankingRecord(Long coreId, long customerId, long applicationId, String customerName,
                                String address, LocalDate dob, String kycDetails,
                                BigDecimal approvedAmount, BigDecimal disbursedAmount, LocalDate disbursedDate) { }
