"""Application factory (`uvicorn --factory subtrack.main:create_app`)."""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from subtrack.api.dependencies import build_container
from subtrack.api.errors import install_error_handlers
from subtrack.api.router import API_PREFIX, api_router
from subtrack.config.settings import Settings
from subtrack.security.middleware import LockMiddleware


def create_app(settings: Settings | None = None) -> FastAPI:
    """Build the app with every router under /api/v1 behind the privacy lock."""
    config = settings or Settings()
    container = build_container(config)

    @asynccontextmanager
    async def lifespan(_: FastAPI) -> AsyncIterator[None]:
        yield
        container.engine.dispose()

    # The contract lives in contract/openapi.yaml, so the generated docs are not served.
    app = FastAPI(
        title="Subtrack API",
        openapi_url=None,
        docs_url=None,
        redoc_url=None,
        redirect_slashes=False,
        lifespan=lifespan,
    )
    app.state.container = container
    install_error_handlers(app)
    app.add_middleware(
        LockMiddleware, enforcer=container.lock, prefix=API_PREFIX, test_mode=config.test_mode
    )
    app.include_router(api_router(test_mode=config.test_mode))
    return app
