# API saves to Databricks

The backend loads `databricks/.env` using an absolute path. It is ignored by Git;
`.env.example` is safe to commit. Environment variables override file values.

Fill in locally:

- `DATABRICKS_HOST`: HTTPS workspace origin, without a workspace UI path.
- `DATABRICKS_TOKEN`: your personal access token (PAT).
- `DATABRICKS_WAREHOUSE_ID`: SQL warehouse ID from its Connection details.
- `DATABRICKS_CATALOG`: the catalog that contains your tables.
- `DATABRICKS_SCHEMA`: defaults to `expense_tracker_dw`, matching the existing SQL.

The PAT's identity needs warehouse use and table write privileges. Run from the
repository root to verify access, create missing managed Delta tables, add
`reason` to existing tables, and queue expenses saved before this integration:

```powershell
python databricks/setup_remote_sync.py --setup --backfill
```

This preserves existing data and uses the existing Bronze/Silver column names.
It avoids the old script's hard-coded DBFS mount locations. Set
`DATABRICKS_SYNC_ENABLED=true`, then restart the backend:

```powershell
python -m uvicorn app.main:app --app-dir backend --host 0.0.0.0 --port 8000
```

Use one backend worker for this delivery loop. The local DB must be persistent.
Do not run the Spark API-to-tables job concurrently with this writer.

Each create, edit, or delete queues an event in the same local database transaction.
Every 15 seconds the worker selects up to 50 pending events, prioritizing recently
created expenses. It sends a parameterized JSON batch in two SQL MERGEs to Bronze
and Silver, rather than two statements per expense. Deletes are batched separately.
Delivery succeeds only when both SQL statements report `SUCCEEDED`.
Failed events remain queued with bounded retry backoff. A restart preserves the
queue. Existing Gold summary tables are not automatically refreshed by this writer;
their existing aggregation job must still run.

Inspect `GET /api/v1/databricks/status` for enabled state, pending events, and
events with failed attempts. An API save confirms local persistence, not remote
delivery. The status endpoint does not expose the PAT. Test failures can mean
missing credentials, table permissions, missing tables/reason columns, or an
unavailable SQL warehouse.

The remote API follows the [Databricks Statement Execution contract](https://docs.databricks.com/api/statement-execution/v1/statement-execution),
including polling and SQL failures returned inside HTTP 200 responses.

For a phone to work while your PC is off, deploy this backend with persistent
storage to an always-online host and configure its URL on the phone. This change
does not deploy a cloud service or package the PAT in Android. The current API
has no user authentication; add authentication before a public deployment.

Tests: `python backend/tests/test_databricks_delivery.py` uses an isolated DB and
mocked Databricks responses. A real workspace write can only be verified after
the `.env` fields have been filled in and the setup command succeeds.
