import hashlib
import sqlite3
from datetime import UTC, datetime, timedelta

import pytest
from fastapi.testclient import TestClient

from subtrack.persistence.isotext import format_timestamp
from tests.support.app import pin_clock
from tests.support.problems import problem
from tests.support.rows import TS, insert, set_passcode

NOW = datetime(2026, 11, 10, 15, 18, tzinfo=UTC)
COOKIE_VALUE = "value-of-the-session-cookie"
PROTECTED = "/api/v1/admin/run-scheduler"


def add_session(client: TestClient, db: sqlite3.Connection, last_activity_at: datetime) -> None:
    client.cookies.set("subtrack_session", COOKIE_VALUE)
    insert(
        db,
        "session",
        {
            "token_hash": hashlib.sha256(COOKIE_VALUE.encode()).hexdigest(),
            "created_at": format_timestamp(last_activity_at),
            "last_activity_at": format_timestamp(last_activity_at),
        },
    )


def last_activity(db: sqlite3.Connection) -> str:
    (value,) = db.execute("SELECT last_activity_at FROM session").fetchone()
    assert isinstance(value, str)
    return value


@pytest.fixture
def locked(client: TestClient, db: sqlite3.Connection) -> TestClient:
    set_passcode(db)
    pin_clock(client, NOW)
    return client


def test_everything_is_open_while_no_passcode_is_set(client: TestClient) -> None:
    problem(client.post(PROTECTED), "not-found", 404)


@pytest.mark.parametrize(
    ("method", "path"),
    [
        ("POST", PROTECTED),
        ("GET", "/api/v1/subscriptions"),
        ("GET", "/api/v1"),
        ("DELETE", PROTECTED),
    ],
)
def test_a_passcode_locks_every_api_path_without_a_session(
    locked: TestClient, method: str, path: str
) -> None:
    problem(locked.request(method, path), "unauthorized", 401)


def test_a_locked_request_is_refused_before_its_body_is_read(locked: TestClient) -> None:
    response = locked.post(
        "/api/v1/subscriptions", content=b"{bad", headers={"content-type": "application/json"}
    )

    problem(response, "unauthorized", 401)


def test_test_mode_endpoints_are_not_behind_the_lock(locked: TestClient) -> None:
    assert locked.post("/api/v1/admin/clock", json={"now": TS}).status_code == 204
    assert locked.post("/api/v1/admin/reset").status_code == 204


@pytest.mark.parametrize(
    ("path", "status"),
    [("/api/v1/health", 200), ("/api/v1/auth/status", 404), ("/api/v1/auth/unlock", 404)],
)
def test_public_paths_stay_open(locked: TestClient, path: str, status: int) -> None:
    method = "POST" if path.endswith("unlock") else "GET"
    assert locked.request(method, path).status_code == status


def test_paths_outside_the_api_are_not_found(locked: TestClient) -> None:
    problem(locked.get("/api/v2/health"), "not-found", 404)


def test_an_unknown_session_is_refused(locked: TestClient, db: sqlite3.Connection) -> None:
    add_session(locked, db, NOW)

    locked.cookies.set("subtrack_session", "other")

    problem(locked.post(PROTECTED), "unauthorized", 401)


def test_a_valid_session_passes_and_renews_its_activity(
    locked: TestClient, db: sqlite3.Connection
) -> None:
    add_session(locked, db, NOW - timedelta(minutes=10))

    response = locked.get("/api/v1/nope")

    problem(response, "not-found", 404)
    assert last_activity(db) == "2026-11-10T15:18:00.000Z"


def test_a_background_request_passes_without_renewing_the_session(
    locked: TestClient, db: sqlite3.Connection
) -> None:
    add_session(locked, db, NOW - timedelta(minutes=10))

    response = locked.get("/api/v1/nope", headers={"X-Subtrack-Background": "1"})

    problem(response, "not-found", 404)
    assert last_activity(db) == "2026-11-10T15:08:00.000Z"


@pytest.mark.parametrize(
    ("policy", "idle", "allowed"),
    [
        ("idle_15", timedelta(minutes=15), True),
        ("idle_15", timedelta(minutes=15, milliseconds=1), False),
        ("idle_5", timedelta(minutes=5, seconds=1), False),
        ("idle_60", timedelta(minutes=59), True),
        ("manual", timedelta(days=30), True),
        ("on_open", timedelta(days=30), True),
    ],
)
def test_a_session_expires_after_the_idle_time_of_the_lock_policy(
    locked: TestClient,
    db: sqlite3.Connection,
    policy: str,
    idle: timedelta,
    allowed: bool,
) -> None:
    set_passcode(db, policy)
    add_session(locked, db, NOW - idle)

    response = locked.get("/api/v1/nope")

    assert response.status_code == (404 if allowed else 401)
