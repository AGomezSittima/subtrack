from pathlib import Path

from sqlalchemy import text

from subtrack.persistence.engine import BUSY_TIMEOUT_MS, create_sqlite_engine


def test_every_connection_enables_foreign_keys_wal_and_a_busy_timeout(database: Path) -> None:
    engine = create_sqlite_engine(database)
    try:
        for _ in range(2):
            with engine.connect() as connection:
                pragmas = [
                    connection.execute(text(f"PRAGMA {name}")).scalar_one()
                    for name in ("foreign_keys", "journal_mode", "busy_timeout")
                ]
            assert pragmas == [1, "wal", BUSY_TIMEOUT_MS]
            engine.dispose()
    finally:
        engine.dispose()


def test_the_pool_holds_a_single_connection(database: Path) -> None:
    engine = create_sqlite_engine(database)
    try:
        assert engine.pool.size() == 1  # type: ignore[attr-defined]
    finally:
        engine.dispose()
