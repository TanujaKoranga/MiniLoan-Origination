package com.miniloan.model;

/** Unchecked wrapper for SQLException thrown by the DAO layer. */
public class DataAccessException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public DataAccessException(String message, Throwable cause) { super(message, cause); }
}
