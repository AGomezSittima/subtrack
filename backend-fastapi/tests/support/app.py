import sqlite3
from collections.abc import Iterator
from contextlib import contextmanager
from datetime import datetime
from pathlib import Path

from fastapi import FastAPI
from fastapi.testclient import TestClient

from subtrack.api.dependencies import Container
from subtrack.clock.settable import SettableClock
from subtrack.config.settings import Settings
from subtrack.main import create_app
from tests.support.migrations import apply_migrations, connect


def migrate(path: Path) -> Path:
    connection = connect(path)
    try:
        apply_migrations(connection)
    finally:
        connection.close()
    return path


def settings(database: Path, template: Path, *, test_mode: bool = True) -> Settings:
    return Settings.model_validate(
        {
            "DB_PATH": database,
            "SUBTRACK_TEST_MODE": test_mode,
            "SUBTRACK_TEMPLATE_DB": template,
        }
    )


def container(client: TestClient) -> Container:
    app = client.app
    assert isinstance(app, FastAPI)
    value = app.state.container
    assert isinstance(value, Container)
    return value


def pin_clock(client: TestClient, instant: datetime) -> None:
    clock = container(client).clock
    assert isinstance(clock, SettableClock)
    clock.pin(instant)


def build_client(database: Path, template: Path, *, test_mode: bool = True) -> TestClient:
    return TestClient(
        create_app(settings(database, template, test_mode=test_mode)),
        raise_server_exceptions=False,
    )


@contextmanager
def database_connection(path: Path) -> Iterator[sqlite3.Connection]:
    connection = connect(path)
    try:
        yield connection
    finally:
        connection.close()
