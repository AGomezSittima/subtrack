from collections.abc import Sequence
from dataclasses import dataclass
from datetime import datetime
from enum import Enum

from subtrack.clock.clock import Clock
from subtrack.persistence.models import CatalogServiceRow
from subtrack.persistence.types import StoredPlan
from subtrack.repository.transaction import Transaction
from subtrack.service.errors import BuiltinNotDeletableError, InvalidFieldsError, NotFoundError


class Unset(Enum):
    UNSET = "unset"


UNSET = Unset.UNSET


@dataclass(frozen=True)
class CatalogEntry:
    id: int
    name: str
    domain: str | None
    category: str
    plans: tuple[StoredPlan, ...]
    builtin: bool
    hidden: bool
    created_at: datetime
    updated_at: datetime


@dataclass(frozen=True)
class NewCatalogEntry:
    name: str
    domain: str | None
    category: str
    plans: tuple[StoredPlan, ...]


@dataclass(frozen=True)
class CatalogChanges:
    name: str | Unset = UNSET
    domain: str | Unset | None = UNSET
    category: str | Unset = UNSET
    plans: tuple[StoredPlan, ...] | Unset = UNSET
    hidden: bool | Unset = UNSET


class CatalogService:
    def __init__(self, transaction: Transaction, clock: Clock) -> None:
        self._transaction = transaction
        self._clock = clock

    def search(self, *, q: str | None, include_hidden: bool) -> list[CatalogEntry]:
        with self._transaction() as repositories:
            rows = repositories.catalog.search(q=q, include_hidden=include_hidden)
            return [_entry(row) for row in rows]

    def create(self, new: NewCatalogEntry) -> CatalogEntry:
        _check(_repeated_plans(new.plans))
        now = self._clock.now()
        with self._transaction() as repositories:
            row = repositories.catalog.add(
                CatalogServiceRow(
                    builtin_key=None,
                    name=new.name,
                    domain=new.domain,
                    category=new.category,
                    default_plans=list(new.plans),
                    hidden=False,
                    created_at=now,
                    updated_at=now,
                )
            )
            return _entry(row)

    def update(self, service_id: int, changes: CatalogChanges) -> CatalogEntry:
        with self._transaction() as repositories:
            row = repositories.catalog.get(service_id)
            if row is None:
                raise NotFoundError
            invalid = [] if changes.plans is UNSET else _repeated_plans(changes.plans)
            if changes.hidden is True and row.builtin_key is None:
                invalid.append("/hidden")
            _check(invalid)
            if changes.name is not UNSET:
                row.name = changes.name
            if changes.domain is not UNSET:
                row.domain = changes.domain
            if changes.category is not UNSET:
                row.category = changes.category
            if changes.plans is not UNSET:
                row.default_plans = list(changes.plans)
            if changes.hidden is not UNSET:
                row.hidden = changes.hidden
            row.updated_at = self._clock.now()
            return _entry(row)

    def delete(self, service_id: int) -> None:
        with self._transaction() as repositories:
            row = repositories.catalog.get(service_id)
            if row is None:
                raise NotFoundError
            if row.builtin_key is not None:
                raise BuiltinNotDeletableError
            repositories.catalog.delete(row)


def _repeated_plans(plans: Sequence[StoredPlan]) -> list[str]:
    seen: set[tuple[str, int, str]] = set()
    repeated: list[str] = []
    for index, plan in enumerate(plans):
        key = (plan.plan, plan.interval_count, plan.interval_unit)
        if key in seen:
            repeated.append(f"/plans/{index}")
        seen.add(key)
    return repeated


def _check(pointers: list[str]) -> None:
    if pointers:
        raise InvalidFieldsError(pointers)


def _entry(row: CatalogServiceRow) -> CatalogEntry:
    return CatalogEntry(
        id=row.id,
        name=row.name,
        domain=row.domain,
        category=row.category,
        plans=tuple(row.default_plans),
        builtin=row.builtin_key is not None,
        hidden=row.hidden,
        created_at=row.created_at,
        updated_at=row.updated_at,
    )
