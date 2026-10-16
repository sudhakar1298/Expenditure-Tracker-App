from sqlalchemy import Column, DateTime, ForeignKey, String, Text, UniqueConstraint, func

from app.core.database import Base


class DeviceCredential(Base):
    __tablename__ = "device_credentials"

    device_id = Column(String, primary_key=True)
    public_key_der = Column(Text, nullable=False)
    label = Column(String, nullable=False, default="Expense tracker phone")
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    last_seen_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)


class DeviceNonce(Base):
    __tablename__ = "device_nonces"
    __table_args__ = (UniqueConstraint("device_id", "nonce", name="uq_device_nonce"),)

    id = Column(String, primary_key=True)
    device_id = Column(
        String,
        ForeignKey("device_credentials.device_id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )
    nonce = Column(String, nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
