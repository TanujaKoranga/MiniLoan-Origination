-- =====================================================================
-- reports.sql  -  6 parameterized reports (JDBC PreparedStatement '?' placeholders)
-- Format read by ReportDao:
--   -- @REPORT <id>.<part> | <title>
--   -- @PARAMS <name>,<name>,...      (one name per '?', in order; blank line/omitted = no params)
--   <SQL ending with ;>
-- Optional filters: pass NULL (user pressed Enter) and the "? IS NULL OR ..." clause is skipped.
-- =====================================================================

-- @REPORT 1.1 | Application Register
-- @PARAMS product,product,status,status
SELECT a.application_number, c.name AS customer_name, a.product, a.requested_amount,
       a.proposed_emi AS emi, a.status, DATE(a.submitted_at) AS submission_date
FROM loan_application a
JOIN customer c ON c.customer_id = a.customer_id
WHERE (? IS NULL OR a.product = ?)
  AND (? IS NULL OR a.status  = ?)
ORDER BY a.application_id;

-- @REPORT 2.1 | Product Pipeline (by product, status)
-- @PARAMS
SELECT a.product, a.status, COUNT(*) AS application_count, SUM(a.requested_amount) AS total_requested
FROM loan_application a
GROUP BY a.product, a.status
ORDER BY a.product, a.status;

-- @REPORT 2.2 | Product Pipeline (by branch, product)
-- @PARAMS
SELECT a.branch_code, a.branch_name, a.product, COUNT(*) AS application_count,
       SUM(a.requested_amount) AS total_requested
FROM loan_application a
GROUP BY a.branch_code, a.branch_name, a.product
ORDER BY a.branch_code, a.product;

-- @REPORT 3.1 | Disbursement summary (granularity: DAILY / MONTHLY / QUARTERLY)
-- @PARAMS granularity,from_date,to_date
SELECT CASE ? WHEN 'DAILY'   THEN DATE_FORMAT(d.disbursed_at, '%Y-%m-%d')
              WHEN 'MONTHLY' THEN DATE_FORMAT(d.disbursed_at, '%Y-%m')
              ELSE CONCAT(YEAR(d.disbursed_at), '-Q', QUARTER(d.disbursed_at)) END AS period_label,
       COUNT(*) AS disbursement_count, SUM(d.amount) AS total_disbursed
FROM disbursement d
WHERE DATE(d.disbursed_at) BETWEEN ? AND ?
GROUP BY period_label
ORDER BY period_label;

-- @REPORT 3.2 | Disbursement branch-wise breakdown
-- @PARAMS from_date,to_date
SELECT a.branch_code, a.branch_name, COUNT(*) AS disbursement_count, SUM(d.amount) AS total_disbursed
FROM disbursement d
JOIN loan_application a ON a.application_id = d.application_id
WHERE DATE(d.disbursed_at) BETWEEN ? AND ?
GROUP BY a.branch_code, a.branch_name
ORDER BY a.branch_code;

-- @REPORT 4.1 | Overdue Reviews (UNDER_REVIEW older than 2 days as of business date)
-- @PARAMS as_of_date,as_of_date
SELECT a.application_number, c.name AS customer_name, a.requested_amount,
       DATEDIFF(?, DATE(a.submitted_at)) AS days_waiting
FROM loan_application a
JOIN customer c ON c.customer_id = a.customer_id
WHERE a.status = 'UNDER_REVIEW'
  AND DATEDIFF(?, DATE(a.submitted_at)) > 2
ORDER BY days_waiting DESC, a.application_id;

-- @REPORT 5.1 | Customers Without Disbursement
-- @PARAMS
SELECT c.customer_id, c.name, c.mobile, COUNT(a.application_id) AS application_count
FROM customer c
JOIN loan_application a ON a.customer_id = c.customer_id
LEFT JOIN disbursement d ON d.application_id = a.application_id
GROUP BY c.customer_id, c.name, c.mobile
HAVING COUNT(d.disbursement_id) = 0
ORDER BY c.customer_id;

-- @REPORT 6.1 | Repeat Applicants
-- @PARAMS
SELECT c.customer_id, c.name, c.mobile, COUNT(a.application_id) AS application_count,
       SUM(a.requested_amount) AS total_requested
FROM customer c
JOIN loan_application a ON a.customer_id = c.customer_id
GROUP BY c.customer_id, c.name, c.mobile
HAVING COUNT(a.application_id) > 1
ORDER BY application_count DESC, c.customer_id;
