import re
from contextlib import suppress
from datetime import UTC, date, datetime

_DATE = re.compile(r"[0-9]{4}-[0-9]{2}-[0-9]{2}")
_TIMESTAMP = re.compile(r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}Z")


def format_date(value: date) -> str:
    if isinstance(value, datetime):
        msg = "a datetime is not a calendar date"
        raise TypeError(msg)
    return value.isoformat()


def parse_date(text: str) -> date:
    if _DATE.fullmatch(text) is not None:
        with suppress(ValueError):
            return date.fromisoformat(text)
    msg = f"not a YYYY-MM-DD date: {text!r}"
    raise ValueError(msg)


def format_timestamp(value: datetime) -> str:
    if value.tzinfo is None:
        msg = "a timestamp must be timezone-aware"
        raise ValueError(msg)
    utc = value.astimezone(UTC).replace(tzinfo=None)
    return f"{utc.isoformat(timespec='milliseconds')}Z"


def parse_timestamp(text: str) -> datetime:
    if _TIMESTAMP.fullmatch(text) is not None:
        with suppress(ValueError):
            return datetime.fromisoformat(text).astimezone(UTC)
    msg = f"not a UTC timestamp with milliseconds: {text!r}"
    raise ValueError(msg)
