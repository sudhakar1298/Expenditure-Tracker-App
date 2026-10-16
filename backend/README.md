# Backend

FastAPI service for Android synchronization, PostgreSQL persistence,
device-bound request authentication and durable Databricks delivery.

For a first deployment, start with the repository root
[`README.md`](../README.md). Render-specific operations are documented in
[`RENDER_DEPLOYMENT.md`](RENDER_DEPLOYMENT.md).

## Local development

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r backend/requirements.txt
$env:PYTHONPATH="backend"
python -m uvicorn app.main:app --app-dir backend --reload
```

The default development database falls back to `backend/expense_tracker.db`.
Do not use SQLite fallback on Render; `render.yaml` disables it.
`backend/.env.example` is a reference template; export local variables in the
shell because the generic backend settings do not automatically load that file.

## Important configuration

| Variable | Purpose |
| --- | --- |
| `DATABASE_URL` | PostgreSQL connection; supplied by Render Blueprint |
| `DATABRICKS_HOST` | HTTPS Databricks workspace origin |
| `DATABRICKS_TOKEN` | Server-side workspace credential |
| `DATABRICKS_WAREHOUSE_ID` | SQL Warehouse ID |
| `DATABRICKS_CATALOG` | Unity Catalog catalog |
| `DATABRICKS_SCHEMA` | Defaults to `expense_tracker_dw` |
| `DEVICE_PAIRING_CODE` | One-time phone pairing secret, 24+ characters |
| `DEVICE_AUTH_ENABLED` | Enforces signed requests after pairing |

Never place server or Databricks credentials in the Android project.

## Tests

```powershell
python -m pytest backend/tests/test_device_auth.py -q
python backend/tests/test_databricks_delivery.py
```
