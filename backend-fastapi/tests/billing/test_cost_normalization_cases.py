import pytest

from subtrack.billing.costs import monthly_equivalent, round_half_up, yearly_equivalent
from tests.support.cases import Json, failure_message, load_cases, parse_interval


@pytest.mark.parametrize("case", load_cases("cost-normalization.json"))
def test_cost_normalization(case: Json) -> None:
    given = case["input"]
    interval = parse_interval(given["interval"])

    actual = {
        "monthly_cents": round_half_up(monthly_equivalent(given["price_cents"], interval)),
        "yearly_cents": round_half_up(yearly_equivalent(given["price_cents"], interval)),
    }

    assert actual == case["expected"], failure_message(case)
