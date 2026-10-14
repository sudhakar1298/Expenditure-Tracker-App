"""Configure Databricks Workflow status emails without replacing other recipients.

Run from the repository root after setting a Databricks token whose identity is
the job owner or has CAN MANAGE on the selected job.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


ROOT_DIR = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT_DIR / "backend"))

from app.core.databricks_config import databricks_settings  # noqa: E402


EVENT_FIELDS = {
    "start": "on_start",
    "success": "on_success",
    "failure": "on_failure",
}


def validate_email(value: str) -> str:
    email = value.strip()
    if not re.fullmatch(r"[^\s@]+@[^\s@]+\.[^\s@]+", email):
        raise argparse.ArgumentTypeError(f"Invalid email address: {value}")
    return email


def merge_email_notifications(
    current: dict | None, email: str, events: list[str]
) -> dict:
    """Return a copy with email added to the selected notification events."""
    merged = {
        key: list(value) if isinstance(value, list) else value
        for key, value in (current or {}).items()
    }
    for event in events:
        field = EVENT_FIELDS[event]
        recipients = list(merged.get(field) or [])
        if not any(recipient.casefold() == email.casefold() for recipient in recipients):
            recipients.append(email)
        merged[field] = recipients
    return merged


class DatabricksJobsClient:
    def __init__(self, host: str, token: str):
        self.host = host.rstrip("/")
        self.headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
        }

    def _request(self, method: str, path: str, payload: dict | None = None) -> dict:
        body = json.dumps(payload).encode("utf-8") if payload is not None else None
        request = Request(
            f"{self.host}{path}", data=body, headers=self.headers, method=method
        )
        try:
            with urlopen(request, timeout=30) as response:
                content = response.read().decode("utf-8")
                return json.loads(content) if content else {}
        except HTTPError as error:
            detail = error.read().decode("utf-8", errors="replace")
            if error.code == 403:
                raise RuntimeError(
                    "Databricks denied access (403). Use a token belonging to the job "
                    "owner, or grant that identity CAN MANAGE on the job."
                ) from error
            raise RuntimeError(
                f"Databricks API returned HTTP {error.code}: {detail[:500]}"
            ) from error
        except URLError as error:
            raise RuntimeError(f"Could not reach Databricks: {error.reason}") from error

    def get_job(self, job_id: int) -> dict:
        query = urlencode({"job_id": job_id})
        return self._request("GET", f"/api/2.1/jobs/get?{query}")

    def update_notifications(self, job_id: int, email_notifications: dict) -> None:
        self._request(
            "POST",
            "/api/2.1/jobs/update",
            {
                "job_id": job_id,
                "new_settings": {
                    "email_notifications": email_notifications,
                    "notification_settings": {
                        "no_alert_for_skipped_runs": True,
                        "no_alert_for_canceled_runs": True,
                        "alert_on_last_attempt": True,
                    },
                },
            },
        )


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Add an email recipient to a Databricks Workflow job."
    )
    parser.add_argument(
        "--job-id",
        type=int,
        required=True,
        help="Numeric ID shown in the Databricks job URL",
    )
    parser.add_argument(
        "--email",
        type=validate_email,
        default="sudhakarpappu123@gmail.com",
    )
    parser.add_argument(
        "--events",
        nargs="+",
        choices=tuple(EVENT_FIELDS),
        default=["success", "failure"],
        help="Job events that should send email (default: success failure)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Show the resulting settings without updating Databricks",
    )
    args = parser.parse_args()

    databricks_settings.validate_connection()
    client = DatabricksJobsClient(
        databricks_settings.DATABRICKS_HOST,
        databricks_settings.DATABRICKS_TOKEN.get_secret_value(),
    )
    job = client.get_job(args.job_id)
    settings = job.get("settings", {})
    merged = merge_email_notifications(
        settings.get("email_notifications"), args.email, args.events
    )

    print(f"Job: {settings.get('name', args.job_id)} ({args.job_id})")
    print(json.dumps({"email_notifications": merged}, indent=2))
    if args.dry_run:
        print("Dry run only; Databricks was not changed.")
        return 0

    client.update_notifications(args.job_id, merged)
    verified = client.get_job(args.job_id).get("settings", {}).get(
        "email_notifications", {}
    )
    for event in args.events:
        field = EVENT_FIELDS[event]
        if args.email not in verified.get(field, []):
            raise RuntimeError(f"Databricks did not retain {args.email} in {field}")
    print("Databricks job email notifications configured and verified.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
