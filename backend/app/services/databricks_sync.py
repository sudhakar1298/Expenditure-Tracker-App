"""Durable, idempotent API -> Databricks Bronze/Silver delivery."""
import json
import logging
import threading
import time
import uuid
from datetime import datetime, timedelta

import httpx
from sqlalchemy import or_
from app.core.databricks_config import databricks_settings
from app.models.databricks_outbox import DatabricksOutbox

log = logging.getLogger(__name__)


def safe_error_label(exc):
    """Return actionable diagnostics without persisting response bodies or secrets."""
    if isinstance(exc, httpx.HTTPStatusError):
        response = exc.response
        endpoint = response.request.url.path
        if endpoint.startswith("/api/2.0/sql/statements/"):
            endpoint = "/api/2.0/sql/statements/{id}"
        try:
            error_code = response.json().get("error_code")
        except (ValueError, AttributeError):
            error_code = None
        details = [f"Databricks HTTP {response.status_code}"]
        if error_code:
            details.append(str(error_code)[:80])
        details.extend((response.request.method, endpoint))
        return " ".join(details)
    return type(exc).__name__


def queue_expense(db, expense, operation="upsert"):
    # Called in the same DB transaction as the expense write. Queue even while
    # disabled so saves can be delivered after credentials are configured.
    db.flush()
    # Drafts remain available through the API, but Databricks only receives a
    # transaction after the user confirms it. Remove any older queued upsert
    # too, so changing a transaction back to pending cannot leak stale data.
    if operation == "upsert" and expense.is_pending:
        db.query(DatabricksOutbox).filter(
            DatabricksOutbox.expense_id == expense.id
        ).delete(synchronize_session=False)
        return
    data = {key: getattr(expense, key) for key in
            ("id", "amount", "merchant", "category", "source", "reason")}
    data["transaction_date"] = expense.transaction_date.isoformat()
    db.merge(DatabricksOutbox(expense_id=expense.id, revision=str(uuid.uuid4()),
        operation=operation, payload=json.dumps(data), attempts=0,
        last_error=None, next_attempt=None))


class DatabricksWriter:
    def __init__(self, settings=databricks_settings, client=None):
        settings.validate_connection()
        self.settings = settings
        self.client = client or httpx.Client(timeout=20, follow_redirects=False)
        self.origin = settings.DATABRICKS_HOST.rstrip("/")
        self.prefix = f"`{settings.DATABRICKS_CATALOG}`.`{settings.DATABRICKS_SCHEMA}`"

    def execute(self, statement, parameters=None):
        headers = {"Authorization": "Bearer " + self.settings.DATABRICKS_TOKEN.get_secret_value()}
        response = self.client.post(self.origin + "/api/2.0/sql/statements", headers=headers,
            json={"warehouse_id": self.settings.DATABRICKS_WAREHOUSE_ID,
                  "statement": statement, "parameters": parameters or [], "wait_timeout": "0s"})
        response.raise_for_status()
        result = response.json()
        deadline = time.monotonic() + 90
        while result.get("status", {}).get("state") != "SUCCEEDED":
            state = result.get("status", {}).get("state")
            if state not in ("PENDING", "RUNNING"):
                # Do not persist response bodies, SQL values, or credentials in logs.
                raise RuntimeError("Databricks SQL state: " + str(state))
            if time.monotonic() >= deadline:
                self.client.post(self.origin + "/api/2.0/sql/statements/" + result["statement_id"] + "/cancel", headers=headers)
                raise TimeoutError("Databricks statement timed out; queued for retry")
            time.sleep(1)
            response = self.client.get(self.origin + "/api/2.0/sql/statements/" + result["statement_id"], headers=headers)
            response.raise_for_status()
            result = response.json()
        return result

    def write(self, data, operation="upsert"):
        self.write_batch([data], operation)

    def write_batch(self, rows, operation="upsert"):
        if not rows:
            return
        parameters = [{"name": "rows", "value": json.dumps(rows, allow_nan=False), "type": "STRING"}]
        schema = "ARRAY<STRUCT<id:STRING,amount:DOUBLE,merchant:STRING,category:STRING,source:STRING,reason:STRING,transaction_date:STRING>>"
        source = f"(SELECT inline(from_json(:rows, '{schema}', map('mode', 'FAILFAST'))))"
        if operation == "delete":
            for table, key in (("bronze_expenses", "id"), ("silver_expenses", "expense_id")):
                self.execute(f"MERGE INTO {self.prefix}.`{table}` AS target USING {source} AS source "
                             f"ON target.{key} = source.id WHEN MATCHED THEN DELETE", parameters)
            return
        for table, key, date_column, date_expression, extra in (
            ("bronze_expenses", "id", "transaction_date", "transaction_date",
             ", current_timestamp() AS ingestion_timestamp, 'RAW' AS data_quality_status"),
            ("silver_expenses", "expense_id", "transaction_timestamp", "CAST(transaction_date AS TIMESTAMP)", ""),
        ):
            columns = [key, "amount", "merchant", "category", "source", "reason", date_column]
            if extra:
                columns += ["ingestion_timestamp", "data_quality_status"]
            updates = ", ".join(f"target.{col} = source.{col}" for col in columns if col != key)
            self.execute(f"""MERGE INTO {self.prefix}.`{table}` AS target
                USING (SELECT id AS {key}, amount, merchant, category, source, reason,
                    {date_expression} AS {date_column}{extra} FROM {source} AS batch) AS source
                ON target.{key} = source.{key}
                WHEN MATCHED THEN UPDATE SET {updates}
                WHEN NOT MATCHED THEN INSERT ({', '.join(columns)})
                VALUES ({', '.join('source.' + col for col in columns)})""", parameters)


def deliver_pending(session_factory, writer):
    from app.models.expense import Expense
    with session_factory() as db:
        # Clean up draft upserts created by versions that queued SMS drafts.
        pending_draft_ids = [row[0] for row in db.query(DatabricksOutbox.expense_id).join(
            Expense, Expense.id == DatabricksOutbox.expense_id
        ).filter(
            DatabricksOutbox.operation == "upsert",
            Expense.is_pending.is_(True),
        ).all()]
        if pending_draft_ids:
            db.query(DatabricksOutbox).filter(
                DatabricksOutbox.expense_id.in_(pending_draft_ids)
            ).delete(synchronize_session=False)
            db.commit()
        entries = db.query(DatabricksOutbox).filter(or_(
            DatabricksOutbox.next_attempt.is_(None),
            DatabricksOutbox.next_attempt <= datetime.utcnow())).outerjoin(
                Expense, Expense.id == DatabricksOutbox.expense_id
            ).order_by(Expense.created_at.desc(), DatabricksOutbox.expense_id).limit(50).all()
        snapshots = [(r.expense_id, r.revision, r.operation, r.payload, r.attempts) for r in entries]
    for operation in ("upsert", "delete"):
        group = [row for row in snapshots if row[2] == operation]
        if not group:
            continue
        error = None
        try:
            writer.write_batch([json.loads(row[3]) for row in group], operation)
        except Exception as exc:
            error = safe_error_label(exc)
            log.warning("Databricks delivery pending (%s)", error)
        with session_factory() as db:
            # A concurrent edit gets a new revision and must not be acknowledged
            # by the completion of this older snapshot.
            for expense_id, revision, _, payload, attempts in group:
                query = db.query(DatabricksOutbox).filter_by(expense_id=expense_id, revision=revision)
                if error:
                    query.update({"attempts": attempts + 1, "last_error": error,
                        "next_attempt": datetime.utcnow() + timedelta(seconds=min(300, 15 * 2 ** min(attempts, 5)))})
                else:
                    query.delete()
            db.commit()


def start_worker(session_factory):
    stop = threading.Event()
    if not databricks_settings.DATABRICKS_SYNC_ENABLED:
        return stop, None
    writer = DatabricksWriter()
    def work():
        try:
            while not stop.is_set():
                try:
                    deliver_pending(session_factory, writer)
                except Exception as exc:
                    log.warning("Databricks worker retry (%s)", type(exc).__name__)
                stop.wait(databricks_settings.DATABRICKS_SYNC_INTERVAL_SECONDS)
        finally:
            writer.client.close()
    thread = threading.Thread(target=work, daemon=True, name="databricks-sync")
    thread.start()
    return stop, thread
