"""Test-mode clock: follows another clock until pinned to an instant."""

from datetime import UTC, datetime
from threading import Lock

from subtrack.clock.clock import Clock


class SettableClock(Clock):
    def __init__(self, fallback: Clock) -> None:
        self._fallback = fallback
        self._pinned: datetime | None = None
        self._lock = Lock()

    def now(self) -> datetime:
        with self._lock:
            pinned = self._pinned
        return self._fallback.now() if pinned is None else pinned

    def pin(self, instant: datetime) -> None:
        if instant.tzinfo is None:
            msg = "the clock can only be pinned to a timezone-aware instant"
            raise ValueError(msg)
        with self._lock:
            self._pinned = instant.astimezone(UTC)

    def unpin(self) -> None:
        with self._lock:
            self._pinned = None
