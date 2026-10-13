from sqlalchemy import Column, String, Text, Integer, DateTime
from app.core.database import Base


class DatabricksOutbox(Base):
    __tablename__ = "databricks_outbox"
    expense_id = Column(String, primary_key=True)
    revision = Column(String, nullable=False)
    operation = Column(String, nullable=False)
    payload = Column(Text, nullable=False)
    attempts = Column(Integer, nullable=False, default=0)
    last_error = Column(String, nullable=True)
    next_attempt = Column(DateTime, nullable=True)
