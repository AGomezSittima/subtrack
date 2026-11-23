import json
import sqlite3
from collections.abc import Callable
from pathlib import Path
from typing import Any

import pytest

from tests.support.migrations import (
    MIGRATIONS_DIR,
    apply_migrations,
    connect,
    migration_files,
)

TS = "2026-11-10T15:18:00.000Z"
CATALOG_FILE = MIGRATIONS_DIR.parent / "catalog" / "catalog.json"
SEED = 5

type Row = dict[str, Any]


def insert(db: sqlite3.Connection, table: str, row: Row) -> int:
    columns = ", ".join(row)
    placeholders = ", ".join("?" for _ in row)
    sql = f"INSERT INTO {table} ({columns}) VALUES ({placeholders})"  # noqa: S608
    row_id = db.execute(sql, tuple(row.values())).lastrowid
    assert row_id is not None
    return row_id


def count(db: sqlite3.Connection, table: str) -> int:
    (total,) = db.execute(f"SELECT count(*) FROM {table}").fetchone()  # noqa: S608
    assert isinstance(total, int)
    return total


def v1_catalog_row(**overrides: Any) -> Row:  # noqa: ANN401
    return {
        "name": "Spotify",
        "domain": "spotify.com",
        "category": "music",
        "default_plans": (
            '[{"plan": "Individual", "price_cents": 1199, "interval_count": 1,'
            ' "interval_unit": "month"}]'
        ),
    } | overrides


def catalog_row(**overrides: Any) -> Row:  # noqa: ANN401
    return v1_catalog_row(created_at=TS, updated_at=TS) | overrides


def builtin_row(**overrides: Any) -> Row:  # noqa: ANN401
    return catalog_row(builtin_key="test-builtin") | overrides


def subscription_row(**overrides: Any) -> Row:  # noqa: ANN401
    return {
        "name": "Spotify",
        "category": "music",
        "price_cents": 1199,
        "interval_count": 1,
        "interval_unit": "month",
        "anchor_date": "2026-01-31",
        "status": "active",
        "created_at": TS,
        "updated_at": TS,
    } | overrides


def price_row(subscription_id: int, **overrides: Any) -> Row:  # noqa: ANN401
    return {
        "subscription_id": subscription_id,
        "price_cents": 1199,
        "effective_from": "2026-01-31",
        "created_at": TS,
    } | overrides


def payment_row(subscription_id: int, **overrides: Any) -> Row:  # noqa: ANN401
    return {
        "subscription_id": subscription_id,
        "due_date": "2026-02-28",
        "expected_cents": 1199,
        "status": "expected",
        "source": "scheduled",
        "created_at": TS,
        "updated_at": TS,
    } | overrides


def paid_payment_row(subscription_id: int, **overrides: Any) -> Row:  # noqa: ANN401
    return (
        payment_row(subscription_id, status="paid", paid_cents=1199, paid_date="2026-02-28")
        | overrides
    )


def notification_row(subscription_id: int, **overrides: Any) -> Row:  # noqa: ANN401
    return {
        "type": "renewal",
        "severity": "info",
        "subscription_id": subscription_id,
        "trigger_date": "2026-02-25",
        "dedupe_key": f"renewal:{subscription_id}:2026-02-25",
        "params": '{"name": "Spotify", "date": "2026-02-28", "amount_cents": 1199}',
        "created_at": TS,
        "updated_at": TS,
    } | overrides


def session_row(**overrides: Any) -> Row:  # noqa: ANN401
    return {
        "token_hash": "a" * 64,
        "created_at": TS,
        "last_activity_at": TS,
    } | overrides


@pytest.fixture
def subscription_id(migrated_db: sqlite3.Connection) -> int:
    return insert(migrated_db, "subscription", subscription_row())


def test_migrations_are_applied_in_numeric_version_order(tmp_path: Path) -> None:
    for name in ["V10__later.sql", "V2__second.sql", "V1__first.sql", "README.md"]:
        (tmp_path / name).write_text("", encoding="utf-8")

    assert [path.name for path in migration_files(tmp_path)] == [
        "V1__first.sql",
        "V2__second.sql",
        "V10__later.sql",
    ]


def test_schema_has_the_seven_tables_all_strict(migrated_db: sqlite3.Connection) -> None:
    tables = migrated_db.execute(
        "SELECT name, strict FROM pragma_table_list WHERE schema = 'main' AND type = 'table'"
        " AND name NOT LIKE 'sqlite_%'"
    ).fetchall()

    assert dict(tables) == {
        "catalog_service": 1,
        "subscription": 1,
        "price_history": 1,
        "payment": 1,
        "notification": 1,
        "setting": 1,
        "session": 1,
    }


def test_fr_52_the_seed_installs_every_builtin_without_prices_or_hiding(
    migrated_db: sqlite3.Connection,
) -> None:
    services = json.loads(CATALOG_FILE.read_text(encoding="utf-8"))["services"]

    seeded = migrated_db.execute(
        "SELECT builtin_key, name, domain, category, default_plans, hidden FROM catalog_service"
        " ORDER BY id"
    ).fetchall()

    assert len(seeded) == len(services) == 54
    assert [row[:4] for row in seeded] == [
        (s["key"], s["name"], s["domain"], s["category"]) for s in services
    ]
    assert {row[5] for row in seeded} == {0}
    for (_, _, _, _, plans, _), service in zip(seeded, services, strict=True):
        assert json.loads(plans) == [plan | {"price_cents": None} for plan in service["plans"]]


def test_settings_start_with_the_documented_defaults(migrated_db: sqlite3.Connection) -> None:
    settings = dict(migrated_db.execute("SELECT key, value FROM setting").fetchall())

    assert settings == {
        "default_reminder_days": "3",
        "trial_reminder_days": "3",
        "yearly_reminder_days": "14",
        "ending_reminder_days": "7",
        "payment_horizon_days": "60",
        "logo_dev_token": None,
        "last_scheduler_run": None,
        "locale": None,
        "theme": "system",
        "font_size": "md",
        "reduced_motion": "system",
        "week_start": "1",
        "passcode_hash": None,
        "lock_policy": "idle_15",
        "failed_attempts": "0",
        "retry_after": None,
        "home_time_zone": None,
        "display_time_zone": "device",
    }


def test_a_valid_row_can_be_inserted_in_each_table(migrated_db: sqlite3.Connection) -> None:
    catalog_id = insert(migrated_db, "catalog_service", catalog_row())
    sub_id = insert(
        migrated_db,
        "subscription",
        subscription_row(
            catalog_service_id=catalog_id,
            plan_name="Individual",
            trial_end_date="2026-01-30",
            payment_method="Visa ··4417",
            auto_confirm=1,
            reminder_days=-1,
            website="https://spotify.com",
            cancel_url="https://spotify.com/account",
            notes="Family plan later",
            archived_at="2026-11-20T08:00:00.123Z",
            resumed_on="2026-05-01",
            unarchived_on="2026-11-19",
        ),
    )
    insert(migrated_db, "price_history", price_row(sub_id))
    payment_id = insert(migrated_db, "payment", paid_payment_row(sub_id, note="On time"))
    insert(
        migrated_db,
        "notification",
        notification_row(sub_id, payment_id=payment_id, read_at="2026-02-25T09:00:00.000Z"),
    )
    insert(migrated_db, "session", session_row())
    migrated_db.execute(
        "UPDATE setting SET value = ? WHERE key = 'last_scheduler_run'",
        ("2026-11-10T15:00:00.000Z",),
    )

    for table in ["subscription", "price_history", "payment", "notification"]:
        assert count(migrated_db, table) == 1
    assert count(migrated_db, "catalog_service") == 55
    assert count(migrated_db, "session") == 1


@pytest.mark.parametrize(
    "row",
    [
        pytest.param(subscription_row(status="trial", trial_end_date="2026-02-14"), id="trial"),
        pytest.param(subscription_row(status="paused", paused_at="2026-03-01"), id="paused"),
        pytest.param(
            subscription_row(status="cancelled", cancelled_at="2026-03-01", ends_on="2027-01-31"),
            id="cancelled",
        ),
        pytest.param(subscription_row(interval_count=14, interval_unit="day"), id="14-days"),
        pytest.param(subscription_row(interval_unit="week"), id="weekly"),
        pytest.param(subscription_row(interval_unit="year", anchor_date="2024-02-29"), id="leap"),
        pytest.param(subscription_row(price_cents=0), id="free"),
        *[
            pytest.param(subscription_row(category=category), id=f"category-{category}")
            for category in ["gaming", "news", "education"]
        ],
    ],
)
def test_valid_subscription_variants_are_accepted(
    migrated_db: sqlite3.Connection, row: Row
) -> None:
    insert(migrated_db, "subscription", row)


@pytest.mark.parametrize(
    "row",
    [
        pytest.param(builtin_row(), id="builtin"),
        pytest.param(builtin_row(hidden=1), id="hidden-builtin"),
        pytest.param(builtin_row(builtin_key="9lives"), id="key-starts-with-digit"),
        pytest.param(builtin_row(builtin_key="my-own-registrar"), id="key-with-dash"),
        pytest.param(catalog_row(domain=None), id="no-domain"),
        pytest.param(catalog_row(default_plans="[]"), id="no-plans"),
        *[
            pytest.param(catalog_row(category=category), id=f"category-{category}")
            for category in ["gaming", "news", "education"]
        ],
    ],
)
def test_valid_catalogue_variants_are_accepted(migrated_db: sqlite3.Connection, row: Row) -> None:
    insert(migrated_db, "catalog_service", row)


def test_fr_47_catalogue_names_may_repeat(migrated_db: sqlite3.Connection) -> None:
    insert(migrated_db, "catalog_service", builtin_row())
    insert(migrated_db, "catalog_service", catalog_row())

    assert migrated_db.execute(
        "SELECT builtin_key FROM catalog_service WHERE name = 'Spotify' ORDER BY id"
    ).fetchall() == [("spotify",), ("test-builtin",), (None,)]


def test_a_builtin_key_is_unique(migrated_db: sqlite3.Connection) -> None:
    insert(migrated_db, "catalog_service", builtin_row(builtin_key="my-service"))

    with pytest.raises(sqlite3.IntegrityError):
        insert(migrated_db, "catalog_service", builtin_row(builtin_key="my-service"))


def test_fr_49_only_a_builtin_can_be_hidden(migrated_db: sqlite3.Connection) -> None:
    user_id = insert(migrated_db, "catalog_service", catalog_row())

    with pytest.raises(sqlite3.IntegrityError):
        migrated_db.execute("UPDATE catalog_service SET hidden = 1 WHERE id = ?", (user_id,))


type RowFactory = Callable[[int], Row]

INVALID_ROWS: list[Any] = [
    *[
        pytest.param(
            "subscription", lambda _s, v=value: subscription_row(anchor_date=v), id=f"date-{value}"
        )
        for value in ["2026-1-31", "2026-02-30", "2026-13-01", "31/01/2026", "2026-01-31T00:00"]
    ],
    pytest.param("subscription", lambda _s: subscription_row(ends_on="2026-04-31"), id="ends_on"),
    pytest.param(
        "subscription", lambda _s: subscription_row(resumed_on="2026-06-31"), id="resumed_on"
    ),
    pytest.param(
        "subscription",
        lambda _s: subscription_row(unarchived_on="2026-11-19T00:00:00.000Z"),
        id="unarchived_on",
    ),
    pytest.param("payment", lambda s: payment_row(s, due_date="20260228"), id="due_date"),
    pytest.param("payment", lambda s: paid_payment_row(s, paid_date="2026-2-28"), id="paid_date"),
    pytest.param("price_history", lambda s: price_row(s, effective_from=""), id="effective_from"),
    pytest.param("notification", lambda s: notification_row(s, trigger_date="x"), id="trigger"),
    *[
        pytest.param(
            "subscription", lambda _s, v=value: subscription_row(created_at=v), id=f"ts-{value}"
        )
        for value in [
            "2026-11-10T15:18:00Z",
            "2026-11-10T15:18:00.000",
            "2026-11-10T15:18:00.000+01:00",
            "2026-11-10 15:18:00.000Z",
            "2026-11-10T15:18:00.000000Z",
            "2026-11-10T25:00:00.000Z",
            "2026-11-10",
            1762787880000,
        ]
    ],
    pytest.param(
        "subscription", lambda _s: subscription_row(archived_at="2026-11-20"), id="archived_at"
    ),
    pytest.param(
        "payment", lambda s: payment_row(s, updated_at="2026-11-10T15:18Z"), id="payment-updated"
    ),
    pytest.param("notification", lambda s: notification_row(s, read_at="yesterday"), id="read_at"),
    pytest.param("price_history", lambda s: price_row(s, created_at=""), id="price-created"),
    pytest.param(
        "session", lambda _s: session_row(last_activity_at="2026-11-10T15:18:00.0Z"), id="session"
    ),
    pytest.param("subscription", lambda _s: subscription_row(interval_unit="months"), id="unit"),
    pytest.param("subscription", lambda _s: subscription_row(status="expired"), id="sub-status"),
    pytest.param("subscription", lambda _s: subscription_row(category="Music"), id="category"),
    pytest.param("subscription", lambda _s: subscription_row(auto_confirm=2), id="auto_confirm"),
    pytest.param("catalog_service", lambda _s: catalog_row(category="games"), id="cat-category"),
    pytest.param("catalog_service", lambda _s: catalog_row(hidden=1), id="cat-hidden-user-entry"),
    pytest.param("catalog_service", lambda _s: builtin_row(hidden=2), id="cat-hidden-2"),
    *[
        pytest.param(
            "catalog_service",
            lambda _s, v=value: builtin_row(builtin_key=v),
            id=f"cat-key-{value!r}",
        )
        for value in ["", "Spotify", "apple music", "apple_music", "música"]
    ],
    pytest.param("catalog_service", lambda _s: catalog_row(name=None), id="cat-no-name"),
    pytest.param(
        "catalog_service", lambda _s: catalog_row(created_at="2026-11-10"), id="cat-created"
    ),
    pytest.param("catalog_service", lambda _s: v1_catalog_row(), id="cat-no-timestamps"),
    pytest.param("payment", lambda s: payment_row(s, status="pending"), id="payment-status"),
    pytest.param("payment", lambda s: payment_row(s, source="import"), id="payment-source"),
    pytest.param("notification", lambda s: notification_row(s, type="reminder"), id="type"),
    pytest.param("notification", lambda s: notification_row(s, severity="error"), id="severity"),
    pytest.param("subscription", lambda _s: subscription_row(price_cents=-1), id="price-neg"),
    pytest.param("subscription", lambda _s: subscription_row(price_cents="abc"), id="price-text"),
    pytest.param("subscription", lambda _s: subscription_row(price_cents=11.99), id="price-float"),
    pytest.param("price_history", lambda s: price_row(s, price_cents=-1), id="history-neg"),
    pytest.param("payment", lambda s: payment_row(s, expected_cents=-1), id="expected-neg"),
    pytest.param("payment", lambda s: paid_payment_row(s, paid_cents=-1), id="paid-neg"),
    pytest.param("subscription", lambda _s: subscription_row(interval_count=0), id="count-zero"),
    pytest.param("subscription", lambda _s: subscription_row(reminder_days=-2), id="reminder"),
    pytest.param("catalog_service", lambda _s: catalog_row(default_plans="{}"), id="plans-obj"),
    pytest.param("notification", lambda s: notification_row(s, params="[]"), id="params-array"),
    pytest.param("notification", lambda s: notification_row(s, params="{name"), id="params-json"),
    pytest.param("session", lambda _s: session_row(token_hash="A" * 64), id="token-uppercase"),
    pytest.param("session", lambda _s: session_row(token_hash="a" * 63), id="token-short"),
    pytest.param(
        "subscription",
        lambda _s: subscription_row(status="cancelled", cancelled_at="2026-03-01"),
        id="cancelled-no-ends_on",
    ),
    pytest.param(
        "subscription", lambda _s: subscription_row(status="paused"), id="paused-no-paused_at"
    ),
    pytest.param(
        "subscription", lambda _s: subscription_row(status="trial"), id="trial-no-trial_end_date"
    ),
    pytest.param(
        "payment", lambda s: payment_row(s, status="paid", paid_cents=1199), id="paid-no-date"
    ),
    pytest.param(
        "payment",
        lambda s: payment_row(s, paid_cents=1199, paid_date="2026-02-28"),
        id="expected-with-paid-fields",
    ),
    pytest.param("payment", lambda s: payment_row(s + 999), id="payment-unknown-subscription"),
]


@pytest.mark.parametrize(("table", "make_row"), INVALID_ROWS)
def test_invalid_values_are_rejected(
    migrated_db: sqlite3.Connection, subscription_id: int, table: str, make_row: RowFactory
) -> None:
    with pytest.raises(sqlite3.IntegrityError):
        insert(migrated_db, table, make_row(subscription_id))


def test_br_28_a_subscription_becomes_or_stays_a_trial_only_with_a_trial_end_date(
    migrated_db: sqlite3.Connection, subscription_id: int
) -> None:
    to_trial = "UPDATE subscription SET status = 'trial' WHERE id = ?"
    with pytest.raises(sqlite3.IntegrityError):
        migrated_db.execute(to_trial, (subscription_id,))

    migrated_db.execute(
        "UPDATE subscription SET status = 'trial', trial_end_date = '2026-02-14' WHERE id = ?",
        (subscription_id,),
    )
    with pytest.raises(sqlite3.IntegrityError):
        migrated_db.execute(
            "UPDATE subscription SET trial_end_date = NULL WHERE id = ?", (subscription_id,)
        )


@pytest.mark.parametrize(
    ("key", "value"),
    [
        ("theme", "blue"),
        ("theme", None),
        ("font_size", "xl"),
        ("reduced_motion", "never"),
        ("lock_policy", "idle_10"),
        ("week_start", "0"),
        ("locale", "fr"),
        ("default_reminder_days", None),
        ("default_reminder_days", "-1"),
        ("default_reminder_days", "3 days"),
        ("payment_horizon_days", "0"),
        ("payment_horizon_days", "367"),
        ("failed_attempts", ""),
        ("passcode_hash", "hunter22"),
        ("logo_dev_token", ""),
        ("last_scheduler_run", "2026-11-10T15:18:00Z"),
        ("retry_after", "2026-11-10"),
        ("home_time_zone", "device"),
        ("home_time_zone", ""),
        ("home_time_zone", "../etc/passwd"),
        ("home_time_zone", "Europe/ Madrid"),
        ("home_time_zone", "Europe//Madrid"),
        ("home_time_zone", "Europe/"),
        ("home_time_zone", "+01:00"),
        ("home_time_zone", "A" * 65),
        ("display_time_zone", None),
        ("display_time_zone", ""),
        ("display_time_zone", "Europe/Madrid "),
    ],
)
def test_invalid_setting_values_are_rejected(
    migrated_db: sqlite3.Connection, key: str, value: str | None
) -> None:
    with pytest.raises(sqlite3.IntegrityError):
        migrated_db.execute("UPDATE setting SET value = ? WHERE key = ?", (value, key))


@pytest.mark.parametrize(
    ("key", "value"),
    [
        ("home_time_zone", None),
        ("home_time_zone", "Europe/Lisbon"),
        ("home_time_zone", "America/Argentina/Buenos_Aires"),
        ("home_time_zone", "Etc/GMT+5"),
        ("home_time_zone", "America/Port-au-Prince"),
        ("display_time_zone", "device"),
        ("display_time_zone", "Etc/UTC"),
    ],
)
def test_time_zone_settings_accept_zone_names(
    migrated_db: sqlite3.Connection, key: str, value: str | None
) -> None:
    migrated_db.execute("UPDATE setting SET value = ? WHERE key = ?", (value, key))

    assert migrated_db.execute("SELECT value FROM setting WHERE key = ?", (key,)).fetchone() == (
        value,
    )


def test_unknown_and_duplicate_setting_keys_are_rejected(migrated_db: sqlite3.Connection) -> None:
    with pytest.raises(sqlite3.IntegrityError):
        insert(migrated_db, "setting", {"key": "currency", "value": "EUR", "updated_at": TS})
    with pytest.raises(sqlite3.IntegrityError):
        insert(migrated_db, "setting", {"key": "theme", "value": "dark", "updated_at": TS})


def test_fr_6_a_price_change_date_is_unique_per_subscription(
    migrated_db: sqlite3.Connection, subscription_id: int
) -> None:
    insert(migrated_db, "price_history", price_row(subscription_id))

    with pytest.raises(sqlite3.IntegrityError):
        insert(migrated_db, "price_history", price_row(subscription_id, price_cents=1299))


def test_br_17_a_notification_dedupe_key_is_unique(
    migrated_db: sqlite3.Connection, subscription_id: int
) -> None:
    insert(migrated_db, "notification", notification_row(subscription_id))

    with pytest.raises(sqlite3.IntegrityError):
        insert(migrated_db, "notification", notification_row(subscription_id))


def test_a_session_token_hash_is_unique(migrated_db: sqlite3.Connection) -> None:
    insert(migrated_db, "session", session_row())

    with pytest.raises(sqlite3.IntegrityError):
        insert(migrated_db, "session", session_row())


@pytest.mark.parametrize(
    ("first", "second"), [("scheduled", "scheduled"), ("scheduled", "backfill")]
)
def test_br_7_a_generated_payment_cannot_be_duplicated(
    migrated_db: sqlite3.Connection, subscription_id: int, first: str, second: str
) -> None:
    insert(migrated_db, "payment", payment_row(subscription_id, source=first))

    with pytest.raises(sqlite3.IntegrityError):
        insert(migrated_db, "payment", payment_row(subscription_id, source=second))


def test_br_7_insert_or_ignore_keeps_payment_generation_idempotent(
    migrated_db: sqlite3.Connection, subscription_id: int
) -> None:
    row = payment_row(subscription_id)
    sql = (
        "INSERT INTO payment (subscription_id, due_date, expected_cents, status, source,"
        " created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
        " ON CONFLICT (subscription_id, due_date) WHERE source != 'manual' DO NOTHING"
    )
    for _ in range(2):
        migrated_db.execute(sql, tuple(row.values()))

    assert count(migrated_db, "payment") == 1


@pytest.mark.parametrize(("first", "second"), [("manual", "manual"), ("scheduled", "manual")])
def test_fr_12_manual_payments_may_share_a_due_date(
    migrated_db: sqlite3.Connection, subscription_id: int, first: str, second: str
) -> None:
    insert(migrated_db, "payment", paid_payment_row(subscription_id, source=first))
    insert(migrated_db, "payment", paid_payment_row(subscription_id, source=second))

    assert count(migrated_db, "payment") == 2


def test_deleting_a_subscription_cascades_to_its_history_and_keeps_its_notifications(
    migrated_db: sqlite3.Connection, subscription_id: int
) -> None:
    other_id = insert(migrated_db, "subscription", subscription_row(name="Proton VPN"))
    insert(migrated_db, "price_history", price_row(subscription_id))
    payment_id = insert(migrated_db, "payment", payment_row(subscription_id))
    insert(migrated_db, "payment", payment_row(other_id))
    notification_id = insert(
        migrated_db, "notification", notification_row(subscription_id, payment_id=payment_id)
    )

    migrated_db.execute("DELETE FROM subscription WHERE id = ?", (subscription_id,))

    assert count(migrated_db, "price_history") == 0
    assert migrated_db.execute("SELECT subscription_id FROM payment").fetchall() == [(other_id,)]
    assert migrated_db.execute(
        "SELECT subscription_id, payment_id FROM notification WHERE id = ?", (notification_id,)
    ).fetchall() == [(None, None)]


def test_deleting_a_payment_keeps_its_notification(
    migrated_db: sqlite3.Connection, subscription_id: int
) -> None:
    payment_id = insert(migrated_db, "payment", payment_row(subscription_id))
    insert(migrated_db, "notification", notification_row(subscription_id, payment_id=payment_id))

    migrated_db.execute("DELETE FROM payment WHERE id = ?", (payment_id,))

    assert migrated_db.execute(
        "SELECT subscription_id, payment_id FROM notification"
    ).fetchall() == [(subscription_id, None)]


def test_deleting_a_catalogue_entry_keeps_its_subscriptions(
    migrated_db: sqlite3.Connection,
) -> None:
    catalog_id = insert(migrated_db, "catalog_service", catalog_row())
    sub_id = insert(migrated_db, "subscription", subscription_row(catalog_service_id=catalog_id))

    migrated_db.execute("DELETE FROM catalog_service WHERE id = ?", (catalog_id,))

    assert migrated_db.execute(
        "SELECT catalog_service_id FROM subscription WHERE id = ?", (sub_id,)
    ).fetchall() == [(None,)]


def test_v3_keeps_subscriptions_and_their_history(tmp_path: Path) -> None:
    db = connect(tmp_path / "subs.db")
    try:
        apply_migrations(db, up_to=2)
        catalog_id = insert(db, "catalog_service", v1_catalog_row())
        sub_id = insert(
            db,
            "subscription",
            subscription_row(
                catalog_service_id=catalog_id,
                status="trial",
                trial_end_date="2026-02-14",
                reminder_days=-1,
                archived_at="2026-11-20T08:00:00.123Z",
            ),
        )
        insert(db, "price_history", price_row(sub_id))
        payment_id = insert(db, "payment", paid_payment_row(sub_id))
        insert(db, "notification", notification_row(sub_id, payment_id=payment_id))
        before = db.execute("SELECT * FROM subscription").fetchall()

        apply_migrations(db, after=2, up_to=3)

        columns = [row[1] for row in db.execute("PRAGMA table_info(subscription)")]
        after = db.execute(
            f"SELECT {', '.join(c for c in columns if c not in ('resumed_on', 'unarchived_on'))}"  # noqa: S608
            " FROM subscription"
        ).fetchall()
        assert after == before
        assert db.execute("SELECT resumed_on, unarchived_on FROM subscription").fetchall() == [
            (None, None)
        ]
        assert db.execute("PRAGMA foreign_key_check").fetchall() == []
        assert db.execute(
            "SELECT 1 FROM sqlite_schema WHERE type = 'index'"
            " AND name = 'subscription_catalog_service_idx' AND tbl_name = 'subscription'"
        ).fetchall() == [(1,)]

        db.execute("DELETE FROM subscription WHERE id = ?", (sub_id,))
        assert count(db, "price_history") == 0
        assert count(db, "payment") == 0
        assert db.execute("SELECT subscription_id, payment_id FROM notification").fetchall() == [
            (None, None)
        ]
    finally:
        db.close()


def test_v3_aborts_when_foreign_keys_are_on(tmp_path: Path) -> None:
    db = connect(tmp_path / "subs.db")
    try:
        apply_migrations(db, up_to=2)
        sub_id = insert(db, "subscription", subscription_row())
        insert(db, "payment", payment_row(sub_id))

        with pytest.raises(sqlite3.IntegrityError):
            apply_migrations(db, after=2, foreign_keys=True)

        assert count(db, "payment") == 1
        assert "resumed_on" not in [row[1] for row in db.execute("PRAGMA table_info(subscription)")]
    finally:
        db.close()


def test_v4_keeps_catalogue_entries_and_subscriptions_as_user_data(tmp_path: Path) -> None:
    db = connect(tmp_path / "subs.db")
    try:
        apply_migrations(db, up_to=3)
        catalog_id = insert(db, "catalog_service", v1_catalog_row())
        sub_id = insert(
            db,
            "subscription",
            subscription_row(
                catalog_service_id=catalog_id,
                status="trial",
                trial_end_date="2026-02-14",
                resumed_on="2026-05-01",
                unarchived_on="2026-11-19",
                archived_at="2026-11-20T08:00:00.123Z",
            ),
        )
        insert(db, "price_history", price_row(sub_id))
        payment_id = insert(db, "payment", paid_payment_row(sub_id))
        insert(db, "notification", notification_row(sub_id, payment_id=payment_id))
        subscriptions_before = db.execute("SELECT * FROM subscription").fetchall()

        apply_migrations(db, after=3, up_to=4)

        assert db.execute(
            "SELECT id, builtin_key, name, domain, category, default_plans, hidden"
            " FROM catalog_service"
        ).fetchall() == [
            (
                catalog_id,
                None,
                "Spotify",
                "spotify.com",
                "music",
                v1_catalog_row()["default_plans"],
                0,
            )
        ]
        ((created_at, updated_at),) = db.execute(
            "SELECT created_at, updated_at FROM catalog_service"
        ).fetchall()
        assert created_at == updated_at
        assert db.execute("SELECT * FROM subscription").fetchall() == subscriptions_before
        assert db.execute("PRAGMA foreign_key_check").fetchall() == []
        assert db.execute(
            "SELECT 1 FROM sqlite_schema WHERE type = 'index'"
            " AND name = 'subscription_catalog_service_idx' AND tbl_name = 'subscription'"
        ).fetchall() == [(1,)]

        db.execute("DELETE FROM catalog_service WHERE id = ?", (catalog_id,))
        assert db.execute("SELECT catalog_service_id FROM subscription").fetchall() == [(None,)]
        db.execute("DELETE FROM subscription WHERE id = ?", (sub_id,))
        assert count(db, "price_history") == 0
        assert count(db, "payment") == 0
    finally:
        db.close()


def test_v4_aborts_when_foreign_keys_are_on(tmp_path: Path) -> None:
    db = connect(tmp_path / "subs.db")
    try:
        apply_migrations(db, up_to=3)
        sub_id = insert(db, "subscription", subscription_row())
        insert(db, "payment", payment_row(sub_id))

        with pytest.raises(sqlite3.IntegrityError):
            apply_migrations(db, after=3, up_to=4, foreign_keys=True)

        assert count(db, "payment") == 1
        columns = [row[1] for row in db.execute("PRAGMA table_info(catalog_service)")]
        assert "builtin_key" not in columns
    finally:
        db.close()


def test_fr_52_running_the_seed_again_keeps_every_user_change(tmp_path: Path) -> None:
    db = connect(tmp_path / "subs.db")
    try:
        apply_migrations(db, up_to=SEED - 1)
        own_id = insert(db, "catalog_service", catalog_row(name="Spotify", domain=None))
        apply_migrations(db, after=SEED - 1, up_to=SEED)
        db.execute(
            "UPDATE catalog_service SET name = 'Spotify Family', default_plans = ?"
            " WHERE builtin_key = 'spotify'",
            ('[{"plan":"Family","price_cents":1799,"interval_count":1,"interval_unit":"month"}]',),
        )
        db.execute("UPDATE catalog_service SET hidden = 1 WHERE builtin_key = 'netflix'")
        before = db.execute("SELECT * FROM catalog_service ORDER BY id").fetchall()

        apply_migrations(db, after=SEED - 1, up_to=SEED)

        assert db.execute("SELECT * FROM catalog_service ORDER BY id").fetchall() == before
        assert len(before) == 55
        assert db.execute(
            "SELECT builtin_key, name FROM catalog_service WHERE id = ?", (own_id,)
        ).fetchall() == [(None, "Spotify")]
    finally:
        db.close()
