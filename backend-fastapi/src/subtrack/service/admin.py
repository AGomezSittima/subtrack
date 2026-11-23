from datetime import datetime

from subtrack.clock.home_zone import HomeZone
from subtrack.clock.settable import SettableClock
from subtrack.repository.template import TemplateRepository


class AdminService:
    def __init__(
        self, template: TemplateRepository, clock: SettableClock, home_zone: HomeZone
    ) -> None:
        self._template = template
        self._clock = clock
        self._home_zone = home_zone

    def reset(self) -> None:
        self._template.restore()
        self._clock.unpin()
        self._home_zone.reload()

    def set_clock(self, now: datetime) -> None:
        self._clock.pin(now)
