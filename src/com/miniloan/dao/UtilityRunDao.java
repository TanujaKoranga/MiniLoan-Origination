package com.miniloan.dao;

import com.miniloan.model.UtilityRun;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UtilityRunDao {

    public UtilityRun insert(Connection c, UtilityRun r) throws SQLException {
        String sql = "INSERT INTO utility_run (utility_name, started_at, completed_at, result, applications_processed, alerts_created, error_message) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, r.utilityName());
            Jdbc.setDt(ps, 2, r.startedAt());
            Jdbc.setDt(ps, 3, r.completedAt());
            ps.setString(4, r.result());
            ps.setInt(5, r.applicationsProcessed());
            ps.setInt(6, r.alertsCreated());
            Jdbc.setStr(ps, 7, r.errorMessage());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new UtilityRun(keys.getLong(1), r.utilityName(), r.startedAt(), r.completedAt(), r.result(),
                        r.applicationsProcessed(), r.alertsCreated(), r.errorMessage());
            }
        }
    }

    public List<UtilityRun> findRecent(Connection c, int limit) throws SQLException {
        String sql = "SELECT run_id, utility_name, started_at, completed_at, result, applications_processed, alerts_created, error_message "
                + "FROM utility_run ORDER BY run_id DESC LIMIT ?";
        List<UtilityRun> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new UtilityRun(rs.getLong(1), rs.getString(2), Jdbc.dt(rs, "started_at"), Jdbc.dt(rs, "completed_at"),
                            rs.getString(5), rs.getInt(6), rs.getInt(7), rs.getString(8)));
                }
            }
        }
        return out;
    }
}
