package com.miniloan.model;

public class InvalidStateTransitionException extends BusinessException {
    private static final long serialVersionUID = 1L;
    public InvalidStateTransitionException(ApplicationStatus from, ApplicationStatus to) {
        super("Invalid state transition: " + from + " -> " + to
                + ". Allowed from " + from + ": "
                + (from.allowedNext().isEmpty() ? "none (terminal state)" : from.allowedNext()));
    }
}
