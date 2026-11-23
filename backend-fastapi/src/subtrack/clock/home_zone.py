import logging
from datetime import UTC, date, tzinfo
from threading import Lock

from subtrack.clock.clock import Clock
from subtrack.clock.zones import UnknownTimeZoneError, load_zone
from subtrack.repository.setting_key import SettingKey
from subtrack.repository.transaction import Transaction

logger = logging.getLogger(__name__)


class HomeZone:
    """The home time zone, cached so that reading today never hits the database."""

    def __init__(self, clock: Clock, transaction: Transaction) -> None:
        self._clock = clock
        self._transaction = transaction
        self._zone: tzinfo = UTC
        self._lock = Lock()

    def zone(self) -> tzinfo:
        with self._lock:
            return self._zone

    def today(self) -> date:
        return self._clock.now().astimezone(self.zone()).date()

    def set(self, name: str | None) -> None:
        zone = UTC if name is None else load_zone(name)
        with self._lock:
            self._zone = zone

    def reload(self) -> None:
        with self._transaction() as repositories:
            name = repositories.settings.value(SettingKey.HOME_TIME_ZONE)
        try:
            self.set(name)
        except UnknownTimeZoneError:
            logger.warning(
                "Home time zone %r is not in this backend's zone database; using UTC", name
            )
            self.set(None)
