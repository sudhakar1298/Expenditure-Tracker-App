from sqlalchemy import Column, String, Float, Boolean, DateTime, ForeignKey, func
from sqlalchemy.orm import relationship
from app.core.database import Base
import uuid

class Expense(Base):
    __tablename__ = "expenses"

    id = Column(String, primary_key=True, default=lambda: str(uuid.uuid4()))
    user_id = Column(String, ForeignKey("users.id"), nullable=True)
    transaction_id = Column(String, unique=True, index=True, nullable=True) # UUID or client side ID
    amount = Column(Float, nullable=False)
    currency = Column(String, default="INR")
    merchant = Column(String, index=True, nullable=False)
    category = Column(String, index=True, default="Others")
    reason = Column(String, nullable=True)
    bank = Column(String, nullable=True)
    source = Column(String, nullable=False, default="SMS") # SMS, GPay, Manual, Statement
    raw_message = Column(String, nullable=True)
    is_pending = Column(Boolean, default=False)
    transaction_date = Column(DateTime(timezone=True), nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now())
    updated_at = Column(DateTime(timezone=True), onupdate=func.now())

    user = relationship("User", back_populates="expenses")
