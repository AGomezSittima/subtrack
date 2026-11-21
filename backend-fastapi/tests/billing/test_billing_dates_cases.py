import pytest

from subtrack.billing.dates import billing_dates
from tests.support.cases import Json, failure_message, load_cases, parse_date, parse_interval


@pytest.mark.parametrize("case", load_cases("billing-dates.json"))
def test_billing_dates(case: Json) -> None:
    given = case["input"]

    dates = billing_dates(
        parse_date(given["anchor"]), parse_interval(given["interval"]), given["count"]
    )

    assert {"dates": [day.isoformat() for day in dates]} == case["expected"], failure_message(case)
