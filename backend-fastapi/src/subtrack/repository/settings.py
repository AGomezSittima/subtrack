from datetime import datetime
from typing import Protocol

from sqlalchemy import select
from sqlalchemy.orm import Session

from subtrack.persistence.isotext import parse_timestamp
from subtrack.persistence.models import SettingRow
from subtrack.repository.setting_key import SettingKey


class SettingRepository(Protocol):
    def value(self, key: SettingKey) -> str | None: ...

    def timestamp(self, key: SettingKey) -> datetime | None: ...


class SqlSettingRepository:
    def __init__(self, session: Session) -> None:
        self._session = session

    def value(self, key: SettingKey) -> str | None:
        return self._session.scalars(
            select(SettingRow.value).where(SettingRow.key == key.value)
        ).one()

    def timestamp(self, key: SettingKey) -> datetime | None:
        text = self.value(key)
        return None if text is None else parse_timestamp(text)
