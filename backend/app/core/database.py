from sqlalchemy import create_engine
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker
from app.core.config import settings

def _build_engine():
    """Build a usable engine, not merely an engine object.

    create_engine() is lazy for PostgreSQL, so construction alone does not
    prove that the database is reachable. Validate a real connection before
    accepting API writes, then use the local SQLite store for development.
    """
    if settings.USE_SQLITE_FALLBACK and "sqlite" not in settings.DATABASE_URL.lower():
        try:
            candidate = create_engine(settings.DATABASE_URL, pool_pre_ping=True)
            with candidate.connect():
                return candidate
        except Exception:
            pass

    if "sqlite" in settings.DATABASE_URL.lower():
        return create_engine(settings.DATABASE_URL, connect_args={"check_same_thread": False})

    if not settings.USE_SQLITE_FALLBACK:
        return create_engine(settings.DATABASE_URL, pool_pre_ping=True)

    return create_engine(settings.SQLITE_URL, connect_args={"check_same_thread": False})


engine = _build_engine()

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
