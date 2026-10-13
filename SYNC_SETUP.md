# Mobile delivery and Databricks

The backend now includes a durable Databricks delivery worker. For the current
`.env` setup and automatic Bronze/Silver delivery, follow
[databricks/REMOTE_SYNC.md](databricks/REMOTE_SYNC.md). The scheduled Spark job
below is an alternative; do not run both writers concurrently.

The phone writes to FastAPI. The website now proxies `/api/v1` to FastAPI
and refreshes Home/Transactions every 10 seconds and on window focus.

## Phone

Install `app/build/outputs/apk/debug/app-debug.apk` as an update (do not uninstall
or clear app storage). On the current Wi-Fi, the backend is
`http://192.168.1.37:8000/api/v1/expenses/`. Set that in app Settings if a previous
address was saved. This LAN address may change; update Settings if it does.

Save an amount, merchant/purpose, and optional reason. The sync banner reports
delivery or an upload pending. Failed requests persist in an on-device outbox;
they retry while the app is open (every 15 seconds), at app startup, and after
changing the backend URL. Background delivery while the app is killed is not
scheduled. Pulling no longer deletes local entries; older missing entries are
submitted when a full server fetch succeeds.

## Databricks workspace

The existing local Spark checks and JSON files are not a connection to a
Databricks workspace. `databricks/.env` contains a placeholder endpoint, and
the Python scripts do not automatically load that shell file.

Deploy the repository to the intended workspace and schedule the Python task
`databricks/jobs/sync_api_to_tables.py` with parameters:

```
--api-url <backend-expenses-URL-reachable-from-cluster>
--schema <catalog>.<schema>
```

The schema must already exist and the job identity needs write access. The job
merges API records by ID into `bronze_expenses` and `silver_expenses`, including
reason, and preserves existing rows absent from the API. It does not refresh
existing Gold tables. A private LAN address or localhost on this PC is not
reachable from a hosted Databricks cluster without networking setup. Do not
expose this unauthenticated development API publicly to work around that.

The remote job has not been deployed or tested against a workspace. A workspace
URL, target table/schema, and appropriate connected access are still required.

## Verification

`python backend/tests/test_mobile_delivery.py` uses an isolated in-memory DB and
checks reason preservation, retries, distinct saves, and paginated API ingestion.

`python backend/tests/check_live_delivery.py` checks the running website proxy,
backend, and pipeline API reader with one unique probe, then removes only that
probe. This does not claim phone UI or remote Delta-table verification.
