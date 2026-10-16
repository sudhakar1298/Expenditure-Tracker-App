# Deploy the phone API and Databricks sender on Render

The hosted flow is:

```text
Android phone -> Render FastAPI -> Render PostgreSQL -> Databricks SQL Warehouse
```

The phone sends expenses to FastAPI. FastAPI commits each expense and its
Databricks outbox entry in the same database transaction. The background sender
then merges queued records into the Databricks Bronze and Silver tables. A
temporary Databricks or network failure leaves the entry queued for retry.

## Before deploying

1. Push this repository to GitHub, GitLab, or Bitbucket. Do not commit any
   `.env` file or Databricks token.
2. Make sure the Databricks catalog, schema, `bronze_expenses`, and
   `silver_expenses` tables have been created using the existing Databricks
   setup in this repository.
3. Copy these values from Databricks:
   - Workspace URL, such as `https://dbc-....cloud.databricks.com`
   - Personal access token or service-principal token
   - SQL warehouse ID
   - Unity Catalog catalog name

## Create the Render Blueprint

1. In Render, select **New > Blueprint** and connect this repository.
2. Set **Blueprint Path** to `backend/render.yaml`. Render supports Blueprint
   files outside the repository root.
3. During creation, enter the values requested for `DATABRICKS_HOST`,
   `DATABRICKS_TOKEN`, `DATABRICKS_WAREHOUSE_ID`, `DATABRICKS_CATALOG`,
   `DEVICE_PAIRING_CODE`, and `DEVICE_AUTH_ENABLED`. Use `false` for
   `DEVICE_AUTH_ENABLED` on the first deployment.
4. Deploy the Blueprint. It creates the FastAPI web service and a PostgreSQL
   database in Singapore.

## Pair and lock the API to this phone

Use this order so the phone is paired before enforcement starts:

1. Generate a long one-time code locally:

   ```powershell
   python -c "import secrets; print(secrets.token_urlsafe(32))"
   ```

2. In the Render service's **Environment** page, set
   `DEVICE_PAIRING_CODE` to that generated value. Leave
   `DEVICE_AUTH_ENABLED=false`, then deploy.
3. Install/open the Android app, go to **Profile > Phone security**, enter the
   same code, and tap **Pair this phone**.
4. After the app says **This phone is paired**, change
   `DEVICE_AUTH_ENABLED=true` in Render and deploy once more.

The Android private key is generated inside Android Keystore and is never sent
to Render. Render stores only its public key. Each protected request signs the
HTTP method, exact path/query, timestamp, random nonce, and body hash. Old,
modified, unsigned, or replayed requests are rejected.

Once enforcement is enabled, expense, analytics, reconciliation, and
Databricks-status endpoints require the paired phone's signature. `/health`
remains public for Render's readiness check. The existing browser dashboard
does not possess the phone key and therefore cannot call those protected
endpoints while device-only enforcement is enabled.

If the app is uninstalled, Android's key is lost. To pair the replacement app,
temporarily set `DEVICE_AUTH_ENABLED=false`. On a paid service, run this from a
Render Shell:

```text
python scripts/revoke_paired_device.py --confirm
```

Free Render web services do not include Shell access. For a free service, copy
the PostgreSQL **External Database URL** from Render and run from the cloned
repository instead:

```powershell
$env:DATABASE_URL="PASTE_EXTERNAL_DATABASE_URL"
$env:USE_SQLITE_FALLBACK="false"
$env:PYTHONPATH="backend"
python backend/scripts/revoke_paired_device.py --confirm
```

Then repeat the pairing steps with a new pairing code. Rotate or remove
`DEVICE_PAIRING_CODE` after pairing; an already-paired phone does not need it.

The service is pinned to one instance because that instance owns the retry
worker. Keep it at one instance unless the worker is later moved to a separate
Render background service.

The Blueprint deliberately sets `USE_SQLITE_FALLBACK=false`. Render's local
filesystem is temporary, so silently switching to SQLite would lose phone data
after a restart or redeploy.

## Verify the deployment

Open these URLs, replacing the hostname with the URL shown by Render:

```text
https://YOUR-SERVICE.onrender.com/health
https://YOUR-SERVICE.onrender.com/docs
```

The health response must be `{"status":"ok"}`. Once phone-only authentication
is enabled, protected URLs such as Databricks status intentionally return 401
to an ordinary browser.

Test the same Databricks values locally before entering them in Render:

```powershell
python backend/scripts/check_databricks.py
```

## Point the Android app at Render

In the app's Settings screen, set **Backend API URL** to the Render service
origin, for example:

```text
https://YOUR-SERVICE.onrender.com
```

The app will append `/api/v1/expenses/`. Keep `https://` and do not add a token
to the URL.

## Render free-tier behavior

The free web service sleeps after 15 minutes without incoming traffic. The
first phone request after that can take about a minute while it starts. The
Android outbox will retain the expense and retry it.

Free Render PostgreSQL databases expire after 30 days and have no backups.
Upgrade the database before that deadline if the expense history must remain
available. Databricks rows that were already delivered are separate, but the
PostgreSQL database is still the API's operational record and retry queue.

After the pairing sequence above is complete, the API accepts protected calls
only when they carry a fresh signature from the paired Android Keystore key.
