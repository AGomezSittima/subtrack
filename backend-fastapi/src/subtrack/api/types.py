from datetime import datetime
from typing import Annotated

from pydantic import Field, PlainSerializer, PlainValidator
from pydantic_core import PydanticCustomError

from subtrack.persistence.isotext import format_timestamp, parse_timestamp

FORMAT_ERROR = "subtrack_format"
NON_BLANK = r"[^\u0009-\u000d\u0020\u0085\u00a0\u1680\u2000-\u200a\u2028\u2029\u202f\u205f\u3000]"


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


type NonBlankText = Annotated[str, Field(pattern=NON_BLANK)]

ApiTimestamp = Annotated[
    datetime,
    PlainValidator(_timestamp),
    PlainSerializer(format_timestamp, return_type=str),
]
