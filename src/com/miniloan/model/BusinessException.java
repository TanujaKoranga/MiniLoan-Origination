package com.miniloan.model;

/** Rule / validation violation that should be shown to the user as-is. */
public class BusinessException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public BusinessException(String message) { super(message); }
    public BusinessException(String message, Throwable cause) { super(message, cause); }
}
