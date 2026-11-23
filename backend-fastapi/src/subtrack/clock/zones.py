from functools import cache
from importlib.resources import files
from zoneinfo import ZoneInfo

_TZDATA = files("tzdata")


class UnknownTimeZoneError(ValueError):
    pass


@cache
def zone_names() -> frozenset[str]:
    return frozenset(_TZDATA.joinpath("zones").read_text(encoding="utf-8").split())


@cache
def load_zone(name: str) -> ZoneInfo:
    """Load an IANA zone from the pinned tzdata package, never from the OS."""
    if name not in zone_names():
        raise UnknownTimeZoneError(name)
    with _TZDATA.joinpath("zoneinfo", *name.split("/")).open("rb") as data:
        return ZoneInfo.from_file(data, key=name)
