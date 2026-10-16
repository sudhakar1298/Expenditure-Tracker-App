# Databricks setup

The production ingestion path is implemented by the Render backend:

```text
confirmed Android transaction -> Render PostgreSQL outbox
                              -> Databricks SQL Statement API
                              -> Bronze and Silver Delta tables
```

Nothing needs to be uploaded as a Databricks notebook or Workflow job for data
ingestion. In particular, do not run `jobs/sync_api_to_tables.py` or another
API-polling writer at the same time as the Render writer.

## Required workspace resources

Create or select:

1. A Unity Catalog catalog.
2. A Serverless or Pro SQL Warehouse.
3. A PAT or service-principal token for the backend.

The token identity needs `CAN USE` on the warehouse and enough Unity Catalog
privileges to use the catalog/schema and create, select and modify its tables.

## Local configuration

From the repository root:

```powershell
Copy-Item databricks/.env.example databricks/.env
```

Set:

```dotenv
DATABRICKS_SYNC_ENABLED=false
DATABRICKS_HOST=https://your-workspace.cloud.databricks.com
DATABRICKS_TOKEN=your-token
DATABRICKS_WAREHOUSE_ID=your-warehouse-id
DATABRICKS_CATALOG=your-catalog
DATABRICKS_SCHEMA=expense_tracker_dw
DATABRICKS_SYNC_INTERVAL_SECONDS=15
```

- Workspace host: copy the origin from the browser, without `/sql`, query
  strings or a trailing workspace page path.
- Warehouse ID: **SQL Warehouses > warehouse > Connection details**.
- Catalog: the catalog name shown in Catalog Explorer.
- Schema: `expense_tracker_dw` is the repository default.

Install dependencies and create the managed tables:

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r backend/requirements.txt
python databricks/setup_remote_sync.py --setup
```

This safely creates the schema plus `bronze_expenses` and `silver_expenses`,
and adds the `reason` column to older tables without deleting rows.

For an existing local backend database, append `--backfill` to queue confirmed
historical expenses. A brand-new deployment does not need backfill.

## Render variables

Copy the same host, token, warehouse ID and catalog into Render. The Blueprint
at `backend/render.yaml` sets sync enabled and the default schema.
Never place the token in Android, Git, SQL text or a `NEXT_PUBLIC_*` variable.

Verify the credentials locally before copying the same values into Render:

```powershell
python backend/scripts/check_databricks.py
```

## Verify data delivery

After confirming a transaction in Android, run in Databricks SQL:

```sql
SELECT *
FROM YOUR_CATALOG.expense_tracker_dw.silver_expenses
ORDER BY transaction_timestamp DESC;
```

Pending drafts are intentionally excluded. Render retries temporary Databricks
failures from PostgreSQL. With phone-only authentication enabled, the API's
Databricks status endpoint must be called by the paired app; use Render logs or
the Render Shell connection check for administration.

## Optional email reporting

There are two separate email features.

### Workflow run-status email

This sends success/failure and a link, not the spending rows. For an existing
Databricks Workflow job, copy its numeric ID from the job URL and run:

```powershell
python databricks/configure_job_email.py `
  --job-id YOUR_JOB_ID `
  --email you@example.com
```

The token identity must own the job or have `CAN MANAGE`. Add `--dry-run` to
preview without changing the job.

### Weekly spending email

1. Open **SQL Editor** and create a query.
2. Paste `databricks/weekly_expense_report.sql`.
3. Replace `YOUR_CATALOG` with your catalog name and run the query.
4. Add the query to an AI/BI dashboard and publish it.
5. Select **Schedule**, create a weekly schedule and add subscribers.

Workspace administrators must allow dashboard email subscriptions. This is the
option that emails actual expense summaries.

## Directory guide

| File/path | Status |
| --- | --- |
| `setup_remote_sync.py` | Supported table setup and optional backfill |
| `configure_job_email.py` | Supported optional Workflow notification helper |
| `weekly_expense_report.sql` | Supported optional dashboard query |
| `jobs/`, `bronze/`, `silver/`, `gold/` | Reference/local Spark pipeline; not required by Render |
| `databricks_warehouse_setup.sql` | Legacy DBFS/external-table example |

## Tests

```powershell
python backend/tests/test_databricks_delivery.py
python -m pytest databricks/tests -q
```

The first test uses mocks and does not write to a workspace. A real workspace
write is proven only by the setup command, Render connection check and a row in
the Silver table.
