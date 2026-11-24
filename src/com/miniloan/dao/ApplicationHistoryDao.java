package com.miniloan.dao;

import com.miniloan.model.ApplicationHistory;
import com.miniloan.model.ApplicationStatus;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ApplicationHistoryDao {

    public void insert(Connection c, ApplicationHistory h) throws SQLException {
        String sql = "INSERT INTO application_history (application_id, action, old_status, new_status, performed_by, performed_at, remarks) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, h.applicationId());
            ps.setString(2, h.action());
            Jdbc.setStr(ps, 3, h.oldStatus() == null ? null : h.oldStatus().name());
            ps.setString(4, h.newStatus().name());
            ps.setString(5, h.performedBy());
            Jdbc.setDt(ps, 6, h.performedAt());
            Jdbc.setStr(ps, 7, h.remarks());
            ps.executeUpdate();
        }
    }

    public List<ApplicationHistory> findByApplication(Connection c, long applicationId) throws SQLException {
        String sql = "SELECT history_id, application_id, action, old_status, new_status, performed_by, performed_at, remarks "
                + "FROM application_history WHERE application_id = ? ORDER BY history_id";
        List<ApplicationHistory> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String old = rs.getString("old_status");
                    out.add(new ApplicationHistory(rs.getLong("history_id"), rs.getLong("application_id"), rs.getString("action"),
                            old == null ? null : ApplicationStatus.valueOf(old), ApplicationStatus.valueOf(rs.getString("new_status")),
                            rs.getString("performed_by"), Jdbc.dt(rs, "performed_at"), rs.getString("remarks")));
                }
            }
        }
        return out;
    }

    public int countByApplication(Connection c, long applicationId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM application_history WHERE application_id = ?")) {
            ps.setLong(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }
}
