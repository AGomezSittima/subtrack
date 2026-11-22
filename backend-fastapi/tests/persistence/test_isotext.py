from datetime import UTC, date, datetime, timedelta, timezone

import pytest

from subtrack.persistence.isotext import (
    format_date,
    format_timestamp,
    parse_date,
    parse_timestamp,
)


def test_dates_are_written_as_year_month_day() -> None:
    assert format_date(date(2026, 12, 1)) == "2026-12-01"
    assert format_date(date(999, 1, 9)) == "0999-01-09"


def test_a_datetime_is_not_written_as_a_date() -> None:
    with pytest.raises(TypeError):
        format_date(datetime(2026, 12, 1, tzinfo=UTC))


@pytest.mark.parametrize(
    "text", ["2026-1-31", "2026-02-30", "2026-13-01", "31/01/2026", "2026-01-31T00:00", ""]
)
def test_non_canonical_dates_are_rejected(text: str) -> None:
    with pytest.raises(ValueError, match="date"):
        parse_date(text)


def test_dates_round_trip() -> None:
    assert parse_date("2024-02-29") == date(2024, 2, 29)


def test_timestamps_are_written_in_utc_with_milliseconds_and_z() -> None:
    madrid_summer = timezone(timedelta(hours=2))

    assert format_timestamp(datetime(2026, 11, 10, 15, 18, tzinfo=UTC)) == (
        "2026-11-10T15:18:00.000Z"
    )
    assert format_timestamp(datetime(2026, 7, 1, 2, 0, 0, 123999, tzinfo=madrid_summer)) == (
        "2026-07-01T00:00:00.123Z"
    )


def test_naive_timestamps_are_refused() -> None:
    with pytest.raises(ValueError, match="aware"):
        format_timestamp(datetime(2026, 11, 10, 15, 18))  # noqa: DTZ001


@pytest.mark.parametrize(
    "text",
    [
        "2026-11-10T15:18:00Z",
        "2026-11-10T15:18:00.000",
        "2026-11-10T15:18:00.000+01:00",
        "2026-11-10 15:18:00.000Z",
        "2026-11-10T15:18:00.000000Z",
        "2026-11-10T25:00:00.000Z",
        "2026-11-10T23:59:60.000Z",
        "0000-01-01T00:00:00.000Z",
        "2026-02-30T00:00:00.000Z",
        "2026-11-10",
    ],
)
def test_non_canonical_timestamps_are_rejected(text: str) -> None:
    with pytest.raises(ValueError, match="timestamp"):
        parse_timestamp(text)


def test_timestamps_round_trip_as_aware_utc() -> None:
    parsed = parse_timestamp("2026-11-10T15:18:00.123Z")

    assert parsed == datetime(2026, 11, 10, 15, 18, 0, 123000, tzinfo=UTC)
    assert parsed.tzinfo is UTC
    assert format_timestamp(parsed) == "2026-11-10T15:18:00.123Z"
