"""Restores every table from a freshly migrated template database (test mode only)."""

from pathlib import Path
from typing import Protocol

from sqlalchemy import Engine

_TABLES = (
    "SELECT name FROM template.sqlite_master WHERE type = 'table'"
    " AND name NOT LIKE 'sqlite\\_%' ESCAPE '\\' AND name <> 'flyway_schema_history'"
    " ORDER BY name"
)


class TemplateRepository(Protocol):
    def restore(self) -> None: ...


class SqlTemplateRepository:
    def __init__(self, engine: Engine, template_path: Path) -> None:
        self._engine = engine
        self._template_path = template_path

    def restore(self) -> None:
        """Replace every table's rows with the template's, in one transaction."""
        connection = self._engine.raw_connection()
        try:
            cursor = connection.cursor()
            # SQLite refuses ATTACH and DETACH inside a transaction.
            cursor.execute("ATTACH DATABASE ? AS template", (str(self._template_path),))
            try:
                cursor.execute("BEGIN")
                try:
                    cursor.execute("PRAGMA defer_foreign_keys = ON")
                    tables = [_quote(name) for (name,) in cursor.execute(_TABLES).fetchall()]
                    for table in tables:
                        cursor.execute(f"DELETE FROM main.{table}")  # noqa: S608
                    for table in tables:
                        cursor.execute(f"INSERT INTO main.{table} SELECT * FROM template.{table}")  # noqa: S608
                    cursor.execute("COMMIT")
                except BaseException:
                    cursor.execute("ROLLBACK")
                    raise
            finally:
                cursor.execute("DETACH DATABASE template")
        finally:
            connection.close()


def _quote(identifier: str) -> str:
    return '"' + identifier.replace('"', '""') + '"'
