from collections.abc import Iterator
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from tests.support.app import build_client
from tests.support.problems import problem


def test_an_unknown_route_is_not_found(client: TestClient) -> None:
    for path in ["/api/v1/nope", "/api/v1", "/api/v1/health/", "/elsewhere"]:
        assert problem(client.get(path), "not-found", 404).keys() == {"type", "title", "status"}


@pytest.mark.parametrize("method", ["DELETE", "PUT", "PATCH", "OPTIONS"])
def test_a_known_path_with_another_method_is_not_allowed(client: TestClient, method: str) -> None:
    response = client.request(method, "/api/v1/health")

    problem(response, "method-not-allowed", 405)


@pytest.fixture
def failing_client(database: Path, template: Path) -> Iterator[TestClient]:
    client = build_client(database, template)

    def explode() -> None:
        msg = "secret internals"
        raise RuntimeError(msg)

    client.app.add_api_route("/explode", explode)  # type: ignore[attr-defined]
    with client:
        yield client


def test_an_unexpected_error_is_an_internal_error_without_internals(
    failing_client: TestClient,
) -> None:
    response = failing_client.get("/explode")

    assert problem(response, "internal-error", 500).keys() == {"type", "title", "status"}
    assert "secret" not in response.text
    assert "RuntimeError" not in response.text
