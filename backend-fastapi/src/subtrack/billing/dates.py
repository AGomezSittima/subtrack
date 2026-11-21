from collections.abc import Iterator
from datetime import date, timedelta

from dateutil.relativedelta import relativedelta

from subtrack.billing.errors import BillingErrorCode, BillingInputError
from subtrack.billing.interval import MONTHS_IN_A_YEAR, Interval, IntervalUnit


def billing_date(anchor: date, interval: Interval, n: int) -> date:
    if n < 0:
        raise BillingInputError(BillingErrorCode.NEGATIVE_INDEX)
    steps = n * interval.count
    match interval.unit:
        case IntervalUnit.DAY:
            return anchor + timedelta(days=steps)
        case IntervalUnit.WEEK:
            return anchor + timedelta(weeks=steps)
        case IntervalUnit.MONTH:
            return anchor + relativedelta(months=steps)
        case IntervalUnit.YEAR:
            return anchor + relativedelta(months=steps * MONTHS_IN_A_YEAR)


def billing_dates(anchor: date, interval: Interval, count: int) -> list[date]:
    return [billing_date(anchor, interval, n) for n in range(1, count + 1)]


def billing_dates_between(
    anchor: date, interval: Interval, start: date, end: date
) -> Iterator[date]:
    n = 0
    while (due := billing_date(anchor, interval, n)) <= end:
        if due >= start:
            yield due
        n += 1
