package com.miniloan.utility;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Indian digit grouping: 1,00,000.00 / 12,34,567.89 (2 decimals, HALF_UP). */
public final class IndianCurrencyFormatter {
    private IndianCurrencyFormatter() { }

    public static String format(BigDecimal amount) { return amount == null ? "-" : "INR " + plain(amount); }

    public static String plain(BigDecimal amount) {
        if (amount == null) return "-";
        BigDecimal v = amount.setScale(2, RoundingMode.HALF_UP);
        boolean negative = v.signum() < 0;
        String s = v.abs().toPlainString();
        int dot = s.indexOf('.');
        String intPart = s.substring(0, dot);
        String frac = s.substring(dot);
        String grouped;
        if (intPart.length() <= 3) {
            grouped = intPart;
        } else {
            String last3 = intPart.substring(intPart.length() - 3);
            String rest = intPart.substring(0, intPart.length() - 3);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < rest.length(); i++) {
                sb.append(rest.charAt(i));
                int remaining = rest.length() - i - 1;
                if (remaining > 0 && remaining % 2 == 0) sb.append(',');
            }
            grouped = sb + "," + last3;
        }
        return (negative ? "-" : "") + grouped + frac;
    }
}
