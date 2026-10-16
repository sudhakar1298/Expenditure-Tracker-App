"""Remove the paired phone so a replacement installation can be paired."""

import argparse

from app.core.database import SessionLocal
from app.models.device_credential import DeviceCredential, DeviceNonce


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--confirm",
        action="store_true",
        help="Confirm removal of the current paired-device credential.",
    )
    args = parser.parse_args()
    if not args.confirm:
        parser.error("Pass --confirm to revoke the paired phone")

    with SessionLocal() as db:
        device_count = db.query(DeviceCredential).count()
        db.query(DeviceNonce).delete(synchronize_session=False)
        db.query(DeviceCredential).delete(synchronize_session=False)
        db.commit()
    print(f"Revoked {device_count} paired phone credential(s).")


if __name__ == "__main__":
    main()
