# Mini Loan Origination - Test & Verification Walkthrough

Covers all 12 required scenarios. Every expected number below was produced by running the real
`EmiFoirCalculator` / `ValidationService` code (EMI, FOIR, age and range rules); the menu steps
exercise the same code against MySQL.

---------------------------------------------------------------------------------------------------
## 0. Build, run, conventions

```powershell
cd C:\Users\HP\Downloads\files
mysql -u root -p -e "source sql/schema.sql"          # once (safe to repeat, data is kept)
# edit config\config.properties -> db.password
# put mysql-connector-j-8.x.jar in the lib\ folder
powershell -ExecutionPolicy Bypass -File .\build.ps1
powershell -ExecutionPolicy Bypass -File .\run.ps1
```

**Conventions used in this guide**
* Menu numbers refer to the console menu (`1` create customer ... `17` reload config).
* **Set the business date to `2026-11-24` first** (menu 14) - the age examples depend on it. If you test on another
  day, shift the DOBs accordingly (age is computed on the business date).
* Standard application inputs ("STD"): `PERSONAL`, branch `BR001`, amount `100000`, tenure `12`, income `50000`,
  obligations `0`, no co-borrower  ->  EMI **8,884.88**, FOIR **17.77%**, eligible.
* Standard customer DOB: `1990-01-01`. Mobiles below (`90000001xx`) are examples; any unused 10 digits work.
* "Create application" = menu 3, "Submit" = menu 5. Applications are identified by the number printed (`LN0000000x`).
* The **Core Data Push scheduler runs every 30 s**, so a DISBURSED application may reach `EXIT` by itself. To watch the
  intermediate state, set `scheduler.enabled=false` in `config.properties` (use menu 12 manually instead).
* SQL checks: `mysql -u root -p miniloan -e "<query>"`.

---------------------------------------------------------------------------------------------------
## Scenario 1 - Product minimum / maximum boundaries

Each row = menu 3 then menu 5. A REJECTED application frees the customer's slot, so one customer can run the
rejections in sequence; each customer's final *passing* application stays UNDER_REVIEW (active).

**PERSONAL** (limits 50,000-5,00,000 / 12-60 months / income >= 20,000 / 12%) - customer `9000000101`

| # | Amount | Tenure | Income | Expected on submit |
|---|-------:|-------:|-------:|--------------------|
| a | 49,999 | 12 | 50,000 | REJECTED - amount below range |
| b | 500,001 | 12 | 100,000 | REJECTED - amount above range |
| c | 100,000 | 11 | 50,000 | REJECTED - tenure 11 outside 12-60 |
| d | 100,000 | 61 | 50,000 | REJECTED - tenure 61 outside 12-60 |
| e | 100,000 | 12 | 19,999 | REJECTED - income below 20,000 |
| f | **50,000** | **12** | **20,000** | **UNDER_REVIEW** (EMI 4,442.44, FOIR 22.21%) - all minimums at once |

Customer `9000000102`: amount **500,000**, tenure **60**, income 100,000 -> **UNDER_REVIEW** (EMI 11,122.22, FOIR 11.12%) - all maximums at once.

**EDUCATION** (limits 1,00,000-10,00,000 / 12-84 months / income >= 15,000 / 10% / no co-borrower) - customer `9000000103`

| # | Amount | Tenure | Income | Co-borrower | Expected |
|---|-------:|-------:|-------:|-------------|----------|
| a | 99,999 | 12 | 50,000 | - | REJECTED - amount below range |
| b | 1,000,001 | 84 | 200,000 | - | REJECTED - amount above range |
| c | 100,000 | 85 | 50,000 | - | REJECTED - tenure 85 outside 12-84 |
| d | 100,000 | 84 | 14,999 | - | REJECTED - income below 15,000 (FOIR 11.07% is fine, only income fails) |
| e | 100,000 | 12 | 50,000 | `Rita` | REJECTED - EDUCATION does not allow a co-borrower |
| f | **100,000** | **84** | **15,000** | - | **UNDER_REVIEW** (EMI 1,660.12, FOIR 11.07%) |

Customer `9000000104`: amount **1,000,000**, tenure **84**, income 100,000 -> **UNDER_REVIEW** (EMI 16,601.18, FOIR 16.60%).

*Multi-rule reporting:* create an EDUCATION draft for a customer with DOB `2010-01-01` with amount 50,000, tenure 6,
income 10,000, obligations 5,000, co-borrower `Rita` and submit -> REJECTED showing **all six** failures together
(age 16, amount, tenure, income, co-borrower, FOIR 135.78%). Menu 6 shows the same list stored in *Eligibility notes*.

(PERSONAL with a co-borrower is allowed and passes.)

---------------------------------------------------------------------------------------------------
## Scenario 2 - Ages 20, 21, 60, 61 (business date 2026-11-24)

Create 4 customers (menu 1), each with one STD PERSONAL application, then submit:

| Customer mobile | DOB | Age on 2026-11-24 | Expected |
|---|---|---:|---|
| 9000000020 | 2005-11-25 | 20 | **REJECTED** - "Age 20 ... outside the allowed 21-60 years." |
| 9000000021 | 2005-11-24 | 21 | **UNDER_REVIEW** (21st birthday today) |
| 9000000060 | 1966-11-24 | 60 | **UNDER_REVIEW** (60th birthday today) |
| 9000000061 | 1965-11-24 | 61 | **REJECTED** - "Age 61 ..." |

Also: menu 1 with DOB `2027-01-01` -> `ERROR ... Date of birth 2027-01-01 is in the future.` (customer is not created).

---------------------------------------------------------------------------------------------------
## Scenario 3 - Duplicate mobile / one active application per product

1. Menu 1: name `Dup Tester`, mobile `9000000030`. Menu 3: STD PERSONAL -> DRAFT (active).
2. Menu 1 again with mobile `9000000030` -> **ERROR**: mobile already registered to customer #n, and it lists the
   active PERSONAL application and states a second one is not allowed.
3. Menu 3 for the same customer, PERSONAL -> **ERROR** "already has an active PERSONAL application LN... in status DRAFT".
4. Menu 3 for the same customer, **EDUCATION** (amount 100,000 / 84 / 50,000) -> **allowed** (different product).
5. Submit the PERSONAL one (UNDER_REVIEW - still active, step 3 error repeats). Menu 9 reject with reason `test` ->
   REJECTED. Menu 3 PERSONAL again -> **allowed** (previous application is REJECTED).
6. Mobile format: `900000003` (9 digits) and `90000A0003` -> `Mobile number must be exactly 10 digits.`
7. DB-level backstop (bypasses the app): with an active PERSONAL application for customer id `N`, run
   ```sql
   INSERT INTO loan_application (application_number,customer_id,product,branch_code,branch_name,requested_amount,
     tenure_months,monthly_income,existing_monthly_obligations,annual_interest_rate,status,created_at)
   VALUES ('MANUAL1',N,'PERSONAL','BR001','Mumbai Main',100000,12,50000,0,12,'DRAFT',NOW());
   ```
   -> `ERROR 1062 ... uq_one_active_per_prd`.

---------------------------------------------------------------------------------------------------
## Scenario 4 - Worked EMI example

Customer `9000000040`, STD PERSONAL (100,000 / 12 months / 12%), submit.
Expected: **EMI INR 8,884.88**, **FOIR 17.77%** (= 8,884.88 / 50,000), status UNDER_REVIEW.
Check in the DB: `SELECT application_number, proposed_emi, foir_percentage, annual_interest_rate FROM loan_application;`
-> `8884.88 | 17.77 | 12.00`. Amounts are shown in Indian grouping everywhere (e.g. `INR 1,00,000.00`).

---------------------------------------------------------------------------------------------------
## Scenario 5 - FOIR exactly 50.00% vs just above

PERSONAL 100,000 / 12 months, **income 20,000** (the minimum), EMI 8,884.88:

| Customer | Existing obligations | FOIR = (obl + 8,884.88) / 20,000 | Expected |
|---|---:|---:|---|
| 9000000050 | 1115.12 | **50.00%** | **UNDER_REVIEW** (50.00% passes) |
| 9000000051 | 1117.12 | **50.01%** | **REJECTED** - "FOIR 50.01% exceeds the maximum of 50.00%" |

---------------------------------------------------------------------------------------------------
## Scenario 6 - Invalid state transitions

Use customer `9000000070`, STD PERSONAL draft, and keep notes of expected messages. Every failure prints
`ERROR: Invalid state transition: X -> Y. Allowed from X: ...` and writes **no** history row (check with menu 6).

| Step | Action | Expected |
|---|---|---|
| 1 | Menu 8 approve the DRAFT | `DRAFT -> APPROVED` invalid, allowed `[UNDER_REVIEW, REJECTED]` |
| 2 | Menu 10 disburse the DRAFT | `DRAFT -> DISBURSED` invalid |
| 3 | Menu 4 edit amount to `120000`, menu 5 submit | edit works (DRAFT); submit -> UNDER_REVIEW |
| 4 | Menu 5 submit again | `UNDER_REVIEW -> UNDER_REVIEW` invalid |
| 5 | Menu 4 edit | `ERROR ... Only DRAFT applications can be edited.` |
| 6 | Menu 10 disburse (UNDER_REVIEW) | `UNDER_REVIEW -> DISBURSED` invalid |
| 7 | Menu 9 reject with **empty reason** | `A rejection reason is mandatory.` |
| 8 | Menu 9 reject with reason `Docs mismatch` | REJECTED |
| 9 | On the REJECTED app: menus 8, 5, 4, 10 | all refused; message says `none (terminal state)` |
| 10 | On an APPROVED app (e.g. scenario 4): menu 8 again, menu 9, menu 4, menu 5 | all refused (`APPROVED -> APPROVED`, `APPROVED -> REJECTED` ...) |
| 11 | On a DISBURSED / EXIT app: menus 8, 9, 5, 4 | all refused; there is no menu path to CORE_BANKING_PUSH or EXIT - only the utility does that |

---------------------------------------------------------------------------------------------------
## Scenario 7 - Generated Application Form

1. Menu 8 approve the scenario-4 application (reviewer `REV001`).
2. Console prints `Application Form generated: ...\output\application_form_LN0000000x.html`.
3. Open that file in a browser: sections **Applicant**, **Loan Details**, **EMI & FOIR Assessment** (EMI 8,884.88, FOIR 17.77%),
   **Credit Approval** (reviewer, approval time, remarks). Amounts use Indian grouping.
4. Menu 6 shows the path under *Application form*; `SELECT application_form_path FROM loan_application WHERE status IN ('APPROVED','DISBURSED','EXIT');`
5. If the output folder is not writable, the approval is **rolled back** (status stays UNDER_REVIEW) - an approval without its form is not allowed.

---------------------------------------------------------------------------------------------------
## Scenario 8 - Duplicate / repeated disbursement attempts

1. Menu 10 on the approved scenario-4 application, reference blank -> **Disbursement confirmation** (application number,
   customer, `INR 1,00,000.00`, auto reference `DSByyyyMMddNNNNNN`, date).
2. Menu 10 on the **same** application again -> `ERROR ... has already been disbursed (reference DSB... ). Duplicate disbursement refused.`
3. Approve the scenario-5 (50.00%) application, then menu 10 and type the **first reference** manually ->
   `ERROR ... reference ... is already used. References must be globally unique.` Application stays APPROVED. Retry with a blank reference -> succeeds.
4. DB checks (both must return **no rows**):
   ```sql
   SELECT application_id, COUNT(*) FROM disbursement GROUP BY application_id HAVING COUNT(*) > 1;
   SELECT reference,      COUNT(*) FROM disbursement GROUP BY reference      HAVING COUNT(*) > 1;
   ```
5. DB backstop: `INSERT INTO disbursement (application_id,reference,amount,disbursed_at) VALUES (<same id>,'X1',1,NOW());`
   -> `ERROR 1062 ... uq_disb_application`.
6. Core push lifecycle: menu 12 (or wait <= 30 s) -> application goes `DISBURSED -> CORE_BANKING_PUSH -> EXIT`;
   menu 6 shows both transitions by `SYSTEM:CORE_DATA_PUSH`; menu 16 lists the `ng_core_banking` row (name, DOB, approved and
   disbursed amount/date). Run menu 12 again -> `Pushed: 0` (no duplicate). Check:
   `SELECT application_id, COUNT(*) FROM ng_core_banking GROUP BY application_id HAVING COUNT(*) > 1;` -> no rows.

---------------------------------------------------------------------------------------------------
## Scenario 9 - Controlled transaction failure with full rollback

Menu **15**, confirm `y`. Expected output (ids and references will differ):

```
Created test customer #n ROLLBACK TEST 2026... (mobile 9xxxxxxxxx)
[PASS] Test application LN... submitted -> UNDER_REVIEW
[PASS] Approved -> APPROVED (EMI 8884.88)
A) Disbursing with an INJECTED failure (after disbursement insert + status update + history write) ...
   caught as expected: INJECTED FAILURE after disbursement insert, status update and history write
[PASS]    status still APPROVED
[PASS]    no disbursement row for the application
[PASS]    total disbursement rows unchanged (..)
[PASS]    no extra application_history row (..)
   retry without failure: disbursed, reference DSB...
[PASS]    status DISBURSED, 1 disbursement row, +1 history row
B) Core data push with an INJECTED failure (after ng_core_banking insert + first status change) ...
   utility_run result: FAILED | App n: Simulated failure after core insert (transaction will roll back)
[PASS]    status still DISBURSED (not CORE_BANKING_PUSH / EXIT)
[PASS]    no ng_core_banking row for the application
[PASS]    ng_core_banking total unchanged (..)
[PASS]    no extra application_history row (..)
[PASS] C) retry without failure: status EXIT, exactly 1 core row, +2 history rows (CORE_BANKING_PUSH, EXIT)
RESULT: ALL CHECKS PASSED - failed transactions left no partial data.
```
Why it proves atomicity: in A the failure is thrown *after* the three writes succeeded but *before* `commit()`; in B after the
core row and the first status change. In both, `rollback()` leaves the database exactly as before. Menu 16 shows the failed
core-push run in `utility_run` (logged in its own transaction). Other DISBURSED applications, if any, are handled the same way.

---------------------------------------------------------------------------------------------------
## Scenario 10 - Review age 2 days vs 3 days (business date)

Precondition: UNDER_REVIEW applications submitted on business date `2026-11-24` (e.g. from scenarios 1-5 that you did not approve/reject).
Create one more for a clear example: customer `9000000100`, STD PERSONAL, submit -> note its application id `X`.

| Business date (menu 14) | Age | Menu 11 expected |
|---|---:|---|
| `2026-11-26` | **2 days** | `New alerts created: 0`; CSV `OverdueFlag = N` for these rows; menu 13 -> report 4 (as-of 2026-11-26) lists nothing |
| `2026-11-27` | **3 days** | `New alerts created: N` (one per UNDER_REVIEW app submitted on 11-24); CSV `OverdueFlag = Y`; report 4 shows `days_waiting = 3` |

Checks:
* CSV: `output\pending_reviews_20261126.csv` and `pending_reviews_20261127.csv` (columns ApplicationNumber, CustomerName, Product, RequestedAmount, SubmittedAt, DaysWaiting, OverdueFlag).
* `SELECT * FROM system_alerts WHERE application_id = X;` -> empty after the 2-day run, one `REVIEW_OVERDUE` row after the 3-day run.
* The threshold comes from `overdue.days=2` in `config.properties` (menu 17 reloads it). Note: report 4 in `reports.sql` has `> 2` written in the SQL.

---------------------------------------------------------------------------------------------------
## Scenario 11 - Repeat utility execution, no duplicate alerts

With business date still `2026-11-27`:
1. Menu 11 again, and once more -> **`New alerts created: 0`** each time; `Pending` count unchanged; the CSV is rewritten.
2. Menu 16: `utility_run` has one row per execution (the first one with alerts, later ones with 0); `system_alerts` count is unchanged.
3. `SELECT application_id, alert_type, COUNT(*) FROM system_alerts GROUP BY application_id, alert_type HAVING COUNT(*) > 1;` -> **no rows**
   (guaranteed by `UNIQUE(application_id, alert_type)`; the scheduled 09:00 run and manual runs can overlap safely).
4. Menu 14 -> `clear` to return to the config/today business date.

---------------------------------------------------------------------------------------------------
## Scenario 12 - Data persistence across restart

1. Record counts:
   ```sql
   SELECT (SELECT COUNT(*) FROM customer) c, (SELECT COUNT(*) FROM loan_application) a,
          (SELECT COUNT(*) FROM application_history) h, (SELECT COUNT(*) FROM disbursement) d,
          (SELECT COUNT(*) FROM system_alerts) s, (SELECT COUNT(*) FROM ng_core_banking) n,
          (SELECT COUNT(*) FROM utility_run) u;
   ```
2. Menu `0` to exit. Restart with `run.ps1`.
3. Menu 7 (list), menu 6 on any application (history intact), menu 16 (runs, alerts, core records intact), and re-run the SQL - **identical counts**.
4. Re-running `schema.sql` does not wipe anything (`IF NOT EXISTS`, `INSERT IGNORE`). The menu-set business date is **not** persisted - after restart it is
   back to `business.date` in config (or today); that is by design.
5. Bonus: close the window abruptly (without `0`) and restart - committed data is still there; an interrupted transaction left nothing partial.

---------------------------------------------------------------------------------------------------
## Appendix - the 6 reports (menu 13) quick check

| # | What to enter | What to look for |
|---|---|---|
| 1 Application Register | blank filters, then `PERSONAL` / `UNDER_REVIEW` | number, customer, product, amount, EMI, status, submission date; filters narrow rows |
| 2 Product Pipeline | none | two tables: (product, status) and (branch, product) with counts and sums |
| 3 Disbursement | `MONTHLY`, from `2026-11-01`, to `2026-11-30` (also DAILY / QUARTERLY) | period label, count, total; second table is branch-wise |
| 4 Overdue Reviews | as-of date | only UNDER_REVIEW apps older than 2 days, with `days_waiting` |
| 5 Customers Without Disbursement | none | customers with >= 1 application and no disbursement (seed customers appear only once they apply) |
| 6 Repeat Applicants | none | customers with > 1 application (e.g. `9000000101`, `9000000103`, `9000000030`) with count and total requested |
