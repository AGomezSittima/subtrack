"""GET /health: liveness and backend identity (public endpoint)."""

from typing import Annotated, Literal

from fastapi import APIRouter, Depends
from pydantic import BaseModel, ConfigDict

from subtrack.api.dependencies import health_service, settings
from subtrack.api.types import ApiTimestamp
from subtrack.config.settings import Settings
from subtrack.service.health import HealthService


class HealthResponse(BaseModel):
    """Contract schema `Health`."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    status: Literal["ok"]
    backend: Literal["fastapi", "spring"]
    version: str
    last_scheduler_run: ApiTimestamp | None


router = APIRouter(tags=["health"])


@router.api_route("/health", methods=["GET", "HEAD"])
def get_health(
    config: Annotated[Settings, Depends(settings)],
    health: Annotated[HealthService, Depends(health_service)],
) -> HealthResponse:
    """Report that this backend is up and which implementation it is."""
    return HealthResponse(
        status="ok",
        backend="fastapi",
        version=config.version,
        last_scheduler_run=health.last_scheduler_run(),
    )
