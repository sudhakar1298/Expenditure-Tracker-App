# Databricks Medallion Data Engineering Pipeline

PySpark & Delta Lake pipeline implementing the Medallion Architecture (Bronze -> Silver -> Gold) for GPay Expense Tracker data analytics.

## Pipeline Architecture

```
[PostgreSQL / FastAPI]
         │
         ▼
 ┌──────────────┐
 │    BRONZE    │  Raw record ingestion + audit metadata
 └──────┬───────┘
        ▼
 ┌──────────────┐
 │    SILVER    │  Merchant normalization ("swiggy-rest" -> "SWIGGY"), string cleaning
 └──────┬───────┘
        ▼
 ┌──────────────┐
 │     GOLD     │  Daily/weekly summaries, category & merchant aggregations
 └──────┬───────┘
        ▼
 [Scheduled Email Report Job]
```

## How to Run Tests Locally

```bash
cd databricks
pip install -r requirements.txt
python tests/test_pipeline.py
```
