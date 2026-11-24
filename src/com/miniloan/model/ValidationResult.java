package com.miniloan.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Collects EVERY failed rule (no fail-fast), so all reasons can be shown and persisted together. */
public class ValidationResult {
    private final List<String> failures = new ArrayList<>();

    public void fail(String reason) { failures.add(reason); }
    public boolean isValid() { return failures.isEmpty(); }
    public List<String> getFailures() { return Collections.unmodifiableList(failures); }
    /** Pipe-separated, suitable for loan_application.eligibility_reasons. */
    public String joined() { return String.join(" | ", failures); }
}
