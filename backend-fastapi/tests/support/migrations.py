"""Build a test database from the Flyway migrations in `db/migrations/`.

The migrations are the only owner of the schema (docs/sdd.md, Migrations and ownership), so tests
never create tables themselves: they apply the same SQL files the `migrate` container applies.
"""

import re
import sqlite3
from pathlib import Path

MIGRATIONS_DIR = Path(__file__).resolve().parents[3] / "db" / "migrations"

_VERSIONED = re.compile(r"^V(?P<version>\d+)__\w+\.sql$")


def migration_files(directory: Path = MIGRATIONS_DIR) -> list[Path]:
    """Return the versioned migrations in the order Flyway applies them (numeric version)."""
    versioned: list[tuple[int, Path]] = []
    for path in directory.iterdir():
        match = _VERSIONED.match(path.name)
        if match is not None:
            versioned.append((int(match["version"]), path))
    versioned.sort()
    return [path for _, path in versioned]


def connect(database: Path) -> sqlite3.Connection:
    """Open a connection with the PRAGMAs every backend connection uses."""
    connection = sqlite3.connect(database, autocommit=True)
    connection.execute("PRAGMA foreign_keys = ON")
    connection.execute("PRAGMA journal_mode = WAL")
    connection.execute("PRAGMA busy_timeout = 5000")
    return connection


def apply_migrations(connection: sqlite3.Connection, directory: Path = MIGRATIONS_DIR) -> None:
    """Apply every migration in order, each in its own transaction, as Flyway does."""
    for path in migration_files(directory):
        script = path.read_text(encoding="utf-8")
        try:
            connection.executescript(f"BEGIN;\n{script}\nCOMMIT;")
        except sqlite3.Error:
            if connection.in_transaction:
                connection.execute("ROLLBACK")
            raise
