"""The only place that opens database connections."""

import sqlite3
from pathlib import Path

from sqlalchemy import Connection, Engine, create_engine, event
from sqlalchemy.orm import Session, sessionmaker
from sqlalchemy.pool import ConnectionPoolEntry, QueuePool

BUSY_TIMEOUT_MS = 5000


def create_sqlite_engine(db_path: Path) -> Engine:
    """Engine with one pooled connection: SQLite has a single writer."""
    engine = create_engine(
        f"sqlite+pysqlite:///{db_path}",
        poolclass=QueuePool,
        pool_size=1,
        max_overflow=0,
        connect_args={"check_same_thread": False},
    )
    event.listen(engine, "connect", _configure_connection)
    event.listen(engine, "begin", _begin)
    return engine


def create_session_factory(engine: Engine) -> sessionmaker[Session]:
    return sessionmaker(engine, expire_on_commit=False)


def _configure_connection(dbapi_connection: sqlite3.Connection, _: ConnectionPoolEntry) -> None:
    # The sqlite3 module opens transactions implicitly; SQLAlchemy's "begin" event does it instead.
    dbapi_connection.isolation_level = None
    dbapi_connection.execute("PRAGMA foreign_keys = ON")
    dbapi_connection.execute("PRAGMA journal_mode = WAL")
    dbapi_connection.execute(f"PRAGMA busy_timeout = {BUSY_TIMEOUT_MS}")


def _begin(connection: Connection) -> None:
    connection.exec_driver_sql("BEGIN")
