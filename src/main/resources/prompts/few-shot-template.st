You are an expert PL/SQL documentation specialist.

Your task is to analyze the following PL/SQL code and generate a Json file based **exactly** on the **structure, level of detail, and business-oriented focus** shown in the Example Analysis below.

The Example is provided **only as a style and structure reference**.
Do **not** reuse its procedure name, table names, business domain, or text.
Do **not** include the Example itself in your final answer.

Your answer must:
- strictly follow the same section headings used in the Example (no new sections, no renamed sections);
- focus on **business logic and business rules**, not on low-level syntax;
- avoid inventing business rules, tables, or behaviors that are not supported by the given PL/SQL code;
- be fully based on the `{code}` provided after the Example.

---

### Example Analysis (START)

# PR_CALCULATE_BONUS - Business Logic Analysis

## Summary of Purpose
This stored procedure is responsible for the quarterly calculation and allocation of sales bonuses for the Sales and Account Management teams. The core business logic is to differentiate bonus percentages based on job role and whether the individual exceeded their quarterly quota.

## Main Logic Flow
* **Step 1:** Fetches all active employee IDs from the `EMPLOYEES` table where `JOB_TITLE` is 'Sales' or 'Account Manager'.
* **Step 2:** Loops through each employee:
    * **Rule A (Quota Check):** Compares the employee's `QUARTERLY_SALES` against the `QUOTA_TARGET`.
    * **Rule B (Bonus Calculation):**
        * IF sales > target AND job = 'Sales', THEN bonus rate is 5%.
        * IF sales > target AND job = 'Account Manager', THEN bonus rate is 3%.
        * ELSE (sales <= target), THEN bonus rate is 1% (baseline participation bonus).
* **Step 3:** Inserts the calculated bonus amount and date into the `BONUS_HISTORY` table.
* **Step 4:** Commits the transaction after all employees have been processed successfully.

## Data/Table Interaction
* `EMPLOYEES`: Read-only (SELECT) to determine eligibility, job title, and quota performance. **Business Rule:** Only records with `STATUS='ACTIVE'` are considered.
* `BONUS_HISTORY`: Insert-only to record the final payout amount. **Business Rule:** Acts as the official audit trail for all bonus disbursements.

## Error/Exception Handling
* **WHEN OTHERS:** Catches any unforeseen database error (like a deadlock or constraint violation) and logs a message to the `APP_LOG` table, followed by an immediate ROLLBACK to prevent partial bonus payments.

### Example Analysis (END)

---

### PL/SQL Code to Analyze

```plsql
{code}
```

### Output format:

```json
{format}
```