package com.miniloan.model;

import java.math.BigDecimal;

/** Per-product business limits, loaded from config.properties by ConfigLoader (utility package). */
public record ProductLimits(Product product, BigDecimal minAmount, BigDecimal maxAmount,
                            int minTenure, int maxTenure, BigDecimal minMonthlyIncome,
                            BigDecimal annualInterestRate, boolean coBorrowerAllowed) { }
