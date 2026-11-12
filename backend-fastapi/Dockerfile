# syntax=docker/dockerfile:1
FROM ghcr.io/astral-sh/uv:0.11.13 AS uv

FROM python:3.14.10-slim AS build
COPY --from=uv /uv /usr/local/bin/uv
ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=never \
    UV_PROJECT_ENVIRONMENT=/app/.venv
WORKDIR /app
COPY pyproject.toml uv.lock ./
RUN --mount=type=cache,target=/root/.cache/uv uv sync --locked --no-dev --no-install-project
COPY README.md ./
COPY src ./src
RUN --mount=type=cache,target=/root/.cache/uv uv sync --locked --no-dev --no-editable

FROM python:3.14.10-slim
# UID 10001 owns the SQLite volume; the Spring image and the migrate job use the same one.
RUN groupadd --system --gid 10001 subtrack \
    && useradd --system --uid 10001 --gid subtrack --no-create-home subtrack \
    && mkdir /data && chown subtrack:subtrack /data
COPY --from=build /app/.venv /app/.venv
ENV PATH=/app/.venv/bin:$PATH \
    PYTHONUNBUFFERED=1 \
    DB_PATH=/data/subs.db
USER subtrack
EXPOSE 8000
CMD ["uvicorn", "subtrack.main:app", "--host", "0.0.0.0", "--port", "8000", "--no-server-header"]
