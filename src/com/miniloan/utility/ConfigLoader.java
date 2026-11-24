package com.miniloan.utility;

import com.miniloan.model.Product;
import com.miniloan.model.ProductLimits;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Properties;

/** Reads config.properties. Call reload() to pick up edits without restarting or recompiling. */
public class ConfigLoader {
    private final Path path;
    private volatile Properties props = new Properties();

    public ConfigLoader(Path path) {
        this.path = path;
        reload();
    }

    /** Uses -Dconfig.file=... or ./config.properties. */
    public static ConfigLoader fromDefaultLocation() {
        return new ConfigLoader(Path.of(System.getProperty("config.file", "config.properties")));
    }

    public final void reload() {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read config file '" + path.toAbsolutePath()
                    + "'. Run the app from the folder containing config.properties. (" + e.getMessage() + ")", e);
        }
        this.props = p;
    }

    public String get(String key) {
        String v = props.getProperty(key);
        if (v == null) throw new IllegalStateException("Missing config key: " + key);
        return v.trim();
    }

    public String get(String key, String def) {
        String v = props.getProperty(key);
        return (v == null || v.isBlank()) ? def : v.trim();
    }

    public int getInt(String key) {
        try { return Integer.parseInt(get(key)); }
        catch (NumberFormatException e) { throw new IllegalStateException("Config key " + key + " must be a whole number"); }
    }

    public BigDecimal getDecimal(String key) {
        try { return new BigDecimal(get(key)); }
        catch (NumberFormatException e) { throw new IllegalStateException("Config key " + key + " must be a number"); }
    }

    public boolean getBoolean(String key) { return Boolean.parseBoolean(get(key)); }

    // ------------- typed accessors -------------
    public ProductLimits productLimits(Product p) {
        String k = "product." + p.name() + ".";
        return new ProductLimits(p, getDecimal(k + "min.amount"), getDecimal(k + "max.amount"),
                getInt(k + "min.tenure"), getInt(k + "max.tenure"), getDecimal(k + "min.income"),
                getDecimal(k + "annual.rate"), getBoolean(k + "coborrower.allowed"));
    }

    public int minAge() { return getInt("age.min"); }
    public int maxAge() { return getInt("age.max"); }
    public BigDecimal foirMaxPercent() { return getDecimal("foir.max.percent"); }
    public int overdueDays() { return getInt("overdue.days"); }
    public String applicationNumberPrefix() { return get("application.number.prefix", "LN"); }
    public String defaultReviewerCode() { return get("reviewer.default.code", "REV001"); }
    public Path outputDir() { return Path.of(get("output.dir", "./output")); }
    public Path reportsFile() { return Path.of(get("reports.file", "reports.sql")); }
    public boolean schedulerEnabled() { return Boolean.parseBoolean(get("scheduler.enabled", "true")); }
    public LocalTime overdueScheduleTime() { return LocalTime.parse(get("overdue.schedule.time", "09:00")); }
    public int coreInitialDelaySeconds() { return getInt("coredata.initial.delay.seconds"); }
    public int corePollIntervalSeconds() { return getInt("coredata.poll.interval.seconds"); }

    /** Configured business date, or null when blank (meaning: use today). */
    public LocalDate configuredBusinessDate() {
        String v = get("business.date", "");
        if (v.isEmpty()) return null;
        try { return LocalDate.parse(v); }
        catch (DateTimeParseException e) { throw new IllegalStateException("business.date must be yyyy-MM-dd, got: " + v); }
    }
}
