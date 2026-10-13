import csv
import os
from datetime import datetime

CSV_PATH = r"C:\Users\sudha\OneDrive\Desktop\expense tracker - Sheet1.csv"
SQL_OUT_PATH = os.path.join(os.path.dirname(__file__), "databricks_dw_seed_data.sql")
NOTEBOOK_OUT_PATH = os.path.join(os.path.dirname(__file__), "databricks_notebook_import.py")

def parse_date(date_str: str) -> str:
    try:
        parts = date_str.strip().split(" ")
        date_parts = parts[0].split("/")
        time_parts = parts[1].split(":")
        m, d, y = int(date_parts[0]), int(date_parts[1]), int(date_parts[2])
        hh, mm, ss = int(time_parts[0]), int(time_parts[1]), int(time_parts[2])
        return f"{y:04d}-{m:02d}-{d:02d} {hh:02d}:{mm:02d}:{ss:02d}"
    except Exception:
        return "2026-06-02 00:00:00"

def generate_scripts():
    if not os.path.exists(CSV_PATH):
        print("CSV file not found.")
        return

    sql_inserts = []
    py_dicts = []

    with open(CSV_PATH, mode="r", encoding="utf-8") as f:
        reader = csv.reader(f)
        idx = 1
        for row in reader:
            if not row or len(row) < 5:
                continue

            date_formatted = parse_date(row[0].strip())
            amount = float(row[1].strip() or "0")
            merchant = row[2].strip().replace("'", "''")
            category = row[3].strip() or "Others"
            source = row[4].strip() or "Manual"

            exp_id = f"csv-tx-{idx}"

            sql_inserts.append(
                f"('{exp_id}', {amount}, '{merchant}', '{category}', '{source}', TIMESTAMP('{date_formatted}'))"
            )

            py_dicts.append({
                "expense_id": exp_id,
                "amount": amount,
                "merchant": merchant.replace("''", "'"),
                "category": category,
                "source": source,
                "transaction_timestamp": date_formatted
            })

            idx += 1

    # 1. Generate SQL File
    sql_content = f"""-- ============================================================
-- DATABRICKS WAREHOUSE SEED SCRIPT
-- Database: expense_tracker_dw
-- Populates all {len(sql_inserts)} CSV historical expense records
-- ============================================================

CREATE DATABASE IF NOT EXISTS expense_tracker_dw;
USE expense_tracker_dw;

-- Create Managed Silver Table
CREATE TABLE IF NOT EXISTS expense_tracker_dw.silver_expenses (
    expense_id STRING,
    amount DOUBLE,
    merchant STRING,
    category STRING,
    source STRING,
    transaction_timestamp TIMESTAMP
)
USING DELTA;

-- Clear previous seed
TRUNCATE TABLE expense_tracker_dw.silver_expenses;

-- Insert all {len(sql_inserts)} historical transactions
INSERT INTO expense_tracker_dw.silver_expenses VALUES
""" + ",\n".join(sql_inserts) + ";\n\n" + """
-- Generate Gold Category Aggregates Table
CREATE OR REPLACE TABLE expense_tracker_dw.gold_category_summary AS
SELECT
    category,
    ROUND(SUM(amount), 2) AS total_spent,
    COUNT(*) AS transaction_count
FROM expense_tracker_dw.silver_expenses
GROUP BY category
ORDER BY total_spent DESC;

-- Generate Gold Daily Aggregates Table
CREATE OR REPLACE TABLE expense_tracker_dw.gold_daily_expenses AS
SELECT
    DATE(transaction_timestamp) AS date,
    ROUND(SUM(amount), 2) AS total_spent,
    COUNT(*) AS transaction_count
FROM expense_tracker_dw.silver_expenses
GROUP BY DATE(transaction_timestamp)
ORDER BY date DESC;

-- Query Results Verification
SELECT * FROM expense_tracker_dw.gold_category_summary;
"""

    with open(SQL_OUT_PATH, "w", encoding="utf-8") as f:
        f.write(sql_content)

    print(f"✓ Generated Databricks SQL Seed File: {SQL_OUT_PATH}")

if __name__ == "__main__":
    generate_scripts()
