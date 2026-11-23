from collections.abc import Iterable, Mapping
from datetime import datetime
from typing import Protocol

from sqlalchemy import select
from sqlalchemy.orm import Session

from subtrack.persistence.isotext import parse_timestamp
from subtrack.persistence.models import SettingRow
from subtrack.repository.setting_key import SettingKey


class SettingRepository(Protocol):
    def value(self, key: SettingKey) -> str | None: ...

    def values(self, keys: Iterable[SettingKey]) -> dict[SettingKey, str | None]: ...

    def timestamp(self, key: SettingKey) -> datetime | None: ...

    def save(self, values: Mapping[SettingKey, str | None], now: datetime) -> None: ...


class SqlSettingRepository:
    def __init__(self, session: Session) -> None:
        self._session = session

    def value(self, key: SettingKey) -> str | None:
        return self._session.scalars(
            select(SettingRow.value).where(SettingRow.key == key.value)
        ).one()

    def values(self, keys: Iterable[SettingKey]) -> dict[SettingKey, str | None]:
        wanted = {key.value: key for key in keys}
        rows = self._session.execute(
            select(SettingRow.key, SettingRow.value).where(SettingRow.key.in_(wanted))
        ).all()
        found = {wanted[key]: value for key, value in rows}
        if len(found) != len(wanted):
            msg = f"missing setting rows: {sorted(set(wanted) - {key.value for key in found})}"
            raise LookupError(msg)
        return found

    def timestamp(self, key: SettingKey) -> datetime | None:
        text = self.value(key)
        return None if text is None else parse_timestamp(text)

    def save(self, values: Mapping[SettingKey, str | None], now: datetime) -> None:
        """Write each value; only rows whose value changes get a new updated_at."""
        rows = self._session.scalars(
            select(SettingRow).where(SettingRow.key.in_([key.value for key in values]))
        ).all()
        for row in rows:
            value = values[SettingKey(row.key)]
            if row.value != value:
                row.value = value
                row.updated_at = now
