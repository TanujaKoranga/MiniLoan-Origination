-- =====================================================================
-- Mini Loan Origination - schema.sql  (MySQL 8.0.16+ required: CHECK is enforced)
-- Safe to re-run: uses IF NOT EXISTS so data persists across restarts.
-- To start from scratch, uncomment the DROP block below.
-- =====================================================================
-- SET FOREIGN_KEY_CHECKS = 0;
-- DROP TABLE IF EXISTS ng_core_banking, utility_run, system_alerts, application_history,
--                      disbursement, loan_application, customer, branch;
-- SET FOREIGN_KEY_CHECKS = 1;

CREATE DATABASE IF NOT EXISTS miniloan CHARACTER SET utf8mb4;
USE miniloan;

-- Reference table (supporting, not one of the 7 core tables): branch master
CREATE TABLE IF NOT EXISTS branch (
    branch_code VARCHAR(10)  NOT NULL PRIMARY KEY,
    branch_name VARCHAR(100) NOT NULL
) ENGINE=InnoDB;

-- 1. customer ----------------------------------------------------------
CREATE TABLE IF NOT EXISTS customer (
    customer_id   BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    date_of_birth DATE         NOT NULL,
    mobile        VARCHAR(10)  NOT NULL,
    address       VARCHAR(255) NOT NULL DEFAULT 'ADDRESS NOT PROVIDED',
    kyc_details   VARCHAR(255) NOT NULL DEFAULT 'KYC PENDING',
    created_at    DATETIME     NOT NULL,
    CONSTRAINT uq_customer_mobile UNIQUE (mobile),
    CONSTRAINT ck_customer_mobile CHECK (mobile REGEXP '^[0-9]{10}$'),
    INDEX idx_customer_name (name)
) ENGINE=InnoDB;

-- 2. loan_application ----------------------------------------------------
CREATE TABLE IF NOT EXISTS loan_application (
    application_id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    application_number           VARCHAR(30)   NOT NULL,
    customer_id                  BIGINT        NOT NULL,
    product                      VARCHAR(20)   NOT NULL,
    branch_code                  VARCHAR(10)   NOT NULL,
    branch_name                  VARCHAR(100)  NOT NULL,
    requested_amount             DECIMAL(15,2) NOT NULL,
    tenure_months                INT           NOT NULL,
    monthly_income               DECIMAL(15,2) NOT NULL,
    existing_monthly_obligations DECIMAL(15,2) NOT NULL DEFAULT 0,
    annual_interest_rate         DECIMAL(5,2)  NOT NULL,
    proposed_emi                 DECIMAL(15,2) NULL,
    foir_percentage              DECIMAL(7,2)  NULL,
    eligible                     BOOLEAN       NULL,
    eligibility_reasons          TEXT          NULL,
    status                       VARCHAR(20)   NOT NULL,
    created_at                   DATETIME      NOT NULL,
    submitted_at                 DATETIME      NULL,
    decided_at                   DATETIME      NULL,
    reviewer_code                VARCHAR(20)   NULL,
    decision_reason              VARCHAR(500)  NULL,
    co_borrower_name             VARCHAR(100)  NULL,   -- extra: needed for the EDUCATION "no co-borrower" rule
    application_form_path        VARCHAR(255)  NULL,   -- extra: path of generated Application Form
    -- DB-level guarantee: max ONE active (DRAFT/UNDER_REVIEW/APPROVED) application per customer+product.
    -- MySQL has no partial unique index, so a STORED generated column is used; NULLs never collide.
    -- DAO MUST NOT include active_key in INSERT/UPDATE statements.
    active_key VARCHAR(40) GENERATED ALWAYS AS (
        CASE WHEN status IN ('DRAFT','UNDER_REVIEW','APPROVED')
             THEN CONCAT(customer_id, '-', product) ELSE NULL END) STORED,
    CONSTRAINT uq_app_number         UNIQUE (application_number),
    CONSTRAINT uq_one_active_per_prd UNIQUE (active_key),
    CONSTRAINT fk_app_customer       FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    CONSTRAINT ck_app_product  CHECK (product IN ('PERSONAL','EDUCATION')),
    CONSTRAINT ck_app_status   CHECK (status IN ('DRAFT','UNDER_REVIEW','APPROVED','REJECTED',
                                                 'DISBURSED','CORE_BANKING_PUSH','EXIT')),
    CONSTRAINT ck_app_amount   CHECK (requested_amount > 0),
    CONSTRAINT ck_app_tenure   CHECK (tenure_months > 0),
    CONSTRAINT ck_app_income   CHECK (monthly_income > 0),
    CONSTRAINT ck_app_oblig    CHECK (existing_monthly_obligations >= 0),
    INDEX idx_app_customer_prod (customer_id, product, status),
    INDEX idx_app_status_sub    (status, submitted_at),
    INDEX idx_app_branch        (branch_code, product)
) ENGINE=InnoDB;

-- 3. disbursement ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS disbursement (
    disbursement_id BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    application_id  BIGINT        NOT NULL,
    reference       VARCHAR(40)   NOT NULL,
    amount          DECIMAL(15,2) NOT NULL,
    disbursed_at    DATETIME      NOT NULL,
    CONSTRAINT uq_disb_application UNIQUE (application_id),   -- max 1 disbursement per application
    CONSTRAINT uq_disb_reference   UNIQUE (reference),        -- globally unique reference
    CONSTRAINT fk_disb_app FOREIGN KEY (application_id) REFERENCES loan_application(application_id),
    CONSTRAINT ck_disb_amount CHECK (amount > 0),
    INDEX idx_disb_date (disbursed_at)
) ENGINE=InnoDB;

-- 4. application_history ----------------------------------------------------
CREATE TABLE IF NOT EXISTS application_history (
    history_id     BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT       NOT NULL,
    action         VARCHAR(40)  NOT NULL,
    old_status     VARCHAR(20)  NULL,
    new_status     VARCHAR(20)  NOT NULL,
    performed_by   VARCHAR(50)  NOT NULL,
    performed_at   DATETIME     NOT NULL,
    remarks        VARCHAR(1000) NULL,
    CONSTRAINT fk_hist_app FOREIGN KEY (application_id) REFERENCES loan_application(application_id),
    INDEX idx_hist_app (application_id, performed_at)
) ENGINE=InnoDB;

-- 5. system_alerts ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS system_alerts (
    alert_id       BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT      NOT NULL,
    alert_type     VARCHAR(40) NOT NULL,
    created_at     DATETIME    NOT NULL,
    CONSTRAINT uq_alert_once UNIQUE (application_id, alert_type),   -- idempotent alerting
    CONSTRAINT fk_alert_app FOREIGN KEY (application_id) REFERENCES loan_application(application_id)
) ENGINE=InnoDB;

-- 6. utility_run ---------------------------------------------------------------
CREATE TABLE IF NOT EXISTS utility_run (
    run_id                 BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    utility_name           VARCHAR(50) NOT NULL,
    started_at             DATETIME    NOT NULL,
    completed_at           DATETIME    NULL,
    result                 VARCHAR(20) NOT NULL,      -- SUCCESS | FAILED | PARTIAL
    applications_processed INT         NOT NULL DEFAULT 0,
    alerts_created         INT         NOT NULL DEFAULT 0,
    error_message          TEXT        NULL,
    INDEX idx_run_name (utility_name, started_at)
) ENGINE=InnoDB;

-- 7. ng_core_banking --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ng_core_banking (
    core_id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    customer_id      BIGINT        NOT NULL,
    application_id   BIGINT        NOT NULL,
    customer_name    VARCHAR(100)  NOT NULL,
    address          VARCHAR(255)  NOT NULL,
    dob              DATE          NOT NULL,
    kyc_details      VARCHAR(255)  NOT NULL,
    approved_amount  DECIMAL(15,2) NOT NULL,
    disbursed_amount DECIMAL(15,2) NOT NULL,
    disbursed_date   DATE          NOT NULL,
    CONSTRAINT uq_core_application UNIQUE (application_id),   -- no duplicate pushes
    CONSTRAINT fk_core_customer FOREIGN KEY (customer_id)    REFERENCES customer(customer_id),
    CONSTRAINT fk_core_app      FOREIGN KEY (application_id) REFERENCES loan_application(application_id)
) ENGINE=InnoDB;

-- ----------------------------- Seed / reference data -----------------------------
INSERT IGNORE INTO branch (branch_code, branch_name) VALUES
 ('BR001','Mumbai Main'), ('BR002','Delhi Connaught Place'),
 ('BR003','Bengaluru MG Road'), ('BR004','Bareilly Civil Lines');

INSERT IGNORE INTO customer (name, date_of_birth, mobile, address, kyc_details, created_at) VALUES
 ('Aarav Sharma','1990-05-15','9876543210','12 MG Road, Mumbai','PAN:ABCDE1234F|AADHAAR:XXXX-1234',NOW()),
 ('Priya Verma', '1985-11-02','9876501234','45 Civil Lines, Bareilly','PAN:PQRSX5678L|AADHAAR:XXXX-5678',NOW()),
 ('Rohan Gupta', '1995-01-20','9123456780','7 Park Street, Delhi','PAN:LMNOP4321Z|AADHAAR:XXXX-4321',NOW());
