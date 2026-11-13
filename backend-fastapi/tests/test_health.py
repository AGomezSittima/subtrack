from fastapi.testclient import TestClient

from subtrack.main import create_app


def test_health_reports_fastapi_backend() -> None:
    client = TestClient(create_app())

    response = client.get("/api/v1/health")

    assert response.status_code == 200
    assert response.json() == {
        "status": "ok",
        "backend": "fastapi",
        "version": "0.1.0",
        "last_scheduler_run": None,
    }
