package com.miniloan.service;

import com.miniloan.dao.*;
import com.miniloan.model.*;
import com.miniloan.utility.BusinessDateProvider;
import com.miniloan.utility.ConfigLoader;
import com.miniloan.utility.IndianCurrencyFormatter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * Application workflow. EVERY state change runs in one ACID transaction together with its
 * application_history row (and disbursement / form path where relevant).
 */
public class ApplicationService {
    public static final String OPERATOR = "OPERATOR";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ConfigLoader config;
    private final BusinessDateProvider clock;
    private final TransactionManager txm;
    private final LoanApplicationDao appDao;
    private final CustomerDao customerDao;
    private final BranchDao branchDao;
    private final DisbursementDao disbursementDao;
    private final ApplicationHistoryDao historyDao;
    private final ValidationService validation;
    private final EmiFoirCalculator calculator;
    private final ApplicationStateMachine stateMachine;
    private final ApplicationFormGenerator formGenerator;

    public ApplicationService(ConfigLoader config, BusinessDateProvider clock, TransactionManager txm,
                              LoanApplicationDao appDao, CustomerDao customerDao, BranchDao branchDao,
                              DisbursementDao disbursementDao, ApplicationHistoryDao historyDao,
                              ValidationService validation, EmiFoirCalculator calculator,
                              ApplicationStateMachine stateMachine, ApplicationFormGenerator formGenerator) {
        this.config = config;
        this.clock = clock;
        this.txm = txm;
        this.appDao = appDao;
        this.customerDao = customerDao;
        this.branchDao = branchDao;
        this.disbursementDao = disbursementDao;
        this.historyDao = historyDao;
        this.validation = validation;
        this.calculator = calculator;
        this.stateMachine = stateMachine;
        this.formGenerator = formGenerator;
    }

    // ================================================================== capture
    public List<Branch> listBranches() { return txm.read(branchDao::findAll); }

    public LoanApplication createDraft(long customerId, Product product, String branchCode, BigDecimal amount,
                                       int tenure, BigDecimal income, BigDecimal obligations, String coBorrower) {
        validation.validateDraftInputs(amount, tenure, income, obligations);
        return txm.inTransaction(conn -> {
            Customer cust = customerDao.findById(conn, customerId)
                    .orElseThrow(() -> new BusinessException("Customer " + customerId + " not found."));
            Branch br = branchDao.findByCode(conn, branchCode == null ? "" : branchCode.trim().toUpperCase())
                    .orElseThrow(() -> new BusinessException("Unknown branch code '" + branchCode + "'."));
            // friendly pre-check; the DB unique key uq_one_active_per_prd is the hard guarantee
            for (LoanApplication ex : appDao.findByCustomer(conn, customerId)) {
                if (ex.getProduct() == product && ex.getStatus().isActive())
                    throw new BusinessException("Customer #" + customerId + " (" + cust.name() + ") already has an active "
                            + product + " application " + ex.getApplicationNumber() + " in status " + ex.getStatus()
                            + ". A new one is allowed only after it is REJECTED or DISBURSED.");
            }
            ProductLimits lim = config.productLimits(product);
            LoanApplication a = new LoanApplication();
            a.setCustomerId(customerId);
            a.setProduct(product);
            a.setBranchCode(br.branchCode());
            a.setBranchName(br.branchName());
            a.setRequestedAmount(amount.setScale(2, RoundingMode.HALF_UP));
            a.setTenureMonths(tenure);
            a.setMonthlyIncome(income.setScale(2, RoundingMode.HALF_UP));
            a.setExistingMonthlyObligations(obligations.setScale(2, RoundingMode.HALF_UP));
            a.setAnnualInterestRate(lim.annualInterestRate().setScale(2, RoundingMode.HALF_UP));
            a.setCoBorrowerName(blankToNull(coBorrower));
            a.setStatus(ApplicationStatus.DRAFT);
            a.setCreatedAt(clock.now());
            appDao.insert(conn, a, config.applicationNumberPrefix());
            stateMachine.log(conn, a.getApplicationId(), "CREATED", null, ApplicationStatus.DRAFT, OPERATOR, "Draft application created");
            return a;
        });
    }

    /** Edit financial details - allowed ONLY while DRAFT. */
    public LoanApplication updateDraft(long applicationId, String branchCode, BigDecimal amount, int tenure,
                                       BigDecimal income, BigDecimal obligations, String coBorrower) {
        validation.validateDraftInputs(amount, tenure, income, obligations);
        return txm.inTransaction(conn -> {
            LoanApplication a = lock(conn, applicationId);
            if (!a.getStatus().isEditable())
                throw new BusinessException("Application " + a.getApplicationNumber() + " is " + a.getStatus()
                        + ". Financial details can be edited only while the application is DRAFT.");
            Branch br = branchDao.findByCode(conn, branchCode.trim().toUpperCase())
                    .orElseThrow(() -> new BusinessException("Unknown branch code '" + branchCode + "'."));
            a.setBranchCode(br.branchCode());
            a.setBranchName(br.branchName());
            a.setRequestedAmount(amount.setScale(2, RoundingMode.HALF_UP));
            a.setTenureMonths(tenure);
            a.setMonthlyIncome(income.setScale(2, RoundingMode.HALF_UP));
            a.setExistingMonthlyObligations(obligations.setScale(2, RoundingMode.HALF_UP));
            a.setAnnualInterestRate(config.productLimits(a.getProduct()).annualInterestRate().setScale(2, RoundingMode.HALF_UP));
            a.setCoBorrowerName(blankToNull(coBorrower));
            if (!appDao.updateDraftDetails(conn, a)) throw concurrent(a);
            stateMachine.log(conn, applicationId, "DRAFT_EDITED", ApplicationStatus.DRAFT, ApplicationStatus.DRAFT, OPERATOR,
                    "Amount " + a.getRequestedAmount() + ", tenure " + tenure + ", income " + a.getMonthlyIncome()
                            + ", obligations " + a.getExistingMonthlyObligations());
            return a;
        });
    }

    // ================================================================== submission
    /** DRAFT -> UNDER_REVIEW if every rule passes, else DRAFT -> REJECTED with ALL failed reasons stored. */
    public LoanApplication submit(long applicationId) {
        return txm.inTransaction(conn -> {
            LoanApplication a = lock(conn, applicationId);
            ApplicationStatus old = a.getStatus();
            stateMachine.assertTransition(old, ApplicationStatus.UNDER_REVIEW);     // only DRAFT passes
            Customer cust = customerDao.findById(conn, a.getCustomerId())
                    .orElseThrow(() -> new BusinessException("Customer not found for application."));
            LocalDateTime now = clock.now();
            LocalDate businessDate = now.toLocalDate();

            ProductLimits lim = config.productLimits(a.getProduct());
            EmiFoirResult ef = calculator.calculate(a.getRequestedAmount(), lim.annualInterestRate(), a.getTenureMonths(),
                    a.getExistingMonthlyObligations(), a.getMonthlyIncome());
            ValidationResult vr = validation.evaluateSubmission(a, cust, businessDate, lim, ef);

            a.setAnnualInterestRate(lim.annualInterestRate().setScale(2, RoundingMode.HALF_UP));
            a.setProposedEmi(ef.emi());
            a.setFoirPercentage(ef.foirPercentage());
            a.setSubmittedAt(now);
            ApplicationStatus next;
            String action, remarks;
            if (vr.isValid()) {
                next = ApplicationStatus.UNDER_REVIEW;
                a.setEligible(true);
                a.setEligibilityReasons("All eligibility checks passed.");
                action = "SUBMITTED";
                remarks = "Eligible. EMI " + ef.emi() + ", FOIR " + ef.foirPercentage() + "%";
            } else {
                next = ApplicationStatus.REJECTED;
                a.setEligible(false);
                a.setEligibilityReasons(vr.joined());
                a.setDecidedAt(now);
                a.setDecisionReason("Auto-rejected on submission: " + vr.joined());
                action = "AUTO_REJECTED";
                remarks = vr.joined();
            }
            stateMachine.assertTransition(old, next);
            a.setStatus(next);
            if (!appDao.updateAfterSubmission(conn, a, old)) throw concurrent(a);
            stateMachine.log(conn, applicationId, action, old, next, OPERATOR, remarks);
            return a;
        });
    }

    // ================================================================== reviewer decisions
    public LoanApplication approve(long applicationId, String reviewerCode) {
        String reviewer = reviewerOrDefault(reviewerCode);
        return txm.inTransaction(conn -> {
            LoanApplication a = lock(conn, applicationId);
            ApplicationStatus old = a.getStatus();
            stateMachine.assertTransition(old, ApplicationStatus.APPROVED);
            Customer cust = customerDao.findById(conn, a.getCustomerId())
                    .orElseThrow(() -> new BusinessException("Customer not found for application."));
            LocalDateTime now = clock.now();
            String reason = "Approved at requested amount and tenure";
            if (!appDao.updateDecision(conn, applicationId, old, ApplicationStatus.APPROVED, now, reviewer, reason)) throw concurrent(a);
            a.setStatus(ApplicationStatus.APPROVED);
            a.setDecidedAt(now);
            a.setReviewerCode(reviewer);
            a.setDecisionReason(reason);
            stateMachine.log(conn, applicationId, "APPROVED", old, ApplicationStatus.APPROVED, reviewer, reason);
            try {
                Path form = formGenerator.generate(a, cust);
                appDao.updateFormPath(conn, applicationId, form.toString());
                a.setApplicationFormPath(form.toString());
            } catch (IOException e) {
                // the approval is rolled back too: an approval without its form is not allowed
                throw new BusinessException("Could not generate the Application Form, approval rolled back: " + e.getMessage(), e);
            }
            return a;
        });
    }

    public LoanApplication reject(long applicationId, String reviewerCode, String reason) {
        if (reason == null || reason.isBlank()) throw new BusinessException("A rejection reason is mandatory.");
        if (reason.trim().length() > 500) throw new BusinessException("Rejection reason must be at most 500 characters.");
        String reviewer = reviewerOrDefault(reviewerCode);
        return txm.inTransaction(conn -> {
            LoanApplication a = lock(conn, applicationId);
            ApplicationStatus old = a.getStatus();
            stateMachine.assertTransition(old, ApplicationStatus.REJECTED);
            LocalDateTime now = clock.now();
            if (!appDao.updateDecision(conn, applicationId, old, ApplicationStatus.REJECTED, now, reviewer, reason.trim())) throw concurrent(a);
            a.setStatus(ApplicationStatus.REJECTED);
            a.setDecidedAt(now);
            a.setReviewerCode(reviewer);
            a.setDecisionReason(reason.trim());
            stateMachine.log(conn, applicationId, "REJECTED", old, ApplicationStatus.REJECTED, reviewer, reason.trim());
            return a;
        });
    }

    // ================================================================== disbursement
    public DisbursementReceipt disburse(long applicationId, String manualReference) {
        return disburse(applicationId, manualReference, false);
    }

    /**
     * APPROVED -> DISBURSED: disbursement row + status change + history row in ONE transaction.
     * injectFailure = TEST HOOK (controlled rollback test): throws after all three writes, before commit.
     */
    public DisbursementReceipt disburse(long applicationId, String manualReference, boolean injectFailure) {
        String ref = blankToNull(manualReference);
        if (ref != null && (ref.length() > 40 || ref.matches(".*\\s.*")))
            throw new BusinessException("Reference must be at most 40 characters with no spaces.");
        return txm.inTransaction(conn -> {
            LoanApplication a = lock(conn, applicationId);
            Optional<Disbursement> already = disbursementDao.findByApplicationId(conn, applicationId);
            if (already.isPresent())
                throw new BusinessException("Application " + a.getApplicationNumber() + " has already been disbursed (reference "
                        + already.get().reference() + " on " + already.get().disbursedAt().toLocalDate()
                        + "). Duplicate disbursement refused.");
            ApplicationStatus old = a.getStatus();
            stateMachine.assertTransition(old, ApplicationStatus.DISBURSED);         // only APPROVED passes
            Customer cust = customerDao.findById(conn, a.getCustomerId())
                    .orElseThrow(() -> new BusinessException("Customer not found for application."));

            String reference;
            if (ref != null) {
                if (disbursementDao.referenceExists(conn, ref))
                    throw new BusinessException("Disbursement reference '" + ref + "' is already used. References must be globally unique.");
                reference = ref;
            } else {
                reference = generateReference(conn);
            }
            LocalDateTime now = clock.now();
            Disbursement d = disbursementDao.insert(conn,
                    new Disbursement(null, applicationId, reference, a.getRequestedAmount(), now));
            if (!appDao.updateStatus(conn, applicationId, old, ApplicationStatus.DISBURSED)) throw concurrent(a);
            stateMachine.log(conn, applicationId, "DISBURSED", old, ApplicationStatus.DISBURSED, OPERATOR,
                    "Disbursed " + IndianCurrencyFormatter.format(d.amount()) + ", reference " + reference);
            if (injectFailure)
                throw new IllegalStateException("INJECTED FAILURE after disbursement insert, status update and history write");
            return new DisbursementReceipt(a.getApplicationNumber(), cust.name(), d.amount(), reference, now);
        });
    }

    private String generateReference(Connection conn) throws SQLException {
        String day = clock.today().format(DateTimeFormatter.BASIC_ISO_DATE);
        for (int i = 0; i < 10; i++) {
            String ref = "DSB" + day + String.format("%06d", RANDOM.nextInt(1_000_000));
            if (!disbursementDao.referenceExists(conn, ref)) return ref;
        }
        throw new BusinessException("Could not generate a unique disbursement reference. Please retry.");
    }

    // ================================================================== queries
    public LoanApplication resolve(String idOrNumber) {
        if (idOrNumber == null || idOrNumber.isBlank()) throw new BusinessException("Application ID or number is required.");
        String s = idOrNumber.trim();
        return txm.read(c -> {
            Optional<LoanApplication> o = s.matches("\\d+") ? appDao.findById(c, Long.parseLong(s))
                    : appDao.findByNumber(c, s.toUpperCase());
            return o.orElseThrow(() -> new BusinessException("Application not found: " + s));
        });
    }

    public ApplicationDetails getDetails(long applicationId) {
        return txm.read(c -> {
            LoanApplication a = appDao.findById(c, applicationId)
                    .orElseThrow(() -> new BusinessException("Application " + applicationId + " not found."));
            Customer cu = customerDao.findById(c, a.getCustomerId())
                    .orElseThrow(() -> new BusinessException("Customer not found for application."));
            return new ApplicationDetails(a, cu, historyDao.findByApplication(c, applicationId),
                    disbursementDao.findByApplicationId(c, applicationId).orElse(null));
        });
    }

    public List<LoanApplication> list(Product product, ApplicationStatus status) {
        return txm.read(c -> appDao.findAll(c, product, status));
    }

    // ================================================================== helpers
    private LoanApplication lock(Connection conn, long id) throws SQLException {
        return appDao.findByIdForUpdate(conn, id).orElseThrow(() -> new BusinessException("Application " + id + " not found."));
    }

    private BusinessException concurrent(LoanApplication a) {
        return new BusinessException("Application " + a.getApplicationNumber() + " was changed by someone else. Please reload and retry.");
    }

    private String reviewerOrDefault(String code) {
        return (code == null || code.isBlank()) ? config.defaultReviewerCode() : code.trim();
    }

    private static String blankToNull(String s) { return (s == null || s.isBlank()) ? null : s.trim(); }
}
