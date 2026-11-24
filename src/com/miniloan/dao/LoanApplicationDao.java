package com.miniloan.dao;

import com.miniloan.model.*;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * All status-changing updates are guarded with "AND status = ?" (optimistic check) and return
 * true only when exactly one row changed. active_key is DB-generated and never written here.
 */
public class LoanApplicationDao {
    private static final String COLS = "application_id, application_number, customer_id, product, branch_code, "
            + "branch_name, requested_amount, tenure_months, monthly_income, existing_monthly_obligations, "
            + "annual_interest_rate, proposed_emi, foir_percentage, eligible, eligibility_reasons, status, "
            + "created_at, submitted_at, decided_at, reviewer_code, decision_reason, co_borrower_name, application_form_path";

    // ------------------------------------------------------------------ writes
    public LoanApplication insert(Connection c, LoanApplication a, String numberPrefix) throws SQLException {
        String sql = "INSERT INTO loan_application (application_number, customer_id, product, branch_code, branch_name, "
                + "requested_amount, tenure_months, monthly_income, existing_monthly_obligations, annual_interest_rate, "
                + "proposed_emi, foir_percentage, eligible, eligibility_reasons, status, created_at, submitted_at, "
                + "decided_at, reviewer_code, decision_reason, co_borrower_name, application_form_path) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        long id;
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, "TMP" + System.nanoTime());          // replaced below once the id is known
            ps.setLong(2, a.getCustomerId());
            ps.setString(3, a.getProduct().name());
            ps.setString(4, a.getBranchCode());
            ps.setString(5, a.getBranchName());
            ps.setBigDecimal(6, a.getRequestedAmount());
            ps.setInt(7, a.getTenureMonths());
            ps.setBigDecimal(8, a.getMonthlyIncome());
            ps.setBigDecimal(9, a.getExistingMonthlyObligations());
            ps.setBigDecimal(10, a.getAnnualInterestRate());
            Jdbc.setDec(ps, 11, a.getProposedEmi());
            Jdbc.setDec(ps, 12, a.getFoirPercentage());
            Jdbc.setBool(ps, 13, a.getEligible());
            Jdbc.setStr(ps, 14, a.getEligibilityReasons());
            ps.setString(15, a.getStatus().name());
            Jdbc.setDt(ps, 16, a.getCreatedAt());
            Jdbc.setDt(ps, 17, a.getSubmittedAt());
            Jdbc.setDt(ps, 18, a.getDecidedAt());
            Jdbc.setStr(ps, 19, a.getReviewerCode());
            Jdbc.setStr(ps, 20, a.getDecisionReason());
            Jdbc.setStr(ps, 21, a.getCoBorrowerName());
            Jdbc.setStr(ps, 22, a.getApplicationFormPath());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { keys.next(); id = keys.getLong(1); }
        } catch (SQLException e) {
            if (Jdbc.isDuplicate(e) && Jdbc.messageHas(e, "uq_one_active"))
                throw new BusinessException("Customer already has an active " + a.getProduct()
                        + " application (DRAFT / UNDER_REVIEW / APPROVED). Only one is allowed per product.");
            throw e;
        }
        String number = numberPrefix + String.format("%08d", id);
        try (PreparedStatement ps = c.prepareStatement("UPDATE loan_application SET application_number = ? WHERE application_id = ?")) {
            ps.setString(1, number);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
        a.setApplicationId(id);
        a.setApplicationNumber(number);
        return a;
    }

    /** Edit financial details. Only succeeds while status = DRAFT. Product is immutable. */
    public boolean updateDraftDetails(Connection c, LoanApplication a) throws SQLException {
        String sql = "UPDATE loan_application SET branch_code=?, branch_name=?, requested_amount=?, tenure_months=?, "
                + "monthly_income=?, existing_monthly_obligations=?, annual_interest_rate=?, co_borrower_name=? "
                + "WHERE application_id=? AND status='DRAFT'";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, a.getBranchCode());
            ps.setString(2, a.getBranchName());
            ps.setBigDecimal(3, a.getRequestedAmount());
            ps.setInt(4, a.getTenureMonths());
            ps.setBigDecimal(5, a.getMonthlyIncome());
            ps.setBigDecimal(6, a.getExistingMonthlyObligations());
            ps.setBigDecimal(7, a.getAnnualInterestRate());
            Jdbc.setStr(ps, 8, a.getCoBorrowerName());
            ps.setLong(9, a.getApplicationId());
            return ps.executeUpdate() == 1;
        }
    }

    /** Persist EMI/FOIR/eligibility + new status on submission (DRAFT -> UNDER_REVIEW or REJECTED). */
    public boolean updateAfterSubmission(Connection c, LoanApplication a, ApplicationStatus expectedOld) throws SQLException {
        String sql = "UPDATE loan_application SET status=?, proposed_emi=?, foir_percentage=?, eligible=?, "
                + "eligibility_reasons=?, annual_interest_rate=?, submitted_at=?, decided_at=?, decision_reason=? "
                + "WHERE application_id=? AND status=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, a.getStatus().name());
            Jdbc.setDec(ps, 2, a.getProposedEmi());
            Jdbc.setDec(ps, 3, a.getFoirPercentage());
            Jdbc.setBool(ps, 4, a.getEligible());
            Jdbc.setStr(ps, 5, a.getEligibilityReasons());
            ps.setBigDecimal(6, a.getAnnualInterestRate());
            Jdbc.setDt(ps, 7, a.getSubmittedAt());
            Jdbc.setDt(ps, 8, a.getDecidedAt());
            Jdbc.setStr(ps, 9, a.getDecisionReason());
            ps.setLong(10, a.getApplicationId());
            ps.setString(11, expectedOld.name());
            return ps.executeUpdate() == 1;
        }
    }

    /** Reviewer approve / reject. */
    public boolean updateDecision(Connection c, long id, ApplicationStatus oldS, ApplicationStatus newS,
                                  java.time.LocalDateTime decidedAt, String reviewerCode, String reason) throws SQLException {
        String sql = "UPDATE loan_application SET status=?, decided_at=?, reviewer_code=?, decision_reason=? "
                + "WHERE application_id=? AND status=?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newS.name());
            Jdbc.setDt(ps, 2, decidedAt);
            Jdbc.setStr(ps, 3, reviewerCode);
            Jdbc.setStr(ps, 4, reason);
            ps.setLong(5, id);
            ps.setString(6, oldS.name());
            return ps.executeUpdate() == 1;
        }
    }

    /** Plain status change (APPROVED->DISBURSED, DISBURSED->CORE_BANKING_PUSH->EXIT). */
    public boolean updateStatus(Connection c, long id, ApplicationStatus oldS, ApplicationStatus newS) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE loan_application SET status=? WHERE application_id=? AND status=?")) {
            ps.setString(1, newS.name());
            ps.setLong(2, id);
            ps.setString(3, oldS.name());
            return ps.executeUpdate() == 1;
        }
    }

    public boolean updateFormPath(Connection c, long id, String path) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE loan_application SET application_form_path=? WHERE application_id=?")) {
            ps.setString(1, path);
            ps.setLong(2, id);
            return ps.executeUpdate() == 1;
        }
    }

    // ------------------------------------------------------------------ reads
    public Optional<LoanApplication> findById(Connection c, long id) throws SQLException {
        return findOne(c, "SELECT " + COLS + " FROM loan_application WHERE application_id = ?", id);
    }

    /** Row-locks the application for the rest of the transaction. */
    public Optional<LoanApplication> findByIdForUpdate(Connection c, long id) throws SQLException {
        return findOne(c, "SELECT " + COLS + " FROM loan_application WHERE application_id = ? FOR UPDATE", id);
    }

    public Optional<LoanApplication> findByNumber(Connection c, String number) throws SQLException {
        return findOne(c, "SELECT " + COLS + " FROM loan_application WHERE application_number = ?", number);
    }

    public List<LoanApplication> findByCustomer(Connection c, long customerId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT " + COLS + " FROM loan_application WHERE customer_id = ? ORDER BY application_id")) {
            ps.setLong(1, customerId);
            return list(ps);
        }
    }

    public List<LoanApplication> findAll(Connection c, Product product, ApplicationStatus status) throws SQLException {
        String sql = "SELECT " + COLS + " FROM loan_application WHERE (? IS NULL OR product = ?) AND (? IS NULL OR status = ?) ORDER BY application_id";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            String p = product == null ? null : product.name();
            String s = status == null ? null : status.name();
            Jdbc.setStr(ps, 1, p); Jdbc.setStr(ps, 2, p);
            Jdbc.setStr(ps, 3, s); Jdbc.setStr(ps, 4, s);
            return list(ps);
        }
    }

    /** Active = DRAFT / UNDER_REVIEW / APPROVED. excludeId = 0 to exclude nothing. */
    public int countActive(Connection c, long customerId, Product product, long excludeId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM loan_application WHERE customer_id=? AND product=? "
                + "AND status IN ('DRAFT','UNDER_REVIEW','APPROVED') AND application_id <> ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, customerId);
            ps.setString(2, product.name());
            ps.setLong(3, excludeId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    /** All UNDER_REVIEW applications with days waiting = DATEDIFF(asOf, submission date). */
    public List<PendingReviewRow> findPendingReviews(Connection c, LocalDate asOf) throws SQLException {
        String sql = "SELECT a.application_id, a.application_number, cu.name, a.product, a.requested_amount, a.submitted_at, "
                + "DATEDIFF(?, DATE(a.submitted_at)) AS days_waiting "
                + "FROM loan_application a JOIN customer cu ON cu.customer_id = a.customer_id "
                + "WHERE a.status = 'UNDER_REVIEW' ORDER BY a.submitted_at, a.application_id";
        List<PendingReviewRow> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, asOf);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new PendingReviewRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                            Product.valueOf(rs.getString(4)), rs.getBigDecimal(5), Jdbc.dt(rs, "submitted_at"), rs.getInt("days_waiting")));
                }
            }
        }
        return out;
    }

    /** DISBURSED applications that have no ng_core_banking row yet. */
    public List<Long> findDisbursedNotPushedIds(Connection c) throws SQLException {
        String sql = "SELECT a.application_id FROM loan_application a WHERE a.status = 'DISBURSED' "
                + "AND NOT EXISTS (SELECT 1 FROM ng_core_banking n WHERE n.application_id = a.application_id) "
                + "ORDER BY a.application_id";
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) ids.add(rs.getLong(1));
        }
        return ids;
    }

    // ------------------------------------------------------------------ mapping
    private Optional<LoanApplication> findOne(Connection c, String sql, Object param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, param);
            List<LoanApplication> l = list(ps);
            return l.isEmpty() ? Optional.empty() : Optional.of(l.get(0));
        }
    }

    private List<LoanApplication> list(PreparedStatement ps) throws SQLException {
        List<LoanApplication> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(map(rs)); }
        return out;
    }

    static LoanApplication map(ResultSet rs) throws SQLException {
        LoanApplication a = new LoanApplication();
        a.setApplicationId(rs.getLong("application_id"));
        a.setApplicationNumber(rs.getString("application_number"));
        a.setCustomerId(rs.getLong("customer_id"));
        a.setProduct(Product.valueOf(rs.getString("product")));
        a.setBranchCode(rs.getString("branch_code"));
        a.setBranchName(rs.getString("branch_name"));
        a.setRequestedAmount(rs.getBigDecimal("requested_amount"));
        a.setTenureMonths(rs.getInt("tenure_months"));
        a.setMonthlyIncome(rs.getBigDecimal("monthly_income"));
        a.setExistingMonthlyObligations(rs.getBigDecimal("existing_monthly_obligations"));
        a.setAnnualInterestRate(rs.getBigDecimal("annual_interest_rate"));
        a.setProposedEmi(rs.getBigDecimal("proposed_emi"));
        a.setFoirPercentage(rs.getBigDecimal("foir_percentage"));
        a.setEligible(Jdbc.bool(rs, "eligible"));
        a.setEligibilityReasons(rs.getString("eligibility_reasons"));
        a.setStatus(ApplicationStatus.valueOf(rs.getString("status")));
        a.setCreatedAt(Jdbc.dt(rs, "created_at"));
        a.setSubmittedAt(Jdbc.dt(rs, "submitted_at"));
        a.setDecidedAt(Jdbc.dt(rs, "decided_at"));
        a.setReviewerCode(rs.getString("reviewer_code"));
        a.setDecisionReason(rs.getString("decision_reason"));
        a.setCoBorrowerName(rs.getString("co_borrower_name"));
        a.setApplicationFormPath(rs.getString("application_form_path"));
        return a;
    }
}
