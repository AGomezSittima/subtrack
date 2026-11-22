"""Column types that store dates and timestamps as canonical ISO text."""

from datetime import date, datetime

from sqlalchemy import Dialect, Text
from sqlalchemy.types import TypeDecorator

from subtrack.persistence.isotext import (
    format_date,
    format_timestamp,
    parse_date,
    parse_timestamp,
)


class IsoDate(TypeDecorator[date]):
    """A calendar date stored as `YYYY-MM-DD`."""

    impl = Text
    cache_ok = True

    def process_bind_param(self, value: date | None, dialect: Dialect) -> str | None:
        return None if value is None else format_date(value)

    def process_result_value(self, value: str | None, dialect: Dialect) -> date | None:
        return None if value is None else parse_date(value)


class UtcTimestamp(TypeDecorator[datetime]):
    """An instant stored as UTC text with milliseconds and `Z`."""

    impl = Text
    cache_ok = True

    def process_bind_param(self, value: datetime | None, dialect: Dialect) -> str | None:
        return None if value is None else format_timestamp(value)

    def process_result_value(self, value: str | None, dialect: Dialect) -> datetime | None:
        return None if value is None else parse_timestamp(value)
