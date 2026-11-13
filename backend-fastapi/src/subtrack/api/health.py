"""GET /health: liveness and backend identity (public endpoint)."""

from datetime import datetime
from typing import Annotated, Literal

from fastapi import APIRouter, Depends
from pydantic import BaseModel, ConfigDict

from subtrack.config.settings import Settings, get_settings


class HealthResponse(BaseModel):
    """Contract schema `Health`."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    status: Literal["ok"]
    backend: Literal["fastapi", "spring"]
    version: str
    last_scheduler_run: datetime | None


router = APIRouter(tags=["health"])


@router.get("/health")
def get_health(settings: Annotated[Settings, Depends(get_settings)]) -> HealthResponse:
    """Report that this backend is up and which implementation it is."""
    return HealthResponse(
        status="ok",
        backend="fastapi",
        version=settings.version,
        last_scheduler_run=None,
    )
