"""Keys of the `setting` table that the backend reads; the schema lists them all."""

from enum import StrEnum


class SettingKey(StrEnum):
    LAST_SCHEDULER_RUN = "last_scheduler_run"
    PASSCODE_HASH = "passcode_hash"
    LOCK_POLICY = "lock_policy"
