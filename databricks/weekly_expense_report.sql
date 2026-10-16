-- Weekly confirmed-expense report for an AI/BI dashboard subscription.
-- Replace YOUR_CATALOG with the catalog configured in Render.
-- Databricks receives only confirmed expenses, so silver_expenses contains no drafts.
WITH weekly AS (
  SELECT
    category,
    amount,
    merchant,
    transaction_timestamp
  FROM YOUR_CATALOG.expense_tracker_dw.silver_expenses
  WHERE transaction_timestamp >= current_timestamp() - INTERVAL 7 DAYS
)
SELECT
  category,
  COUNT(*) AS transaction_count,
  ROUND(SUM(amount), 2) AS total_spent_inr,
  ROUND(AVG(amount), 2) AS average_expense_inr,
  MAX_BY(merchant, amount) AS largest_expense_merchant,
  ROUND(MAX(amount), 2) AS largest_expense_inr
FROM weekly
GROUP BY category
ORDER BY total_spent_inr DESC;
