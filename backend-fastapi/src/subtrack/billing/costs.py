import math
from fractions import Fraction

from subtrack.billing.errors import BillingErrorCode, BillingInputError
from subtrack.billing.interval import MONTHS_IN_A_YEAR, Interval, IntervalUnit

_MONTHS_PER_UNIT = {
    IntervalUnit.DAY: Fraction(365, MONTHS_IN_A_YEAR),
    IntervalUnit.WEEK: Fraction(52, MONTHS_IN_A_YEAR),
    IntervalUnit.MONTH: Fraction(1),
    IntervalUnit.YEAR: Fraction(1, MONTHS_IN_A_YEAR),
}


def monthly_equivalent(price_cents: int, interval: Interval) -> Fraction:
    if price_cents < 0:
        raise BillingInputError(BillingErrorCode.NEGATIVE_CENTS)
    return price_cents * _MONTHS_PER_UNIT[interval.unit] / interval.count


def yearly_equivalent(price_cents: int, interval: Interval) -> Fraction:
    return monthly_equivalent(price_cents, interval) * MONTHS_IN_A_YEAR


def round_half_up(cents: Fraction) -> int:
    return math.floor(cents + Fraction(1, 2))


def floor_cents(cents: Fraction) -> int:
    return math.floor(cents)
