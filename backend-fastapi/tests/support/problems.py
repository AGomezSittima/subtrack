from typing import Any

from httpx2 import Response

PROBLEM = "urn:subtrack:problem:"


def problem(response: Response, code: str, status: int) -> dict[str, Any]:
    assert response.status_code == status
    assert response.headers["content-type"] == "application/problem+json"
    body: dict[str, Any] = response.json()
    assert body["type"] == f"{PROBLEM}{code}"
    assert body["status"] == status
    assert isinstance(body["title"], str)
    assert set(body) <= {"type", "title", "status", "detail", "errors"}
    return body
