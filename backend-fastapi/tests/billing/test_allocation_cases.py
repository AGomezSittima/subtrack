import pytest

from subtrack.billing.allocation import AllocationItem, allocate
from tests.support.cases import Json, failure_message, load_cases, parse_interval


@pytest.mark.parametrize("case", load_cases("allocation.json"))
def test_allocation(case: Json) -> None:
    items = [
        AllocationItem(
            part=item["part"],
            price_cents=item["price_cents"],
            interval=parse_interval(item["interval"]),
        )
        for item in case["input"]["items"]
    ]

    allocation = allocate(items)

    actual = {
        "total_cents": allocation.total_cents,
        "parts": [{"part": part.part, "cents": part.cents} for part in allocation.parts],
    }
    assert actual == case["expected"], failure_message(case)
