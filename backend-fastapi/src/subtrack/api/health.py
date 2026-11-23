from datetime import date
from typing import Annotated, Literal

from fastapi import APIRouter, Depends
from pydantic import BaseModel, ConfigDict

from subtrack.api.dependencies import health_service, settings
from subtrack.api.types import ApiTimestamp
from subtrack.config.settings import Settings
from subtrack.service.health import HealthService


class HealthResponse(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    status: Literal["ok"]
    backend: Literal["fastapi", "spring"]
    version: str
    today: date
    last_scheduler_run: ApiTimestamp | None


router = APIRouter(tags=["health"])


@router.api_route("/health", methods=["GET", "HEAD"])
def get_health(
    config: Annotated[Settings, Depends(settings)],
    health: Annotated[HealthService, Depends(health_service)],
) -> HealthResponse:
    return HealthResponse(
        status="ok",
        backend="fastapi",
        version=config.version,
        today=health.today(),
        last_scheduler_run=health.last_scheduler_run(),
    )
