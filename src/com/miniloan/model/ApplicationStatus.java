package com.miniloan.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Application lifecycle states and the single source of truth for the state machine. */
public enum ApplicationStatus {
    DRAFT, UNDER_REVIEW, APPROVED, REJECTED, DISBURSED, CORE_BANKING_PUSH, EXIT;

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED =
            new EnumMap<>(ApplicationStatus.class);

    /** Statuses that count as "active" for the one-active-application-per-product rule. */
    public static final Set<ApplicationStatus> ACTIVE =
            EnumSet.of(DRAFT, UNDER_REVIEW, APPROVED);

    static {
        ALLOWED.put(DRAFT,             EnumSet.of(UNDER_REVIEW, REJECTED));
        ALLOWED.put(UNDER_REVIEW,      EnumSet.of(APPROVED, REJECTED));
        ALLOWED.put(APPROVED,          EnumSet.of(DISBURSED));
        ALLOWED.put(DISBURSED,         EnumSet.of(CORE_BANKING_PUSH));   // system (utility) only
        ALLOWED.put(CORE_BANKING_PUSH, EnumSet.of(EXIT));                // system (utility) only
        ALLOWED.put(REJECTED,          EnumSet.noneOf(ApplicationStatus.class));
        ALLOWED.put(EXIT,              EnumSet.noneOf(ApplicationStatus.class));
    }

    public Set<ApplicationStatus> allowedNext() { return ALLOWED.get(this); }

    public boolean canTransitionTo(ApplicationStatus next) { return ALLOWED.get(this).contains(next); }

    public boolean isActive() { return ACTIVE.contains(this); }

    /** Financial details (amount/tenure/income/...) may be edited only in DRAFT. */
    public boolean isEditable() { return this == DRAFT; }

    /** No further MANUAL action possible (DISBURSED is moved on only by the CoreDataPush utility). */
    public boolean isTerminalForManualActions() {
        return this == REJECTED || this == DISBURSED || this == CORE_BANKING_PUSH || this == EXIT;
    }
}
