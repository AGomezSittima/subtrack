from abc import ABC, abstractmethod
from datetime import date, datetime
from zoneinfo import ZoneInfo

APP_ZONE = ZoneInfo("Europe/Madrid")


class Clock(ABC):
    @abstractmethod
    def now(self) -> datetime:
        pass

    def today(self) -> date:
        return self.now().astimezone(APP_ZONE).date()
