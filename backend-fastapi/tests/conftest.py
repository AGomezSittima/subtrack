import sqlite3
from collections.abc import Iterator
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from tests.support.app import build_client, database_connection, migrate
from tests.support.migrations import apply_migrations, connect


@pytest.fixture
def migrated_db(tmp_path: Path) -> Iterator[sqlite3.Connection]:
    connection = connect(tmp_path / "subs.db")
    try:
        apply_migrations(connection)
        yield connection
    finally:
        connection.close()


@pytest.fixture
def database(tmp_path: Path) -> Path:
    return migrate(tmp_path / "subs.db")


@pytest.fixture
def template(tmp_path: Path) -> Path:
    return migrate(tmp_path / "template.db")


@pytest.fixture
def db(database: Path) -> Iterator[sqlite3.Connection]:
    with database_connection(database) as connection:
        yield connection


@pytest.fixture
def client(database: Path, template: Path) -> Iterator[TestClient]:
    with build_client(database, template) as test_client:
        yield test_client


@pytest.fixture
def production_client(database: Path, template: Path) -> Iterator[TestClient]:
    with build_client(database, template, test_mode=False) as test_client:
        yield test_client
