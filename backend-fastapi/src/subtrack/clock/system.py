"""The real clock: the only code that reads the system time."""

from datetime import UTC, datetime

from subtrack.clock.clock import Clock


class SystemClock(Clock):
    def now(self) -> datetime:
        return datetime.now(UTC)
