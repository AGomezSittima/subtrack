from dataclasses import dataclass
from enum import StrEnum

from subtrack.billing.errors import BillingErrorCode, BillingInputError

MONTHS_IN_A_YEAR = 12


class IntervalUnit(StrEnum):
    DAY = "day"
    WEEK = "week"
    MONTH = "month"
    YEAR = "year"


@dataclass(frozen=True, slots=True)
class Interval:
    count: int
    unit: IntervalUnit

    def __post_init__(self) -> None:
        if self.count < 1:
            raise BillingInputError(BillingErrorCode.INTERVAL_COUNT_NOT_POSITIVE)


def is_yearly_or_longer(interval: Interval) -> bool:
    match interval.unit:
        case IntervalUnit.YEAR:
            return True
        case IntervalUnit.MONTH:
            return interval.count >= MONTHS_IN_A_YEAR
        case IntervalUnit.DAY | IntervalUnit.WEEK:
            return False
