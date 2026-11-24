package com.miniloan.dao;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Small null-safe JDBC helpers shared by the DAOs. */
final class Jdbc {
    private Jdbc() { }

    static LocalDateTime dt(ResultSet rs, String col) throws SQLException { return rs.getObject(col, LocalDateTime.class); }
    static LocalDate d(ResultSet rs, String col) throws SQLException { return rs.getObject(col, LocalDate.class); }

    static Boolean bool(ResultSet rs, String col) throws SQLException {
        boolean b = rs.getBoolean(col);
        return rs.wasNull() ? null : b;
    }

    static void setDt(PreparedStatement ps, int i, LocalDateTime v) throws SQLException {
        if (v == null) ps.setNull(i, Types.TIMESTAMP); else ps.setObject(i, v);
    }
    static void setStr(PreparedStatement ps, int i, String v) throws SQLException {
        if (v == null) ps.setNull(i, Types.VARCHAR); else ps.setString(i, v);
    }
    static void setDec(PreparedStatement ps, int i, BigDecimal v) throws SQLException {
        if (v == null) ps.setNull(i, Types.DECIMAL); else ps.setBigDecimal(i, v);
    }
    static void setBool(PreparedStatement ps, int i, Boolean v) throws SQLException {
        if (v == null) ps.setNull(i, Types.BOOLEAN); else ps.setBoolean(i, v);
    }

    /** MySQL error 1062 = duplicate entry (UNIQUE / PRIMARY violation). */
    static boolean isDuplicate(SQLException e) { return e.getErrorCode() == 1062; }

    static boolean messageHas(SQLException e, String fragment) {
        return e.getMessage() != null && e.getMessage().contains(fragment);
    }
}
