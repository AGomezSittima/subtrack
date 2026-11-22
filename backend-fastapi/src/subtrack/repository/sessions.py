from datetime import datetime
from typing import Protocol

from sqlalchemy import select, update
from sqlalchemy.orm import Session

from subtrack.persistence.models import SessionRow


class SessionRepository(Protocol):
    def find_by_token_hash(self, token_hash: str) -> SessionRow | None: ...

    def touch(self, session_id: int, at: datetime) -> None: ...


class SqlSessionRepository:
    def __init__(self, session: Session) -> None:
        self._session = session

    def find_by_token_hash(self, token_hash: str) -> SessionRow | None:
        return self._session.scalars(
            select(SessionRow).where(SessionRow.token_hash == token_hash)
        ).one_or_none()

    def touch(self, session_id: int, at: datetime) -> None:
        self._session.execute(
            update(SessionRow).where(SessionRow.id == session_id).values(last_activity_at=at)
        )
