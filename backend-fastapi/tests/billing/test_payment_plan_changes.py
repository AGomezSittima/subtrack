from dataclasses import replace
from datetime import date

from subtrack.billing.interval import Interval, IntervalUnit
from subtrack.billing.payments import (
    Payment,
    PaymentSettings,
    PaymentSubscription,
    PriceChange,
    SubscriptionStatus,
    plan_payments,
)

TODAY = date(2026, 11, 22)
SETTINGS = PaymentSettings(payment_horizon_days=90)
SUBSCRIPTION = PaymentSubscription(
    anchor_date=date(2026, 11, 22),
    interval=Interval(count=1, unit=IntervalUnit.MONTH),
    status=SubscriptionStatus.ACTIVE,
    created_on=TODAY,
    auto_confirm=False,
)
PRICES = [PriceChange(price_cents=1199, effective_from=date(2026, 11, 22))]


def saved(payments: list[Payment]) -> list[Payment]:
    return [replace(payment, id=index) for index, payment in enumerate(payments, start=1)]


def test_br20_planning_its_own_result_again_changes_nothing() -> None:
    first = plan_payments(TODAY, SETTINGS, SUBSCRIPTION, PRICES, [], backfill=False)

    second = plan_payments(
        TODAY, SETTINGS, SUBSCRIPTION, PRICES, saved(first.payments), backfill=False
    )

    assert len(first.inserts) == 3
    assert second.inserts == []
    assert second.updated_ids == frozenset()
    assert second.deleted_ids == frozenset()


def test_br9_br10_repriced_and_cut_off_payments_are_reported_by_id() -> None:
    existing = saved(
        plan_payments(TODAY, SETTINGS, SUBSCRIPTION, PRICES, [], backfill=False).payments
    )
    paused = replace(SUBSCRIPTION, status=SubscriptionStatus.PAUSED, paused_at=date(2027, 1, 1))
    raised = [*PRICES, PriceChange(price_cents=1299, effective_from=date(2026, 12, 1))]

    plan = plan_payments(TODAY, SETTINGS, paused, raised, existing, backfill=False)

    assert plan.updated_ids == frozenset({2})
    assert plan.deleted_ids == frozenset({3})
    assert plan.inserts == []
