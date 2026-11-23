import sqlite3
from datetime import UTC, datetime
from pathlib import Path

import pytest
from sqlalchemy import select

from subtrack.persistence.engine import create_session_factory, create_sqlite_engine
from subtrack.persistence.models import CatalogServiceRow, SessionRow, SettingRow
from subtrack.persistence.types import StoredPlan

HASH = "a" * 64


def test_timestamps_are_stored_as_canonical_text(database: Path, db: sqlite3.Connection) -> None:
    engine = create_sqlite_engine(database)
    try:
        with create_session_factory(engine).begin() as session:
            session.add(
                SessionRow(
                    token_hash=HASH,
                    created_at=datetime(2026, 11, 10, 15, 18, tzinfo=UTC),
                    last_activity_at=datetime(2026, 11, 10, 15, 18, 0, 5000, tzinfo=UTC),
                )
            )
    finally:
        engine.dispose()

    assert db.execute("SELECT created_at, last_activity_at FROM session").fetchall() == [
        ("2026-11-10T15:18:00.000Z", "2026-11-10T15:18:00.005Z")
    ]


def test_timestamps_are_read_back_as_aware_utc(database: Path) -> None:
    engine = create_sqlite_engine(database)
    try:
        with create_session_factory(engine).begin() as session:
            row = session.scalars(select(SettingRow).where(SettingRow.key == "theme")).one()
        assert row.updated_at.tzinfo is UTC
    finally:
        engine.dispose()


def test_non_canonical_stored_text_fails_to_read(database: Path, db: sqlite3.Connection) -> None:
    db.execute("PRAGMA ignore_check_constraints = ON")
    db.execute("UPDATE setting SET updated_at = '2026-11-10T15:18:00Z' WHERE key = 'theme'")
    engine = create_sqlite_engine(database)
    try:
        with (
            create_session_factory(engine).begin() as session,
            pytest.raises(ValueError, match="timestamp"),
        ):
            session.scalars(select(SettingRow).where(SettingRow.key == "theme")).one()
    finally:
        engine.dispose()


def test_catalogue_plans_are_stored_as_canonical_json(
    database: Path, db: sqlite3.Connection
) -> None:
    now = datetime(2026, 11, 10, 15, 18, tzinfo=UTC)
    engine = create_sqlite_engine(database)
    try:
        with create_session_factory(engine).begin() as session:
            session.add(
                CatalogServiceRow(
                    name="Café Ñu",
                    domain=None,
                    category="other",
                    default_plans=[
                        StoredPlan("Año", 1999, 1, "year"),
                        StoredPlan("Mes", None, 1, "month"),
                    ],
                    hidden=False,
                    created_at=now,
                    updated_at=now,
                )
            )
    finally:
        engine.dispose()

    assert db.execute(
        "SELECT default_plans, hidden FROM catalog_service WHERE name = 'Café Ñu'"
    ).fetchall() == [
        (
            '[{"plan":"Año","price_cents":1999,"interval_count":1,"interval_unit":"year"},'
            '{"plan":"Mes","price_cents":null,"interval_count":1,"interval_unit":"month"}]',
            0,
        )
    ]
