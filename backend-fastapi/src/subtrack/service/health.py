from datetime import datetime

from subtrack.repository.setting_key import SettingKey
from subtrack.repository.transaction import Transaction


class HealthService:
    def __init__(self, transaction: Transaction) -> None:
        self._transaction = transaction

    def last_scheduler_run(self) -> datetime | None:
        with self._transaction() as repositories:
            return repositories.settings.timestamp(SettingKey.LAST_SCHEDULER_RUN)
