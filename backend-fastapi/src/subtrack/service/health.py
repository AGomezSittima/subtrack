from datetime import date, datetime

from subtrack.clock.home_zone import HomeZone
from subtrack.repository.setting_key import SettingKey
from subtrack.repository.transaction import Transaction


class HealthService:
    def __init__(self, transaction: Transaction, home_zone: HomeZone) -> None:
        self._transaction = transaction
        self._home_zone = home_zone

    def today(self) -> date:
        return self._home_zone.today()

    def last_scheduler_run(self) -> datetime | None:
        with self._transaction() as repositories:
            return repositories.settings.timestamp(SettingKey.LAST_SCHEDULER_RUN)
