import json
from dataclasses import dataclass
from datetime import date, datetime

from sqlalchemy import Dialect, Text
from sqlalchemy.types import TypeDecorator

from subtrack.persistence.isotext import (
    format_date,
    format_timestamp,
    parse_date,
    parse_timestamp,
)


class IsoDate(TypeDecorator[date]):
    impl = Text
    cache_ok = True

    def process_bind_param(self, value: date | None, dialect: Dialect) -> str | None:
        return None if value is None else format_date(value)

    def process_result_value(self, value: str | None, dialect: Dialect) -> date | None:
        return None if value is None else parse_date(value)


class UtcTimestamp(TypeDecorator[datetime]):
    impl = Text
    cache_ok = True

    def process_bind_param(self, value: datetime | None, dialect: Dialect) -> str | None:
        return None if value is None else format_timestamp(value)

    def process_result_value(self, value: str | None, dialect: Dialect) -> datetime | None:
        return None if value is None else parse_timestamp(value)


@dataclass(frozen=True)
class StoredPlan:
    plan: str
    price_cents: int | None
    interval_count: int
    interval_unit: str


class CatalogPlans(TypeDecorator[list[StoredPlan]]):
    impl = Text
    cache_ok = True

    def process_bind_param(self, value: list[StoredPlan] | None, dialect: Dialect) -> str | None:
        if value is None:
            return None
        plans = [
            {
                "plan": plan.plan,
                "price_cents": plan.price_cents,
                "interval_count": plan.interval_count,
                "interval_unit": plan.interval_unit,
            }
            for plan in value
        ]
        return json.dumps(plans, ensure_ascii=False, separators=(",", ":"))

    def process_result_value(self, value: str | None, dialect: Dialect) -> list[StoredPlan] | None:
        if value is None:
            return None
        return [
            StoredPlan(
                plan=plan["plan"],
                price_cents=plan["price_cents"],
                interval_count=plan["interval_count"],
                interval_unit=plan["interval_unit"],
            )
            for plan in json.loads(value)
        ]
