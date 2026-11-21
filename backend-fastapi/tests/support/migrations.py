import re
import sqlite3
from pathlib import Path

MIGRATIONS_DIR = Path(__file__).resolve().parents[3] / "db" / "migrations"

_VERSIONED = re.compile(r"^V(?P<version>\d+)__\w+\.sql$")


def migration_files(
    directory: Path = MIGRATIONS_DIR, *, after: int = 0, up_to: int | None = None
) -> list[Path]:
    versioned: list[tuple[int, Path]] = []
    for path in directory.iterdir():
        match = _VERSIONED.match(path.name)
        if match is not None:
            versioned.append((int(match["version"]), path))
    versioned.sort()
    return [
        path
        for version, path in versioned
        if version > after and (up_to is None or version <= up_to)
    ]


def connect(database: Path) -> sqlite3.Connection:
    connection = sqlite3.connect(database, autocommit=True)
    connection.execute("PRAGMA foreign_keys = ON")
    connection.execute("PRAGMA journal_mode = WAL")
    connection.execute("PRAGMA busy_timeout = 5000")
    return connection


def apply_migrations(
    connection: sqlite3.Connection,
    directory: Path = MIGRATIONS_DIR,
    *,
    after: int = 0,
    up_to: int | None = None,
    foreign_keys: bool = False,
) -> None:
    (enabled,) = connection.execute("PRAGMA foreign_keys").fetchone()
    # Flyway's sqlite-jdbc connection leaves foreign keys off.
    connection.execute(f"PRAGMA foreign_keys = {'ON' if foreign_keys else 'OFF'}")
    try:
        for path in migration_files(directory, after=after, up_to=up_to):
            script = path.read_text(encoding="utf-8")
            try:
                connection.executescript(f"BEGIN;\n{script}\nCOMMIT;")
            except sqlite3.Error:
                if connection.in_transaction:
                    connection.execute("ROLLBACK")
                raise
    finally:
        connection.execute(f"PRAGMA foreign_keys = {'ON' if enabled else 'OFF'}")
