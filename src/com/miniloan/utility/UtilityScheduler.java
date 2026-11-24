package com.miniloan.utility;

import com.miniloan.model.UtilityRun;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ScheduledExecutorService wrapper: Overdue utility once a day at overdue.schedule.time (wall clock),
 * CoreDataPush every coredata.poll.interval.seconds (fixed delay, so runs never overlap).
 * Daemon threads, so they never keep the JVM alive after the menu exits.
 */
public class UtilityScheduler {
    private final ConfigLoader config;
    private final OverdueReviewUtility overdue;
    private final CoreDataPushUtility corePush;
    private ScheduledExecutorService executor;

    public UtilityScheduler(ConfigLoader config, OverdueReviewUtility overdue, CoreDataPushUtility corePush) {
        this.config = config;
        this.overdue = overdue;
        this.corePush = corePush;
    }

    public synchronized void start() {
        if (executor != null) return;
        if (!config.schedulerEnabled()) {
            System.out.println("[Scheduler] disabled (scheduler.enabled=false). Use the manual menu options.");
            return;
        }
        executor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "utility-scheduler");
            t.setDaemon(true);
            return t;
        });
        LocalTime at = config.overdueScheduleTime();
        long delay = secondsUntilNext(at);
        executor.scheduleAtFixedRate(this::safeOverdue, delay, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
        executor.scheduleWithFixedDelay(this::safeCore, config.coreInitialDelaySeconds(),
                config.corePollIntervalSeconds(), TimeUnit.SECONDS);
        System.out.println("[Scheduler] Overdue utility daily at " + at + " | CoreDataPush every "
                + config.corePollIntervalSeconds() + "s");
    }

    public synchronized void stop() {
        if (executor == null) return;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        executor = null;
    }

    // Exceptions must never escape a scheduled task, or the executor silently stops rescheduling it.
    private void safeOverdue() {
        try {
            UtilityRun r = overdue.runOnce();
            System.out.println("\n[Scheduler] Overdue utility: " + r.result() + ", pending=" + r.applicationsProcessed()
                    + ", new alerts=" + r.alertsCreated());
        } catch (Throwable t) {
            System.err.println("[Scheduler] Overdue utility error: " + t);
        }
    }

    private void safeCore() {
        try {
            corePush.runOnce(false).ifPresent(r -> System.out.println("\n[Scheduler] CoreDataPush: " + r.result()
                    + ", pushed=" + r.applicationsProcessed() + (r.errorMessage() != null ? ", errors=" + r.errorMessage() : "")));
        } catch (Throwable t) {
            System.err.println("[Scheduler] CoreDataPush error: " + t);
        }
    }

    static long secondsUntilNext(LocalTime at) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = now.toLocalDate().atTime(at);
        if (!next.isAfter(now)) next = next.plusDays(1);
        return Duration.between(now, next).getSeconds();
    }
}
