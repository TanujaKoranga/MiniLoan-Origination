package com.miniloan.dao;

import com.miniloan.model.CoreBankingRecord;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CoreBankingDao {

    /** true = inserted; false = a row for this application already existed (unique application_id). */
    public boolean insertIfAbsent(Connection c, CoreBankingRecord r) throws SQLException {
        String sql = "INSERT INTO ng_core_banking (customer_id, application_id, customer_name, address, dob, kyc_details, "
                + "approved_amount, disbursed_amount, disbursed_date) VALUES (?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, r.customerId());
            ps.setLong(2, r.applicationId());
            ps.setString(3, r.customerName());
            ps.setString(4, r.address());
            ps.setObject(5, r.dob());
            ps.setString(6, r.kycDetails());
            ps.setBigDecimal(7, r.approvedAmount());
            ps.setBigDecimal(8, r.disbursedAmount());
            ps.setObject(9, r.disbursedDate());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (Jdbc.isDuplicate(e)) return false;
            throw e;
        }
    }

    public boolean existsForApplication(Connection c, long applicationId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM ng_core_banking WHERE application_id = ?")) {
            ps.setLong(1, applicationId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public int countAll(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM ng_core_banking"); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public List<CoreBankingRecord> findAll(Connection c) throws SQLException {
        String sql = "SELECT core_id, customer_id, application_id, customer_name, address, dob, kyc_details, approved_amount, "
                + "disbursed_amount, disbursed_date FROM ng_core_banking ORDER BY core_id";
        List<CoreBankingRecord> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new CoreBankingRecord(rs.getLong("core_id"), rs.getLong("customer_id"), rs.getLong("application_id"),
                        rs.getString("customer_name"), rs.getString("address"), Jdbc.d(rs, "dob"), rs.getString("kyc_details"),
                        rs.getBigDecimal("approved_amount"), rs.getBigDecimal("disbursed_amount"), Jdbc.d(rs, "disbursed_date")));
            }
        }
        return out;
    }
}
