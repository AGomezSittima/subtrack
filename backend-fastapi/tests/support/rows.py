import sqlite3
from typing import Any

TS = "2026-11-10T15:18:00.000Z"

type Row = dict[str, Any]


def insert(db: sqlite3.Connection, table: str, row: Row) -> int:
    columns = ", ".join(row)
    placeholders = ", ".join("?" for _ in row)
    sql = f"INSERT INTO {table} ({columns}) VALUES ({placeholders})"  # noqa: S608
    row_id = db.execute(sql, tuple(row.values())).lastrowid
    assert row_id is not None
    return row_id


def rows(db: sqlite3.Connection, table: str) -> list[tuple[Any, ...]]:
    return db.execute(f"SELECT * FROM {table} ORDER BY id").fetchall()  # noqa: S608


def fill_every_user_table(db: sqlite3.Connection) -> None:
    catalog_id = insert(
        db,
        "catalog_service",
        {"name": "Spotify", "domain": "spotify.com", "category": "music"},
    )
    subscription_id = insert(
        db,
        "subscription",
        {
            "catalog_service_id": catalog_id,
            "name": "Spotify",
            "category": "music",
            "price_cents": 1199,
            "interval_count": 1,
            "interval_unit": "month",
            "anchor_date": "2026-01-31",
            "status": "active",
            "created_at": TS,
            "updated_at": TS,
        },
    )
    insert(
        db,
        "price_history",
        {
            "subscription_id": subscription_id,
            "price_cents": 1199,
            "effective_from": "2026-01-31",
            "created_at": TS,
        },
    )
    payment_id = insert(
        db,
        "payment",
        {
            "subscription_id": subscription_id,
            "due_date": "2026-02-28",
            "expected_cents": 1199,
            "status": "expected",
            "source": "scheduled",
            "created_at": TS,
            "updated_at": TS,
        },
    )
    insert(
        db,
        "notification",
        {
            "type": "renewal",
            "severity": "info",
            "subscription_id": subscription_id,
            "payment_id": payment_id,
            "trigger_date": "2026-02-25",
            "dedupe_key": f"renewal:{subscription_id}:2026-02-25",
            "params": "{}",
            "created_at": TS,
            "updated_at": TS,
        },
    )
    insert(db, "session", {"token_hash": "a" * 64, "created_at": TS, "last_activity_at": TS})
    db.execute("UPDATE setting SET value = 'dark', updated_at = ? WHERE key = 'theme'", (TS,))


PASSCODE_HASH = "$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA"


def set_passcode(db: sqlite3.Connection, lock_policy: str = "idle_15") -> None:
    db.execute("UPDATE setting SET value = ? WHERE key = 'passcode_hash'", (PASSCODE_HASH,))
    db.execute("UPDATE setting SET value = ? WHERE key = 'lock_policy'", (lock_policy,))
