package com.miniloan.dao;

import com.miniloan.model.BusinessException;
import com.miniloan.model.Customer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CustomerDao {
    private static final String COLS = "customer_id, name, date_of_birth, mobile, address, kyc_details";

    public Customer insert(Connection c, Customer cust, LocalDateTime createdAt) throws SQLException {
        String sql = "INSERT INTO customer (name, date_of_birth, mobile, address, kyc_details, created_at) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, cust.name());
            ps.setObject(2, cust.dateOfBirth());
            ps.setString(3, cust.mobile());
            ps.setString(4, cust.address());
            ps.setString(5, cust.kycDetails());
            Jdbc.setDt(ps, 6, createdAt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return cust.withId(keys.getLong(1));
            }
        } catch (SQLException e) {
            if (Jdbc.isDuplicate(e))
                throw new BusinessException("Mobile number " + cust.mobile() + " is already registered to another customer.");
            throw e;
        }
    }

    public Optional<Customer> findById(Connection c, long id) throws SQLException {
        return findOne(c, "SELECT " + COLS + " FROM customer WHERE customer_id = ?", id);
    }

    public Optional<Customer> findByMobile(Connection c, String mobile) throws SQLException {
        return findOne(c, "SELECT " + COLS + " FROM customer WHERE mobile = ?", mobile);
    }

    /** Search by exact customer id, exact mobile, or partial name. */
    public List<Customer> search(Connection c, String term) throws SQLException {
        long id = -1;
        try { id = Long.parseLong(term.trim()); } catch (NumberFormatException ignored) { }
        String sql = "SELECT " + COLS + " FROM customer WHERE customer_id = ? OR mobile = ? OR name LIKE ? ORDER BY customer_id LIMIT 50";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setString(2, term.trim());
            ps.setString(3, "%" + term.trim() + "%");
            return list(ps);
        }
    }

    public List<Customer> findAll(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLS + " FROM customer ORDER BY customer_id")) {
            return list(ps);
        }
    }

    private Optional<Customer> findOne(Connection c, String sql, Object param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, param);
            List<Customer> l = list(ps);
            return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
        }
    }

    private List<Customer> list(PreparedStatement ps) throws SQLException {
        List<Customer> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new Customer(rs.getLong("customer_id"), rs.getString("name"), Jdbc.d(rs, "date_of_birth"),
                        rs.getString("mobile"), rs.getString("address"), rs.getString("kyc_details")));
            }
        }
        return out;
    }
}
