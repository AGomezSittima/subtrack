"""The injected source of the current time."""

from abc import ABC, abstractmethod
from datetime import date, datetime
from zoneinfo import ZoneInfo

APP_ZONE = ZoneInfo("Europe/Madrid")


class Clock(ABC):
    @abstractmethod
    def now(self) -> datetime:
        """The current instant, timezone-aware in UTC."""

    def today(self) -> date:
        """The current calendar date in Europe/Madrid."""
        return self.now().astimezone(APP_ZONE).date()
