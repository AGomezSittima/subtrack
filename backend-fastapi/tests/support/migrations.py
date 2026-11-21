import re
import sqlite3
from pathlib import Path

MIGRATIONS_DIR = Path(__file__).resolve().parents[3] / "db" / "migrations"

_VERSIONED = re.compile(r"^V(?P<version>\d+)__\w+\.sql$")


def migration_files(directory: Path = MIGRATIONS_DIR) -> list[Path]:
    versioned: list[tuple[int, Path]] = []
    for path in directory.iterdir():
        match = _VERSIONED.match(path.name)
        if match is not None:
            versioned.append((int(match["version"]), path))
    versioned.sort()
    return [path for _, path in versioned]


def connect(database: Path) -> sqlite3.Connection:
    connection = sqlite3.connect(database, autocommit=True)
    connection.execute("PRAGMA foreign_keys = ON")
    connection.execute("PRAGMA journal_mode = WAL")
    connection.execute("PRAGMA busy_timeout = 5000")
    return connection


def apply_migrations(connection: sqlite3.Connection, directory: Path = MIGRATIONS_DIR) -> None:
    for path in migration_files(directory):
        script = path.read_text(encoding="utf-8")
        try:
            connection.executescript(f"BEGIN;\n{script}\nCOMMIT;")
        except sqlite3.Error:
            if connection.in_transaction:
                connection.execute("ROLLBACK")
            raise
