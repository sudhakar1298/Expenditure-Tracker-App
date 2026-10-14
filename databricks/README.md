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

## Email notifications

`jobs/weekly_email_report.py` generates report HTML, but it does not send mail by
itself. Databricks Workflow notifications can send job status emails on success
or failure. Open the job in **Workflows > Jobs**, copy the numeric job ID from
the URL, and run this from the repository root:

```powershell
python databricks/configure_job_email.py --job-id YOUR_JOB_ID
```

The default recipient is `sudhakarpappu123@gmail.com`, and the script preserves
existing recipients. The token in `databricks/.env` must belong to the job owner
or an identity with **CAN MANAGE** permission on that job. Preview without making
changes by adding `--dry-run`.

Workflow emails contain run status and a link to the run. To receive the actual
weekly spending data, create an AI/BI dashboard using
`weekly_expense_report.sql`, publish it, select **Schedule**, add a weekly
schedule, and add the email address as a subscriber. An administrator must keep
**Settings > Notifications > Enable dashboard email subscriptions** enabled.
