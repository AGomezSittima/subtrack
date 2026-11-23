import sqlite3
from datetime import UTC, datetime

from fastapi.testclient import TestClient

from tests.support.app import pin_clock


def test_health_reports_fastapi_backend(client: TestClient) -> None:
    pin_clock(client, datetime(2026, 11, 23, 23, 30, tzinfo=UTC))

    response = client.get("/api/v1/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "backend": "fastapi",
        "version": "0.1.0",
        "today": "2026-11-23",
        "last_scheduler_run": None,
    }


def test_health_reads_the_last_scheduler_run_from_the_settings(
    client: TestClient, db: sqlite3.Connection
) -> None:
    db.execute(
        "UPDATE setting SET value = '2026-11-10T15:00:00.000Z' WHERE key = 'last_scheduler_run'"
    )

    assert client.get("/api/v1/health").json()["last_scheduler_run"] == "2026-11-10T15:00:00.000Z"


def test_health_answers_head_like_get_without_a_body(client: TestClient) -> None:
    response = client.head("/api/v1/health")

    assert response.status_code == 200
    assert response.content == b""


def test_routes_are_only_served_under_the_api_prefix(client: TestClient) -> None:
    assert client.get("/health").status_code == 404
