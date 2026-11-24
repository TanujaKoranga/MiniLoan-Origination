package com.miniloan.model;

/** Supported loan products. Limits are NOT hard-coded; see ProductLimits / config.properties. */
public enum Product {
    PERSONAL("Personal Loan"),
    EDUCATION("Education Loan");

    private final String displayName;

    Product(String displayName) { this.displayName = displayName; }

    public String getDisplayName() { return displayName; }

    public static Product fromString(String s) {
        if (s == null) throw new BusinessException("Product is required (PERSONAL / EDUCATION).");
        try {
            return Product.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Unknown product '" + s + "'. Allowed: PERSONAL, EDUCATION.");
        }
    }
}
