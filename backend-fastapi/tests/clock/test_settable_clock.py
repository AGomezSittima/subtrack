from datetime import UTC, datetime, timedelta, timezone

import pytest

from subtrack.clock.clock import Clock
from subtrack.clock.settable import SettableClock

FALLBACK_NOW = datetime(2026, 1, 15, 10, 0, tzinfo=UTC)


class FixedClock(Clock):
    def now(self) -> datetime:
        return FALLBACK_NOW


def test_follows_its_fallback_until_pinned() -> None:
    clock = SettableClock(FixedClock())

    assert clock.now() == FALLBACK_NOW


def test_a_pinned_instant_stays_until_pinned_again_or_unpinned() -> None:
    clock = SettableClock(FixedClock())
    pinned = datetime(2026, 11, 10, 16, 18, tzinfo=timezone(timedelta(hours=1)))

    clock.pin(pinned)
    assert clock.now() == pinned
    assert clock.now().tzinfo is UTC

    clock.unpin()
    assert clock.now() == FALLBACK_NOW


def test_a_naive_instant_is_refused() -> None:
    with pytest.raises(ValueError, match="timezone-aware"):
        SettableClock(FixedClock()).pin(datetime(2026, 1, 1))  # noqa: DTZ001
