package com.miniloan.dao;

import com.miniloan.model.Branch;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BranchDao {

    public List<Branch> findAll(Connection c) throws SQLException {
        List<Branch> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT branch_code, branch_name FROM branch ORDER BY branch_code");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(new Branch(rs.getString(1), rs.getString(2)));
        }
        return out;
    }

    public Optional<Branch> findByCode(Connection c, String code) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT branch_code, branch_name FROM branch WHERE branch_code = ?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Branch(rs.getString(1), rs.getString(2))) : Optional.empty();
            }
        }
    }
}
