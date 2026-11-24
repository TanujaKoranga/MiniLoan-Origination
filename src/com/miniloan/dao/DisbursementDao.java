package com.miniloan.dao;

import com.miniloan.model.BusinessException;
import com.miniloan.model.Disbursement;

import java.sql.*;
import java.util.Optional;

public class DisbursementDao {

    /** Unique(application_id) and unique(reference) violations are turned into clear BusinessExceptions. */
    public Disbursement insert(Connection c, Disbursement d) throws SQLException {
        String sql = "INSERT INTO disbursement (application_id, reference, amount, disbursed_at) VALUES (?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, d.applicationId());
            ps.setString(2, d.reference());
            ps.setBigDecimal(3, d.amount());
            Jdbc.setDt(ps, 4, d.disbursedAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Disbursement(keys.getLong(1), d.applicationId(), d.reference(), d.amount(), d.disbursedAt());
            }
        } catch (SQLException e) {
            if (Jdbc.isDuplicate(e)) {
                if (Jdbc.messageHas(e, "uq_disb_reference"))
                    throw new BusinessException("Disbursement reference '" + d.reference() + "' is already used.");
                throw new BusinessException("This application has already been disbursed (max one disbursement per application).");
            }
            throw e;
        }
    }

    public Optional<Disbursement> findByApplicationId(Connection c, long applicationId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT disbursement_id, application_id, reference, amount, disbursed_at FROM disbursement WHERE application_id = ?")) {
            ps.setLong(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Disbursement(rs.getLong(1), rs.getLong(2), rs.getString(3),
                        rs.getBigDecimal(4), Jdbc.dt(rs, "disbursed_at")));
            }
        }
    }

    public boolean referenceExists(Connection c, String reference) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM disbursement WHERE reference = ?")) {
            ps.setString(1, reference);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public int countAll(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM disbursement"); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
