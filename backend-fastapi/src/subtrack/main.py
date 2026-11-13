"""Application factory and ASGI entry point (`uvicorn subtrack.main:app`)."""

from fastapi import FastAPI

from subtrack.api import health

API_PREFIX = "/api/v1"


def create_app() -> FastAPI:
    """Build the FastAPI app with every router mounted under /api/v1."""
    # The contract lives in contract/openapi.yaml, so the generated docs are not served.
    app = FastAPI(title="Subtrack API", openapi_url=None, docs_url=None, redoc_url=None)
    app.include_router(health.router, prefix=API_PREFIX)
    return app


app = create_app()
