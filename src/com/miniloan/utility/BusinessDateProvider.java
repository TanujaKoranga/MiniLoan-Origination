package com.miniloan.utility;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Business date = menu override, else config business.date, else today.
 * Business timestamps = business date + current wall-clock time.
 */
public class BusinessDateProvider {
    private final ConfigLoader config;
    private volatile LocalDate override;

    public BusinessDateProvider(ConfigLoader config) { this.config = config; }

    public LocalDate today() {
        LocalDate o = override;
        if (o != null) return o;
        LocalDate c = config.configuredBusinessDate();
        return c != null ? c : LocalDate.now();
    }

    public LocalDateTime now() { return today().atTime(LocalTime.now()); }

    public void setOverride(LocalDate date) { this.override = date; }
    public void clearOverride() { this.override = null; }
    public boolean isOverridden() { return override != null; }
}
