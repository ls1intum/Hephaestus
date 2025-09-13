"""Database configuration and utilities."""

from typing import Optional
from sqlalchemy import create_engine, Engine
from sqlalchemy.orm import sessionmaker, Session
from app.settings import settings


def create_database_engine() -> Engine:
    """Create a SQLAlchemy engine for database connections."""

    engine = create_engine(
        settings.DATABASE_CONNECTION_STRING,
        pool_size=5,
        max_overflow=10,
        pool_pre_ping=True,
        echo=False,  # Set to True for SQL logging during development
    )

    return engine


def get_session_factory(engine: Optional[Engine] = None) -> sessionmaker:
    """Create a session factory for database operations."""
    if engine is None:
        engine = create_database_engine()

    return sessionmaker(bind=engine, expire_on_commit=False)


# Global instances (initialized when needed)
_engine: Optional[Engine] = None
_session_factory: Optional[sessionmaker] = None


def get_engine() -> Engine:
    """Get the global database engine instance."""
    global _engine
    if _engine is None:
        _engine = create_database_engine()
    return _engine


def get_session() -> Session:
    """Get a new database session."""
    global _session_factory
    if _session_factory is None:
        _session_factory = get_session_factory()
    return _session_factory()


# Context manager for database sessions
class DatabaseSession:
    """Context manager for database sessions with automatic cleanup."""

    def __enter__(self) -> Session:
        self.session = get_session()
        return self.session

    def __exit__(self, exc_type, exc_val, exc_tb):
        if exc_type is not None:
            self.session.rollback()
        else:
            self.session.commit()
        self.session.close()


# Convenience function for database operations
def with_database_session(func):
    """Decorator that provides a database session to the decorated function."""

    def wrapper(*args, **kwargs):
        with DatabaseSession() as session:
            return func(session, *args, **kwargs)

    return wrapper
