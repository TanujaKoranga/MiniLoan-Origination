package com.miniloan.service;

import com.miniloan.model.*;
import com.miniloan.utility.ConfigLoader;
import com.miniloan.utility.IndianCurrencyFormatter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.regex.Pattern;

/** Input validation (fails fast on bad input) and submission eligibility (collects ALL failures). */
public class ValidationService {
    private static final Pattern MOBILE = Pattern.compile("^[0-9]{10}$");
    private static final BigDecimal MAX_MONEY = new BigDecimal("10000000000000");   // DECIMAL(15,2) headroom
    private final ConfigLoader config;

    public ValidationService(ConfigLoader config) { this.config = config; }

    public static int ageOn(LocalDate dob, LocalDate on) { return Period.between(dob, on).getYears(); }

    public static void throwIfInvalid(ValidationResult vr) {
        if (vr.isValid()) return;
        StringBuilder sb = new StringBuilder("Validation failed:");
        for (String f : vr.getFailures()) sb.append("\n   - ").append(f);
        throw new BusinessException(sb.toString());
    }

    public void validateCustomerInput(String name, LocalDate dob, String mobile, String address, String kyc, LocalDate businessDate) {
        ValidationResult vr = new ValidationResult();
        if (name == null || name.isBlank()) vr.fail("Name is required.");
        else if (name.trim().length() > 100) vr.fail("Name must be at most 100 characters.");
        if (dob == null) vr.fail("Date of birth is required.");
        else if (dob.isAfter(businessDate)) vr.fail("Date of birth " + dob + " is in the future.");
        if (mobile == null || !MOBILE.matcher(mobile.trim()).matches()) vr.fail("Mobile number must be exactly 10 digits.");
        if (address != null && address.trim().length() > 255) vr.fail("Address must be at most 255 characters.");
        if (kyc != null && kyc.trim().length() > 255) vr.fail("KYC details must be at most 255 characters.");
        throwIfInvalid(vr);
    }

    public void validateDraftInputs(BigDecimal amount, int tenure, BigDecimal income, BigDecimal obligations) {
        ValidationResult vr = new ValidationResult();
        money(vr, "Requested amount", amount, false);
        if (tenure <= 0) vr.fail("Tenure must be a positive whole number of months.");
        money(vr, "Monthly income", income, false);
        if (obligations == null) vr.fail("Existing monthly obligations are required (use 0 if none).");
        else if (obligations.signum() < 0) vr.fail("Existing monthly obligations cannot be negative.");
        else money(vr, "Existing monthly obligations", obligations, true);
        throwIfInvalid(vr);
    }

    private void money(ValidationResult vr, String label, BigDecimal v, boolean zeroAllowed) {
        if (v == null) { vr.fail(label + " is required."); return; }
        if (!zeroAllowed && v.signum() <= 0) { vr.fail(label + " must be greater than 0."); return; }
        if (v.stripTrailingZeros().scale() > 2) vr.fail(label + " can have at most 2 decimal places.");
        if (v.compareTo(MAX_MONEY) >= 0) vr.fail(label + " is too large.");
    }

    /** Evaluates EVERY submission rule - no early exit - so all failed reasons are reported together. */
    public ValidationResult evaluateSubmission(LoanApplication app, Customer cust, LocalDate businessDate,
                                               ProductLimits lim, EmiFoirResult ef) {
        ValidationResult vr = new ValidationResult();

        if (cust.dateOfBirth().isAfter(businessDate)) {
            vr.fail("Date of birth " + cust.dateOfBirth() + " is in the future.");
        } else {
            int age = ageOn(cust.dateOfBirth(), businessDate);
            if (age < config.minAge() || age > config.maxAge())
                vr.fail("Age " + age + " on " + businessDate + " is outside the allowed " + config.minAge() + "-" + config.maxAge() + " years.");
        }
        BigDecimal amt = app.getRequestedAmount();
        if (amt.compareTo(lim.minAmount()) < 0 || amt.compareTo(lim.maxAmount()) > 0)
            vr.fail("Requested amount " + IndianCurrencyFormatter.format(amt) + " is outside the " + lim.product() + " range "
                    + IndianCurrencyFormatter.format(lim.minAmount()) + " - " + IndianCurrencyFormatter.format(lim.maxAmount()) + ".");
        int t = app.getTenureMonths();
        if (t < lim.minTenure() || t > lim.maxTenure())
            vr.fail("Tenure " + t + " months is outside the " + lim.product() + " range " + lim.minTenure() + " - " + lim.maxTenure() + " months.");
        if (app.getMonthlyIncome().compareTo(lim.minMonthlyIncome()) < 0)
            vr.fail("Monthly net income " + IndianCurrencyFormatter.format(app.getMonthlyIncome()) + " is below the " + lim.product()
                    + " minimum of " + IndianCurrencyFormatter.format(lim.minMonthlyIncome()) + ".");
        if (!lim.coBorrowerAllowed() && app.getCoBorrowerName() != null && !app.getCoBorrowerName().isBlank())
            vr.fail(lim.product() + " loans do not allow a co-borrower.");
        if (!ef.foirEligible())
            vr.fail("FOIR " + ef.foirPercentage() + "% exceeds the maximum of " + config.foirMaxPercent().setScale(2) + "% (EMI "
                    + IndianCurrencyFormatter.format(ef.emi()) + ", existing obligations "
                    + IndianCurrencyFormatter.format(app.getExistingMonthlyObligations()) + ").");
        return vr;
    }
}
