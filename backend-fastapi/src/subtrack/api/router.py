from fastapi import APIRouter

from subtrack.api import admin, health

API_PREFIX = "/api/v1"


def api_router(*, test_mode: bool) -> APIRouter:
    router = APIRouter(prefix=API_PREFIX)
    router.include_router(health.router)
    if test_mode:
        router.include_router(admin.router)
    return router
