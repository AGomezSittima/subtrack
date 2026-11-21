import sqlite3
from collections.abc import Iterator
from pathlib import Path

import pytest

from tests.support.migrations import apply_migrations, connect


@pytest.fixture
def migrated_db(tmp_path: Path) -> Iterator[sqlite3.Connection]:
    connection = connect(tmp_path / "subs.db")
    try:
        apply_migrations(connection)
        yield connection
    finally:
        connection.close()
