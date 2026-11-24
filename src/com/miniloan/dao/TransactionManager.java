package com.miniloan.dao;

import com.miniloan.model.DataAccessException;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Runs work in one ACID transaction: setAutoCommit(false) -> commit(), or rollback() on ANY exception.
 * DAO methods never open connections; they receive the Connection from here.
 */
public class TransactionManager {

    @FunctionalInterface public interface SqlWork<T> { T run(Connection conn) throws SQLException; }
    @FunctionalInterface public interface SqlRunnable { void run(Connection conn) throws SQLException; }

    private final DbConnection db;

    public TransactionManager(DbConnection db) { this.db = db; }

    /** Transactional work that returns a value. */
    public <T> T inTransaction(SqlWork<T> work) {
        Connection conn = null;
        try {
            conn = db.getConnection();
            conn.setAutoCommit(false);
            T result = work.run(conn);
            conn.commit();
            return result;
        } catch (SQLException e) {
            rollbackQuietly(conn);
            throw new DataAccessException("Database error (transaction rolled back): " + e.getMessage(), e);
        } catch (RuntimeException e) {
            rollbackQuietly(conn);
            throw e;
        } finally {
            closeQuietly(conn);
        }
    }

    /** Transactional work with no return value. */
    public void runInTransaction(SqlRunnable work) {
        inTransaction(conn -> { work.run(conn); return null; });
    }

    /** Read-only / single-statement work on an auto-commit connection. */
    public <T> T read(SqlWork<T> work) {
        Connection conn = null;
        try {
            conn = db.getConnection();
            return work.run(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database error: " + e.getMessage(), e);
        } finally {
            closeQuietly(conn);
        }
    }

    private static void rollbackQuietly(Connection c) {
        if (c == null) return;
        try { c.rollback(); } catch (SQLException ignored) { }
    }

    private static void closeQuietly(Connection c) {
        if (c == null) return;
        try { c.close(); } catch (SQLException ignored) { }
    }
}
