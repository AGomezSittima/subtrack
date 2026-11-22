"""Test-mode use cases: reset the database and set the clock."""

from datetime import datetime

from subtrack.clock.settable import SettableClock
from subtrack.repository.template import TemplateRepository


class AdminService:
    def __init__(self, template: TemplateRepository, clock: SettableClock) -> None:
        self._template = template
        self._clock = clock

    def reset(self) -> None:
        self._template.restore()
        self._clock.unpin()

    def set_clock(self, now: datetime) -> None:
        self._clock.pin(now)
