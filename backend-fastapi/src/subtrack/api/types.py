from datetime import datetime
from typing import Annotated

from pydantic import PlainSerializer, PlainValidator
from pydantic_core import PydanticCustomError

from subtrack.persistence.isotext import format_timestamp, parse_timestamp

FORMAT_ERROR = "subtrack_format"


def _timestamp(value: object) -> datetime:
    if isinstance(value, datetime) and value.tzinfo is not None:
        return value
    if not isinstance(value, str):
        raise PydanticCustomError("string_type", "Input should be a valid string")
    try:
        return parse_timestamp(value)
    except ValueError:
        raise PydanticCustomError(
            FORMAT_ERROR, "Input should be a UTC timestamp like 2026-11-10T15:18:00.000Z"
        ) from None


ApiTimestamp = Annotated[
    datetime,
    PlainValidator(_timestamp),
    PlainSerializer(format_timestamp, return_type=str),
]
