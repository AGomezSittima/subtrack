from collections.abc import Sequence
from typing import Protocol

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from subtrack.persistence.models import CatalogServiceRow


class CatalogRepository(Protocol):
    def search(self, *, q: str | None, include_hidden: bool) -> Sequence[CatalogServiceRow]: ...

    def get(self, service_id: int) -> CatalogServiceRow | None: ...

    def add(self, row: CatalogServiceRow) -> CatalogServiceRow: ...

    def delete(self, row: CatalogServiceRow) -> None: ...


class SqlCatalogRepository:
    def __init__(self, session: Session) -> None:
        self._session = session

    def search(self, *, q: str | None, include_hidden: bool) -> Sequence[CatalogServiceRow]:
        statement = select(CatalogServiceRow)
        if not include_hidden:
            statement = statement.where(CatalogServiceRow.hidden.is_(False))
        if q is not None:
            statement = statement.where(
                func.instr(func.lower(CatalogServiceRow.name), func.lower(q)) > 0
            )
        statement = statement.order_by(
            CatalogServiceRow.name.collate("NOCASE"), CatalogServiceRow.id
        )
        return self._session.scalars(statement).all()

    def get(self, service_id: int) -> CatalogServiceRow | None:
        return self._session.get(CatalogServiceRow, service_id)

    def add(self, row: CatalogServiceRow) -> CatalogServiceRow:
        self._session.add(row)
        self._session.flush()
        return row

    def delete(self, row: CatalogServiceRow) -> None:
        self._session.delete(row)
        self._session.flush()
