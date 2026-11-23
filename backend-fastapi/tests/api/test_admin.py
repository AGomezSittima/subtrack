import sqlite3
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import pytest
from fastapi.testclient import TestClient

from tests.support.app import build_client, container, database_connection
from tests.support.problems import problem
from tests.support.rows import TS, fill_every_user_table, rows, set_passcode

CLOCK = "/api/v1/admin/clock"
RESET = "/api/v1/admin/reset"


@pytest.mark.parametrize("path", [CLOCK, RESET, "/api/v1/admin/run-scheduler"])
def test_test_endpoints_are_not_found_outside_test_mode(
    production_client: TestClient, path: str
) -> None:
    problem(
        production_client.post(path, json={"now": "2026-11-10T15:18:00.000Z"}), "not-found", 404
    )


@pytest.mark.parametrize("path", [CLOCK, RESET])
def test_test_endpoints_are_not_found_outside_test_mode_even_when_locked(
    production_client: TestClient, db: sqlite3.Connection, path: str
) -> None:
    set_passcode(db)

    problem(production_client.post(path, json={"now": TS}), "not-found", 404)


def test_outside_test_mode_the_clock_is_the_system_clock(production_client: TestClient) -> None:
    assert type(container(production_client).clock).__name__ == "SystemClock"


def test_test_mode_refuses_to_start_without_a_template(database: Path, tmp_path: Path) -> None:
    with pytest.raises(RuntimeError, match="template"):
        build_client(database, tmp_path / "missing.db")


def test_setting_the_clock_pins_now(client: TestClient) -> None:
    response = client.post(CLOCK, json={"now": "2026-03-28T23:30:00.000Z"})

    assert response.status_code == 204
    assert response.content == b""
    assert container(client).clock.now() == datetime(2026, 3, 28, 23, 30, tzinfo=UTC)


def test_reset_restores_the_migrated_state_and_unpins_the_clock(
    client: TestClient, db: sqlite3.Connection, template: Path
) -> None:
    fill_every_user_table(db)
    client.post(CLOCK, json={"now": "2000-01-01T00:00:00.000Z"})

    response = client.post(RESET)

    assert response.status_code == 204
    with database_connection(template) as fresh:
        for table in ["subscription", "payment", "session", "setting", "catalog_service"]:
            assert rows(db, table) == rows(fresh, table)
    assert container(client).clock.now().year > 2000


def body_issues(client: TestClient, body: object) -> list[dict[str, Any]]:
    response = client.post(CLOCK, json=body)
    errors: list[dict[str, Any]] = problem(response, "validation-error", 422)["errors"]
    return errors


def test_a_missing_now_is_reported(client: TestClient) -> None:
    assert body_issues(client, {}) == [{"location": "body", "pointer": "/now", "code": "required"}]


@pytest.mark.parametrize(
    "now",
    [
        "2026-11-10T15:18:00Z",
        "2026-11-10T15:18:00.000+01:00",
        "2026-02-30T00:00:00.000Z",
        "2026-11-10T23:59:60.000Z",
        "0000-01-01T00:00:00.000Z",
        "",
    ],
)
def test_a_non_canonical_now_is_a_format_error(client: TestClient, now: str) -> None:
    assert body_issues(client, {"now": now}) == [
        {"location": "body", "pointer": "/now", "code": "format"}
    ]


def test_structural_errors_are_reported(client: TestClient) -> None:
    assert {"location": "body", "pointer": "/now", "code": "type"} in body_issues(
        client, {"now": 5}
    )
    assert {"location": "body", "pointer": "/extra", "code": "unknown_property"} in body_issues(
        client, {"now": "2026-11-10T15:18:00.000Z", "extra": 1}
    )
    assert {"location": "body", "pointer": "", "code": "type"} in body_issues(client, [])


def test_malformed_json_is_a_malformed_request(client: TestClient) -> None:
    response = client.post(CLOCK, content=b"{bad", headers={"content-type": "application/json"})

    problem(response, "malformed-request", 400)


@pytest.mark.parametrize(
    "headers",
    [
        {"content-type": "text/plain"},
        {"content-type": "application/x-www-form-urlencoded"},
        {"content-type": "multipart/form-data"},
        {},
    ],
)
def test_a_body_that_is_not_json_is_an_unsupported_media_type(
    client: TestClient, headers: dict[str, str]
) -> None:
    response = client.post(CLOCK, content=b'{"now": "2026-11-10T15:18:00.000Z"}', headers=headers)

    problem(response, "unsupported-media-type", 415)


def test_a_missing_body_is_a_required_error(client: TestClient) -> None:
    assert body_issues_raw(client) == [{"location": "body", "pointer": "", "code": "required"}]


def body_issues_raw(client: TestClient) -> list[dict[str, Any]]:
    errors: list[dict[str, Any]] = problem(client.post(CLOCK), "validation-error", 422)["errors"]
    return errors


def test_issues_are_listed_by_location_pointer_and_code(client: TestClient) -> None:
    issues = body_issues(client, {"zeta": 1, "alpha": 2})

    assert [issue["pointer"] for issue in issues] == ["/alpha", "/now", "/zeta"]
