import sqlite3
from pathlib import Path

import pytest
from sqlalchemy import text

from subtrack.persistence.engine import create_sqlite_engine
from subtrack.repository.template import SqlTemplateRepository
from tests.support.app import database_connection
from tests.support.rows import TS, fill_every_user_table, insert, rows

TABLES = [
    "catalog_service",
    "subscription",
    "price_history",
    "payment",
    "notification",
    "setting",
    "session",
]


def restore(database: Path, template: Path) -> None:
    engine = create_sqlite_engine(database)
    try:
        SqlTemplateRepository(engine, template).restore()
        with engine.connect() as connection:
            assert connection.execute(text("PRAGMA foreign_keys")).scalar_one() == 1
            databases = connection.execute(text("PRAGMA database_list")).fetchall()
            assert [name for _, name, _ in databases] == ["main"]
    finally:
        engine.dispose()


def test_every_table_returns_to_its_state_right_after_migrations(
    database: Path, template: Path, db: sqlite3.Connection
) -> None:
    fill_every_user_table(db)

    restore(database, template)

    with database_connection(template) as fresh:
        for table in TABLES:
            assert rows(db, table) == rows(fresh, table), table


def test_ids_start_again_from_one(database: Path, template: Path, db: sqlite3.Connection) -> None:
    fill_every_user_table(db)

    restore(database, template)
    fill_every_user_table(db)

    assert [row[0] for row in rows(db, "subscription")] == [1]


def test_the_template_catalogue_is_kept(
    database: Path, template: Path, db: sqlite3.Connection
) -> None:
    hulu = {
        "name": "Hulu",
        "domain": "hulu.com",
        "category": "video",
        "created_at": TS,
        "updated_at": TS,
    }
    with database_connection(template) as fresh:
        insert(fresh, "catalog_service", hulu)
        seeded = rows(fresh, "catalog_service")
    insert(db, "catalog_service", hulu | {"name": "Hulu (mine)"})

    restore(database, template)

    assert rows(db, "catalog_service") == seeded
    assert [row[2] for row in seeded].count("Hulu") == 1


def test_tables_added_by_later_migrations_are_restored_too(
    database: Path, template: Path, db: sqlite3.Connection
) -> None:
    create = "CREATE TABLE later (id INTEGER PRIMARY KEY, label TEXT NOT NULL) STRICT"
    with database_connection(template) as fresh:
        fresh.execute(create)
        fresh.execute("INSERT INTO later (label) VALUES ('seeded')")
    db.execute(create)
    db.execute("INSERT INTO later (label) VALUES ('user data')")

    restore(database, template)

    assert rows(db, "later") == [(1, "seeded")]


def test_a_failed_restore_changes_nothing(
    database: Path, tmp_path: Path, db: sqlite3.Connection
) -> None:
    broken = tmp_path / "broken.db"
    with database_connection(broken) as other:
        other.execute("CREATE TABLE subscription (id INTEGER PRIMARY KEY) STRICT")
        other.execute("INSERT INTO subscription (id) VALUES (1)")
    fill_every_user_table(db)
    before = {table: rows(db, table) for table in TABLES}

    engine = create_sqlite_engine(database)
    try:
        with pytest.raises(sqlite3.Error):
            SqlTemplateRepository(engine, broken).restore()
    finally:
        engine.dispose()

    assert {table: rows(db, table) for table in TABLES} == before
