import pytest

from subtrack.billing.payments import (
    Payment,
    PaymentSettings,
    PaymentSource,
    PaymentStatus,
    PaymentSubscription,
    PriceChange,
    SubscriptionStatus,
    plan_payments,
)
from tests.support.cases import (
    Json,
    failure_message,
    load_cases,
    parse_date,
    parse_interval,
    parse_optional_date,
    without_none,
)


def parse_subscription(value: Json) -> PaymentSubscription:
    return PaymentSubscription(
        anchor_date=parse_date(value["anchor_date"]),
        interval=parse_interval(value["interval"]),
        status=SubscriptionStatus(value["status"]),
        created_on=parse_date(value["created_on"]),
        auto_confirm=value["auto_confirm"],
        trial_end_date=parse_optional_date(value.get("trial_end_date")),
        paused_at=parse_optional_date(value.get("paused_at")),
        resumed_on=parse_optional_date(value.get("resumed_on")),
        ends_on=parse_optional_date(value.get("ends_on")),
        archived_on=parse_optional_date(value.get("archived_on")),
        unarchived_on=parse_optional_date(value.get("unarchived_on")),
    )


def parse_payment(value: Json) -> Payment:
    return Payment(
        id=int(value["ref"].removeprefix("p")),
        due_date=parse_date(value["due_date"]),
        expected_cents=value["expected_cents"],
        status=PaymentStatus(value["status"]),
        source=PaymentSource(value["source"]),
        paid_cents=value.get("paid_cents"),
        paid_date=parse_optional_date(value.get("paid_date")),
    )


def payment_json(payment: Payment) -> Json:
    return without_none(
        {
            "ref": None if payment.id is None else f"p{payment.id}",
            "due_date": payment.due_date.isoformat(),
            "expected_cents": payment.expected_cents,
            "status": payment.status.value,
            "source": payment.source.value,
            "paid_cents": payment.paid_cents,
            "paid_date": None if payment.paid_date is None else payment.paid_date.isoformat(),
        }
    )


@pytest.mark.parametrize("case", load_cases("payment-generation.json"))
def test_payment_generation(case: Json) -> None:
    given = case["input"]

    plan = plan_payments(
        parse_date(given["today"]),
        PaymentSettings(payment_horizon_days=given["settings"]["payment_horizon_days"]),
        parse_subscription(given["subscription"]),
        [
            PriceChange(
                price_cents=change["price_cents"],
                effective_from=parse_date(change["effective_from"]),
            )
            for change in given["price_history"]
        ],
        [parse_payment(payment) for payment in given["existing_payments"]],
        backfill=given["backfill"],
    )

    actual = {
        "payments": [payment_json(payment) for payment in plan.payments],
        "subscription_status": plan.status.value,
    }
    assert actual == case["expected"], failure_message(case)
