import sqlite3
from datetime import UTC, date, datetime
from pathlib import Path

import pytest

from subtrack.clock.home_zone import HomeZone
from subtrack.clock.settable import SettableClock
from subtrack.clock.system import SystemClock
from subtrack.persistence.engine import create_session_factory, create_sqlite_engine
from subtrack.repository.transaction import sql_transaction

NEAR_MIDNIGHT_UTC = datetime(2026, 11, 23, 23, 30, tzinfo=UTC)


@pytest.fixture
def clock() -> SettableClock:
    clock = SettableClock(SystemClock())
    clock.pin(NEAR_MIDNIGHT_UTC)
    return clock


@pytest.fixture
def home_zone(database: Path, clock: SettableClock) -> HomeZone:
    engine = create_sqlite_engine(database)
    return HomeZone(clock, sql_transaction(create_session_factory(engine)))


def store(db: sqlite3.Connection, name: str | None) -> None:
    db.execute("UPDATE setting SET value = ? WHERE key = 'home_time_zone'", (name,))
    db.commit()


def test_without_a_home_zone_today_is_the_date_in_utc(home_zone: HomeZone) -> None:
    home_zone.reload()

    assert home_zone.zone() is UTC
    assert home_zone.today() == date(2026, 11, 23)


@pytest.mark.parametrize(
    ("name", "today"),
    [("Europe/Madrid", date(2026, 11, 24)), ("America/New_York", date(2026, 11, 23))],
)
def test_today_is_the_date_in_the_stored_home_zone(
    home_zone: HomeZone, db: sqlite3.Connection, name: str, today: date
) -> None:
    store(db, name)
    home_zone.reload()

    assert home_zone.today() == today


def test_the_zone_is_cached_until_reloaded_or_set(
    home_zone: HomeZone, db: sqlite3.Connection
) -> None:
    home_zone.reload()
    store(db, "Europe/Madrid")

    assert home_zone.today() == date(2026, 11, 23)

    home_zone.set("Pacific/Auckland")
    assert home_zone.today() == date(2026, 11, 24)

    home_zone.set(None)
    assert home_zone.today() == date(2026, 11, 23)


def test_today_follows_the_clock_across_a_dst_change(
    home_zone: HomeZone, clock: SettableClock
) -> None:
    home_zone.set("Europe/Madrid")

    clock.pin(datetime(2026, 3, 28, 22, 59, tzinfo=UTC))
    assert home_zone.today() == date(2026, 3, 28)
    clock.pin(datetime(2026, 3, 28, 23, 0, tzinfo=UTC))
    assert home_zone.today() == date(2026, 3, 29)
    clock.pin(datetime(2026, 10, 24, 21, 59, tzinfo=UTC))
    assert home_zone.today() == date(2026, 10, 24)
    clock.pin(datetime(2026, 10, 24, 22, 0, tzinfo=UTC))
    assert home_zone.today() == date(2026, 10, 25)


def test_a_stored_zone_this_backend_does_not_know_falls_back_to_utc(
    home_zone: HomeZone, db: sqlite3.Connection
) -> None:
    store(db, "Mars/Olympus")
    home_zone.reload()

    assert home_zone.zone() is UTC
