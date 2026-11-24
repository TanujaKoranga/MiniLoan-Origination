package com.miniloan.service;

import com.miniloan.model.EmiFoirResult;
import com.miniloan.utility.ConfigLoader;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * EMI = P*r*(1+r)^n / ((1+r)^n - 1), r = annualRate/12/100. All BigDecimal; 40 digits of intermediate
 * precision, results rounded HALF_UP to scale 2.  FOIR = (obligations + ROUNDED emi) / income * 100.
 */
public class EmiFoirCalculator {
    private static final MathContext MC = new MathContext(40, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private final ConfigLoader config;

    public EmiFoirCalculator(ConfigLoader config) { this.config = config; }

    public static BigDecimal emi(BigDecimal principal, BigDecimal annualRatePercent, int months) {
        BigDecimal r = annualRatePercent.divide(BigDecimal.valueOf(1200), MC);
        if (r.signum() == 0) return principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        BigDecimal pow = BigDecimal.ONE.add(r).pow(months, MC);
        BigDecimal emi = principal.multiply(r, MC).multiply(pow, MC).divide(pow.subtract(BigDecimal.ONE), MC);
        return emi.setScale(2, RoundingMode.HALF_UP);
    }

    public EmiFoirResult calculate(BigDecimal principal, BigDecimal annualRatePercent, int months,
                                   BigDecimal existingObligations, BigDecimal monthlyIncome) {
        BigDecimal emi = emi(principal, annualRatePercent, months);
        BigDecimal foir = existingObligations.add(emi).divide(monthlyIncome, MC).multiply(HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);
        boolean ok = foir.compareTo(config.foirMaxPercent()) <= 0;       // 50.00 passes, 50.01 fails
        return new EmiFoirResult(emi, foir, ok);
    }
}
