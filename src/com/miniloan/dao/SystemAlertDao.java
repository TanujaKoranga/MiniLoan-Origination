package com.miniloan.dao;

import com.miniloan.model.SystemAlert;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SystemAlertDao {

    /** Returns true if a NEW alert was inserted, false if (application_id, alert_type) already existed. */
    public boolean insertIfAbsent(Connection c, long applicationId, String alertType, LocalDateTime createdAt) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO system_alerts (application_id, alert_type, created_at) VALUES (?,?,?)")) {
            ps.setLong(1, applicationId);
            ps.setString(2, alertType);
            Jdbc.setDt(ps, 3, createdAt);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (Jdbc.isDuplicate(e)) return false;
            throw e;
        }
    }

    public List<SystemAlert> findAll(Connection c) throws SQLException {
        List<SystemAlert> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT alert_id, application_id, alert_type, created_at FROM system_alerts ORDER BY alert_id");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(new SystemAlert(rs.getLong(1), rs.getLong(2), rs.getString(3), Jdbc.dt(rs, "created_at")));
        }
        return out;
    }

    public int countFor(Connection c, long applicationId, String alertType) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM system_alerts WHERE application_id=? AND alert_type=?")) {
            ps.setLong(1, applicationId);
            ps.setString(2, alertType);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }
}
