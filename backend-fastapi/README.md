# Subtrack backend (FastAPI)

Python 3.14, FastAPI, Pydantic, uv. It implements `contract/openapi.yaml` under `/api/v1`, with the same behavior as `backend-spring`.

## Layout

`src/subtrack/` uses the module names shared with the Spring backend:

| Module        | Role                                                                |
| ------------- | ------------------------------------------------------------------- |
| `api`         | Routers and request/response models; HTTP ↔ service calls, no logic |
| `service`     | Use cases, one transaction each                                     |
| `billing`     | Pure date and money functions (no I/O, no framework imports)        |
| `repository`  | Data access interfaces and implementations                          |
| `persistence` | SQLAlchemy models, column types, engine setup                       |
| `scheduler`   | The daily job (plain code) and its trigger                          |
| `security`    | Privacy lock: passcode hashing, sessions, the router dependency     |
| `clock`       | The only place that reads the current time                          |
| `config`      | Typed settings from environment variables                           |
| `cli`         | The `subtrack` command (e.g. `reset-passcode`)                      |

`main.py` is the application factory (`uvicorn subtrack.main:app`).

## Commands

Run from this folder, or use `just lint` / `just test` from the root.

```sh
uv sync                    # create .venv with Python 3.14
uv run ruff check .        # lint
uv run ruff format .       # format
uv run mypy                # strict type check
uv run pytest              # unit tests
```

## Configuration

| Variable             | Default                                  | Meaning                         |
| -------------------- | ---------------------------------------- | ------------------------------- |
| `DB_PATH`            | `subs.db` (`/data/subs.db` in the image) | SQLite file                     |
| `SUBTRACK_TEST_MODE` | `false`                                  | Enables the test-only endpoints |
