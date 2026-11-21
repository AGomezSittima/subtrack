from collections.abc import Iterable
from dataclasses import dataclass
from fractions import Fraction

from subtrack.billing.costs import floor_cents, monthly_equivalent, round_half_up
from subtrack.billing.interval import Interval

type PartKey = int | str | None


@dataclass(frozen=True, slots=True)
class AllocationItem:
    part: PartKey
    price_cents: int
    interval: Interval


@dataclass(frozen=True, slots=True)
class AllocatedPart:
    part: PartKey
    cents: int


@dataclass(frozen=True, slots=True)
class Allocation:
    total_cents: int
    parts: list[AllocatedPart]


def _key_order(part: PartKey) -> tuple[bool, int | str]:
    if part is None:
        return (True, "")
    return (False, part)


def allocate(items: Iterable[AllocationItem]) -> Allocation:
    exact: dict[PartKey, Fraction] = {}
    for item in items:
        share = monthly_equivalent(item.price_cents, item.interval)
        exact[item.part] = exact.get(item.part, Fraction(0)) + share
    ordered = sorted(exact, key=_key_order)

    total_cents = round_half_up(sum(exact.values(), Fraction(0)))
    floors = {part: floor_cents(exact[part]) for part in ordered}
    leftover = total_cents - sum(floors.values())
    by_remainder = sorted(
        range(len(ordered)), key=lambda i: (-(exact[ordered[i]] - floors[ordered[i]]), i)
    )
    for i in by_remainder[:leftover]:
        floors[ordered[i]] += 1

    return Allocation(
        total_cents=total_cents,
        parts=[AllocatedPart(part=part, cents=floors[part]) for part in ordered],
    )
