from collections.abc import Sequence
from dataclasses import dataclass, replace
from datetime import date, timedelta
from enum import StrEnum

from subtrack.billing.dates import billing_dates_between
from subtrack.billing.errors import BillingErrorCode, BillingInputError
from subtrack.billing.interval import Interval


class SubscriptionStatus(StrEnum):
    TRIAL = "trial"
    ACTIVE = "active"
    PAUSED = "paused"
    CANCELLED = "cancelled"


class PaymentStatus(StrEnum):
    EXPECTED = "expected"
    PAID = "paid"
    SKIPPED = "skipped"
    FAILED = "failed"


class PaymentSource(StrEnum):
    SCHEDULED = "scheduled"
    BACKFILL = "backfill"
    MANUAL = "manual"


@dataclass(frozen=True, slots=True)
class PaymentSettings:
    payment_horizon_days: int


@dataclass(frozen=True, slots=True)
class PriceChange:
    price_cents: int
    effective_from: date

    def __post_init__(self) -> None:
        if self.price_cents < 0:
            raise BillingInputError(BillingErrorCode.NEGATIVE_CENTS)


@dataclass(frozen=True, slots=True, kw_only=True)
class PaymentSubscription:
    anchor_date: date
    interval: Interval
    status: SubscriptionStatus
    created_on: date
    auto_confirm: bool
    trial_end_date: date | None = None
    paused_at: date | None = None
    resumed_on: date | None = None
    ends_on: date | None = None
    archived_on: date | None = None
    unarchived_on: date | None = None

    def __post_init__(self) -> None:
        if self.status is SubscriptionStatus.TRIAL and self.trial_end_date is None:
            raise BillingInputError(BillingErrorCode.TRIAL_END_DATE_REQUIRED)
        if self.status is SubscriptionStatus.PAUSED and self.paused_at is None:
            raise BillingInputError(BillingErrorCode.PAUSED_AT_REQUIRED)
        if self.status is SubscriptionStatus.CANCELLED and self.ends_on is None:
            raise BillingInputError(BillingErrorCode.ENDS_ON_REQUIRED)


@dataclass(frozen=True, slots=True, kw_only=True)
class Payment:
    id: int | None = None
    due_date: date
    expected_cents: int
    status: PaymentStatus
    source: PaymentSource
    paid_cents: int | None = None
    paid_date: date | None = None

    @property
    def is_scheduled_expected(self) -> bool:
        return self.source is PaymentSource.SCHEDULED and self.status is PaymentStatus.EXPECTED


@dataclass(frozen=True, slots=True)
class PaymentPlan:
    status: SubscriptionStatus
    payments: list[Payment]
    updated_ids: frozenset[int]
    deleted_ids: frozenset[int]

    @property
    def inserts(self) -> list[Payment]:
        return [payment for payment in self.payments if payment.id is None]


def price_in_force(price_history: Sequence[PriceChange], on: date) -> int:
    in_force = [change for change in price_history if change.effective_from <= on]
    if not in_force:
        raise BillingInputError(BillingErrorCode.PRICE_NOT_IN_FORCE)
    return max(in_force, key=lambda change: change.effective_from).price_cents


def plan_payments(
    today: date,
    settings: PaymentSettings,
    subscription: PaymentSubscription,
    price_history: Sequence[PriceChange],
    payments: Sequence[Payment],
    *,
    backfill: bool,
) -> PaymentPlan:
    if not price_history:
        raise BillingInputError(BillingErrorCode.PRICE_HISTORY_EMPTY)

    status = _next_status(today, subscription)
    kept = [payment for payment in payments if not _is_cut_off(subscription, status, payment)]
    planned = [_repriced(payment, price_history) for payment in kept]
    if backfill:
        planned += _backfilled(subscription, price_history, planned)
    planned += _generated(today, settings, subscription, status, price_history, planned)
    if subscription.auto_confirm and subscription.archived_on is None:
        planned = [_auto_confirmed(today, payment) for payment in planned]

    originals = {payment.id: payment for payment in payments if payment.id is not None}
    kept_ids = {payment.id for payment in kept}
    return PaymentPlan(
        status=status,
        payments=sorted(planned, key=_payment_order),
        updated_ids=frozenset(
            payment.id
            for payment in planned
            if payment.id is not None and payment != originals[payment.id]
        ),
        deleted_ids=frozenset(id_ for id_ in originals if id_ not in kept_ids),
    )


def _next_status(today: date, subscription: PaymentSubscription) -> SubscriptionStatus:
    trial_end_date = subscription.trial_end_date
    if (
        subscription.status is SubscriptionStatus.TRIAL
        and trial_end_date is not None
        and trial_end_date < today
    ):
        return SubscriptionStatus.ACTIVE
    return subscription.status


def _is_cut_off(
    subscription: PaymentSubscription, status: SubscriptionStatus, payment: Payment
) -> bool:
    if not payment.is_scheduled_expected:
        return False
    due = payment.due_date
    paused_at, ends_on, archived_on = (
        subscription.paused_at,
        subscription.ends_on,
        subscription.archived_on,
    )
    return (
        (status is SubscriptionStatus.PAUSED and paused_at is not None and due > paused_at)
        or (status is SubscriptionStatus.CANCELLED and ends_on is not None and due >= ends_on)
        or (archived_on is not None and due > archived_on)
    )


def _repriced(payment: Payment, price_history: Sequence[PriceChange]) -> Payment:
    if not payment.is_scheduled_expected:
        return payment
    return replace(payment, expected_cents=price_in_force(price_history, payment.due_date))


def _backfilled(
    subscription: PaymentSubscription,
    price_history: Sequence[PriceChange],
    planned: Sequence[Payment],
) -> list[Payment]:
    taken = {payment.due_date for payment in planned}
    last_day = subscription.created_on - timedelta(days=1)
    backfilled: list[Payment] = []
    for due in billing_dates_between(
        subscription.anchor_date, subscription.interval, subscription.anchor_date, last_day
    ):
        if due in taken:
            continue
        price = price_in_force(price_history, due)
        backfilled.append(
            Payment(
                due_date=due,
                expected_cents=price,
                status=PaymentStatus.PAID,
                source=PaymentSource.BACKFILL,
                paid_cents=price,
                paid_date=due,
            )
        )
    return backfilled


def _generated(
    today: date,
    settings: PaymentSettings,
    subscription: PaymentSubscription,
    status: SubscriptionStatus,
    price_history: Sequence[PriceChange],
    planned: Sequence[Payment],
) -> list[Payment]:
    if status not in {SubscriptionStatus.TRIAL, SubscriptionStatus.ACTIVE}:
        return []
    if subscription.archived_on is not None:
        return []
    taken = {payment.due_date for payment in planned if payment.source is not PaymentSource.MANUAL}
    start = max(
        day
        for day in (subscription.created_on, subscription.resumed_on, subscription.unarchived_on)
        if day is not None
    )
    end = today + timedelta(days=settings.payment_horizon_days)
    return [
        Payment(
            due_date=due,
            expected_cents=price_in_force(price_history, due),
            status=PaymentStatus.EXPECTED,
            source=PaymentSource.SCHEDULED,
        )
        for due in billing_dates_between(
            subscription.anchor_date, subscription.interval, start, end
        )
        if due not in taken
    ]


def _auto_confirmed(today: date, payment: Payment) -> Payment:
    if not payment.is_scheduled_expected or payment.due_date > today:
        return payment
    return replace(
        payment,
        status=PaymentStatus.PAID,
        paid_cents=payment.expected_cents,
        paid_date=payment.due_date,
    )


def _payment_order(payment: Payment) -> tuple[date, bool, int]:
    return (payment.due_date, payment.id is None, payment.id or 0)
