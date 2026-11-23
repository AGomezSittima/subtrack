import zoneinfo
from datetime import datetime, timedelta

import pytest

from subtrack.clock.zones import UnknownTimeZoneError, load_zone, zone_names


@pytest.mark.parametrize(
    "name", ["Europe/Lisbon", "America/Argentina/Buenos_Aires", "Etc/UTC", "Etc/GMT+5", "UTC"]
)
def test_iana_names_load_with_their_own_key(name: str) -> None:
    assert load_zone(name).key == name


@pytest.mark.parametrize(
    "name", ["device", "+01:00", "GMT+1", "UTC+1", "europe/madrid", "Mars/Olympus", "../etc"]
)
def test_offsets_and_unknown_names_are_not_zones(name: str) -> None:
    assert name not in zone_names()
    with pytest.raises(UnknownTimeZoneError):
        load_zone(name)


def test_zones_come_from_the_tzdata_package_even_without_an_os_database() -> None:
    zoneinfo.reset_tzpath(to=())
    load_zone.cache_clear()
    try:
        madrid = load_zone("Europe/Madrid")
        assert datetime(2026, 7, 1, tzinfo=madrid).utcoffset() == timedelta(hours=2)
        assert datetime(2026, 1, 1, tzinfo=madrid).utcoffset() == timedelta(hours=1)
    finally:
        zoneinfo.reset_tzpath()
        load_zone.cache_clear()
