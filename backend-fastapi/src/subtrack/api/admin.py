from typing import Annotated

from fastapi import APIRouter, Depends, Response, status
from pydantic import BaseModel, ConfigDict

from subtrack.api.dependencies import admin_service
from subtrack.api.types import ApiTimestamp
from subtrack.service.admin import AdminService


class ClockSetRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True, frozen=True)

    now: ApiTimestamp


router = APIRouter(prefix="/admin", tags=["admin"])


@router.post("/clock", status_code=status.HTTP_204_NO_CONTENT, response_class=Response)
def set_clock(
    body: ClockSetRequest, admin: Annotated[AdminService, Depends(admin_service)]
) -> Response:
    admin.set_clock(body.now)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/reset", status_code=status.HTTP_204_NO_CONTENT, response_class=Response)
def reset(admin: Annotated[AdminService, Depends(admin_service)]) -> Response:
    admin.reset()
    return Response(status_code=status.HTTP_204_NO_CONTENT)
