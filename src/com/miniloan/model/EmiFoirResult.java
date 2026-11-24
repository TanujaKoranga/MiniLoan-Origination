package com.miniloan.model;

import java.math.BigDecimal;

/** emi and foir are both scale 2, HALF_UP. */
public record EmiFoirResult(BigDecimal emi, BigDecimal foirPercentage, boolean foirEligible) { }
