from datetime import date

import pytest

from subtrack.billing.costs import monthly_equivalent
from subtrack.billing.dates import billing_date
from subtrack.billing.errors import BillingErrorCode, BillingInputError
from subtrack.billing.interval import Interval, IntervalUnit
from subtrack.billing.payments import (
    PaymentSettings,
    PaymentSubscription,
    PriceChange,
    SubscriptionStatus,
    plan_payments,
    price_in_force,
)
from subtrack.billing.reminders import ReminderSubscription

MONTHLY = Interval(count=1, unit=IntervalUnit.MONTH)
DAY = date(2026, 11, 22)


def payment_subscription(status: SubscriptionStatus) -> PaymentSubscription:
    return PaymentSubscription(
        anchor_date=DAY, interval=MONTHLY, status=status, created_on=DAY, auto_confirm=False
    )


def test_interval_count_must_be_positive() -> None:
    with pytest.raises(BillingInputError) as error:
        Interval(count=0, unit=IntervalUnit.MONTH)
    assert error.value.code is BillingErrorCode.INTERVAL_COUNT_NOT_POSITIVE


def test_billing_date_index_must_not_be_negative() -> None:
    with pytest.raises(BillingInputError) as error:
        billing_date(DAY, MONTHLY, -1)
    assert error.value.code is BillingErrorCode.NEGATIVE_INDEX


def test_price_must_not_be_negative() -> None:
    with pytest.raises(BillingInputError) as error:
        monthly_equivalent(-1, MONTHLY)
    assert error.value.code is BillingErrorCode.NEGATIVE_CENTS


def test_br28_a_trial_needs_a_trial_end_date_for_payment_planning() -> None:
    with pytest.raises(BillingInputError) as error:
        payment_subscription(SubscriptionStatus.TRIAL)
    assert error.value.code is BillingErrorCode.TRIAL_END_DATE_REQUIRED


def test_br28_a_trial_needs_a_trial_end_date_for_reminder_planning() -> None:
    with pytest.raises(BillingInputError) as error:
        ReminderSubscription(
            id=1, name="Spotify", interval=MONTHLY, status=SubscriptionStatus.TRIAL, archived=False
        )
    assert error.value.code is BillingErrorCode.TRIAL_END_DATE_REQUIRED


@pytest.mark.parametrize(
    ("status", "code"),
    [
        (SubscriptionStatus.PAUSED, BillingErrorCode.PAUSED_AT_REQUIRED),
        (SubscriptionStatus.CANCELLED, BillingErrorCode.ENDS_ON_REQUIRED),
    ],
)
def test_status_needs_its_date(status: SubscriptionStatus, code: BillingErrorCode) -> None:
    with pytest.raises(BillingInputError) as error:
        payment_subscription(status)
    assert error.value.code is code


def test_payment_planning_needs_a_price_history() -> None:
    with pytest.raises(BillingInputError) as error:
        plan_payments(
            DAY,
            PaymentSettings(payment_horizon_days=30),
            payment_subscription(SubscriptionStatus.ACTIVE),
            [],
            [],
            backfill=False,
        )
    assert error.value.code is BillingErrorCode.PRICE_HISTORY_EMPTY


def test_no_price_is_in_force_before_the_first_change() -> None:
    with pytest.raises(BillingInputError) as error:
        price_in_force([PriceChange(price_cents=1199, effective_from=DAY)], date(2026, 11, 21))
    assert error.value.code is BillingErrorCode.PRICE_NOT_IN_FORCE
