"""Decides whether a request may pass the privacy lock."""

from datetime import timedelta

from subtrack.clock.clock import Clock
from subtrack.repository.setting_key import SettingKey
from subtrack.repository.transaction import Transaction
from subtrack.security.tokens import hash_token

_IDLE_LIMITS: dict[str, timedelta | None] = {
    "on_open": None,
    "manual": None,
    "idle_5": timedelta(minutes=5),
    "idle_15": timedelta(minutes=15),
    "idle_30": timedelta(minutes=30),
    "idle_60": timedelta(minutes=60),
}


class LockEnforcer:
    def __init__(self, transaction: Transaction, clock: Clock) -> None:
        self._transaction = transaction
        self._clock = clock

    def allows(self, session_token: str | None, *, background: bool) -> bool:
        """Open while no passcode is set; otherwise only with a live session, which it renews."""
        with self._transaction() as repositories:
            if repositories.settings.value(SettingKey.PASSCODE_HASH) is None:
                return True
            if not session_token:
                return False
            session = repositories.sessions.find_by_token_hash(hash_token(session_token))
            if session is None:
                return False
            now = self._clock.now()
            limit = _IDLE_LIMITS[repositories.settings.value(SettingKey.LOCK_POLICY) or ""]
            if limit is not None and now - session.last_activity_at > limit:
                return False
            if not background:
                repositories.sessions.touch(session.id, now)
            return True
