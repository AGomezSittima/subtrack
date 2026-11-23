from collections.abc import Callable, Iterator
from contextlib import AbstractContextManager, contextmanager
from dataclasses import dataclass

from sqlalchemy.orm import Session, sessionmaker

from subtrack.repository.catalog import CatalogRepository, SqlCatalogRepository
from subtrack.repository.sessions import SessionRepository, SqlSessionRepository
from subtrack.repository.settings import SettingRepository, SqlSettingRepository


@dataclass(frozen=True)
class Repositories:
    settings: SettingRepository
    sessions: SessionRepository
    catalog: CatalogRepository


type Transaction = Callable[[], AbstractContextManager[Repositories]]


def sql_transaction(session_factory: sessionmaker[Session]) -> Transaction:

    @contextmanager
    def transaction() -> Iterator[Repositories]:
        with session_factory.begin() as session:
            yield Repositories(
                settings=SqlSettingRepository(session),
                sessions=SqlSessionRepository(session),
                catalog=SqlCatalogRepository(session),
            )

    return transaction
