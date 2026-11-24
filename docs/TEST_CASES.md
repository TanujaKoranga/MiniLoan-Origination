# Mini Loan Origination System - Verification & Test Cases

| TC ID | Test Scenario | Menu Option | Input / Precondition | Expected Outcome | Status |
| :--- | :--- | :---: | :--- | :--- | :---: |
| TC-01 | Age Boundary - Underage (Age 20) | 1 / 5 | DOB resulting in age 20 on business date | Rejected with age validation error (21-60 required) | PASS |
| TC-02 | Age Boundary - Valid Min/Max (21 & 60) | 1 / 5 | DOB resulting in age 21 and age 60 | Accepted; proceeds through eligibility check | PASS |
| TC-03 | Age Boundary - Overage (61) & Future DOB | 1 / 5 | DOB resulting in age 61 or future date | Rejected with clear validation reason | PASS |
| TC-04 | Mobile Validation & Uniqueness | 1 | 9-digit mobile or duplicate 10-digit mobile | Rejected (10 digits and unique enforced) | PASS |
| TC-05 | Product Limits - PERSONAL vs EDUCATION | 3 / 5 | Amount/tenure/income inside & outside config bounds | Out-of-range values rejected with all failed rules listed | PASS |
| TC-06 | EMI & FOIR Pass Case (46.28% <= 50%) | 3 / 5 | PERSONAL, Amount 1,00,000, Tenure 12, Income 30,000, Obligations 5,000 | EMI = 8,884.88, FOIR = 46.28% -> UNDER_REVIEW | PASS |
| TC-07 | EMI & FOIR Fail Case (52.95% > 50%) | 3 / 5 | PERSONAL, Amount 1,00,000, Tenure 12, Income 30,000, Obligations 7,000 | EMI = 8,884.88, FOIR = 52.95% -> REJECTED | PASS |
| TC-08 | Duplicate Active Application Guard | 3 | Second PERSONAL app while first is DRAFT/UNDER_REVIEW/APPROVED | Blocked; allowed only after REJECTED or DISBURSED | PASS |
| TC-09 | Edit Restriction Outside DRAFT | 4 | Attempt to edit application in UNDER_REVIEW or APPROVED | Blocked (Editing allowed only in DRAFT) | PASS |
| TC-10 | Reviewer Approval & Form Generation | 8 | Approve an UNDER_REVIEW application | Moves to APPROVED, logs history, generates form file in output/ | PASS |
| TC-11 | Reviewer Rejection with Mandatory Reason | 9 | Reject UNDER_REVIEW app with blank vs valid reason | Blank reason rejected; valid reason moves to REJECTED | PASS |
| TC-12 | Atomic Disbursement & Duplicate Ref Guard | 10 | Disburse APPROVED loan with unique ref; retry duplicate ref | First succeeds (DISBURSED + receipt); duplicate ref rejected | PASS |
| TC-13 | Controlled Transaction Rollback Test | 15 | Trigger simulated failure mid-transaction | Full DB rollback verified; zero partial rows committed | PASS |
| TC-14 | Overdue Review Utility & Repeat-Run Safety | 14 + 11 | Advance business date +3 days, run Option 11 twice | Flags REVIEW_OVERDUE once (no duplicate), exports CSV | PASS |
| TC-15 | Core Data Push Utility & EXIT Transition | 12 | Run CoreDataPush on DISBURSED application twice | Pushes once to NG_CORE_BANKING, transitions status to EXIT | PASS |
| TC-16 | Six Parameterized SQL Reports | 13 | Execute Reports 1 through 6 | All 6 queries execute cleanly with Indian currency formatting | PASS |
