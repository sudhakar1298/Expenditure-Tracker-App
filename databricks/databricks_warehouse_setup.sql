-- ============================================================
-- DATABRICKS DATA WAREHOUSE & METASTORE DATABASE DDL SETUP
-- Database: expense_tracker_dw
-- ============================================================

CREATE DATABASE IF NOT EXISTS expense_tracker_dw;
USE expense_tracker_dw;

-- 1. BRONZE LAYER TABLE (Raw Records Ingestion)
CREATE TABLE IF NOT EXISTS expense_tracker_dw.bronze_expenses (
    id STRING,
    amount DOUBLE,
    merchant STRING,
    category STRING,
    source STRING,
    transaction_date STRING,
    ingestion_timestamp TIMESTAMP,
    data_quality_status STRING
)
USING DELTA
LOCATION 'dbfs:/mnt/expense_tracker/delta/bronze';

-- 2. SILVER LAYER TABLE (Normalized & Cleaned Records)
CREATE TABLE IF NOT EXISTS expense_tracker_dw.silver_expenses (
    expense_id STRING,
    amount DOUBLE,
    merchant STRING,
    category STRING,
    source STRING,
    transaction_timestamp TIMESTAMP
)
USING DELTA
LOCATION 'dbfs:/mnt/expense_tracker/delta/silver';

-- 3. GOLD LAYER TABLES (Analytical Aggregates)
CREATE TABLE IF NOT EXISTS expense_tracker_dw.gold_category_summary (
    category STRING,
    total_spent DOUBLE,
    transaction_count LONG
)
USING DELTA
LOCATION 'dbfs:/mnt/expense_tracker/delta/gold_category';

CREATE TABLE IF NOT EXISTS expense_tracker_dw.gold_daily_expenses (
    date STRING,
    total_spent DOUBLE,
    transaction_count LONG
)
USING DELTA
LOCATION 'dbfs:/mnt/expense_tracker/delta/gold_daily';

CREATE TABLE IF NOT EXISTS expense_tracker_dw.gold_merchant_summary (
    merchant STRING,
    total_spent DOUBLE,
    transaction_count LONG
)
USING DELTA
LOCATION 'dbfs:/mnt/expense_tracker/delta/gold_merchant';

-- Sample Verification Queries
SELECT category, SUM(amount) AS total_spent
FROM expense_tracker_dw.silver_expenses
GROUP BY category
ORDER BY total_spent DESC;
