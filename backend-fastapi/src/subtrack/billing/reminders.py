from collections.abc import Iterator, Sequence, Set
from dataclasses import dataclass
from datetime import date, timedelta
from enum import StrEnum

from subtrack.billing.errors import BillingErrorCode, BillingInputError
from subtrack.billing.interval import Interval, is_yearly_or_longer
from subtrack.billing.payments import PaymentStatus, SubscriptionStatus

REMINDER_OFF = -1


class NotificationType(StrEnum):
    RENEWAL = "renewal"
    YEARLY_RENEWAL = "yearly_renewal"
    TRIAL_ENDING = "trial_ending"
    ENDING = "ending"
    OVERDUE = "overdue"


class Severity(StrEnum):
    INFO = "info"
    WARNING = "warning"
    CRITICAL = "critical"


_SEVERITY = {
    NotificationType.RENEWAL: Severity.INFO,
    NotificationType.YEARLY_RENEWAL: Severity.INFO,
    NotificationType.TRIAL_ENDING: Severity.WARNING,
    NotificationType.ENDING: Severity.WARNING,
    NotificationType.OVERDUE: Severity.CRITICAL,
}


@dataclass(frozen=True, slots=True)
class ReminderSettings:
    default_reminder_days: int
    trial_reminder_days: int
    yearly_reminder_days: int
    ending_reminder_days: int


@dataclass(frozen=True, slots=True, kw_only=True)
class ReminderSubscription:
    id: int
    name: str
    interval: Interval
    status: SubscriptionStatus
    archived: bool
    reminder_days: int | None = None
    trial_end_date: date | None = None
    ends_on: date | None = None

    def __post_init__(self) -> None:
        if self.status is SubscriptionStatus.TRIAL and self.trial_end_date is None:
            raise BillingInputError(BillingErrorCode.TRIAL_END_DATE_REQUIRED)
        if self.status is SubscriptionStatus.CANCELLED and self.ends_on is None:
            raise BillingInputError(BillingErrorCode.ENDS_ON_REQUIRED)


@dataclass(frozen=True, slots=True, kw_only=True)
class ReminderPayment:
    id: int
    subscription_id: int
    due_date: date
    expected_cents: int
    status: PaymentStatus


@dataclass(frozen=True, slots=True)
class NotificationParams:
    name: str
    date: date
    amount_cents: int | None = None


@dataclass(frozen=True, slots=True, kw_only=True)
class PlannedNotification:
    type: NotificationType
    severity: Severity
    trigger_date: date
    dedupe_key: str
    subscription_id: int
    payment_id: int | None
    params: NotificationParams


def dedupe_key(type_: NotificationType, subscription_id: int, trigger_date: date) -> str:
    return f"{type_.value}:{subscription_id}:{trigger_date.isoformat()}"


def plan_reminders(
    last_run_date: date | None,
    today: date,
    settings: ReminderSettings,
    subscriptions: Sequence[ReminderSubscription],
    payments: Sequence[ReminderPayment],
    existing_dedupe_keys: Set[str],
) -> list[PlannedNotification]:
    window_start = last_run_date if last_run_date is not None else today
    by_id = {subscription.id: subscription for subscription in subscriptions}
    candidates = [
        *(
            notification
            for subscription in subscriptions
            if not subscription.archived
            for notification in _subscription_triggers(today, settings, subscription)
        ),
        *(
            notification
            for payment in payments
            if (subscription := by_id.get(payment.subscription_id)) is not None
            and not subscription.archived
            for notification in _payment_triggers(today, settings, subscription, payment)
        ),
    ]

    seen = set(existing_dedupe_keys)
    planned: list[PlannedNotification] = []
    for notification in sorted(candidates, key=_notification_order):
        if not window_start <= notification.trigger_date <= today:
            continue
        if notification.dedupe_key in seen:
            continue
        seen.add(notification.dedupe_key)
        planned.append(notification)
    return planned


def _payment_triggers(
    today: date,
    settings: ReminderSettings,
    subscription: ReminderSubscription,
    payment: ReminderPayment,
) -> Iterator[PlannedNotification]:
    if payment.status is not PaymentStatus.EXPECTED:
        return
    params = NotificationParams(
        name=subscription.name, date=payment.due_date, amount_cents=payment.expected_cents
    )
    lead_days = _renewal_lead_days(settings, subscription)
    if (
        subscription.status is not SubscriptionStatus.PAUSED
        and lead_days != REMINDER_OFF
        and payment.due_date >= today
    ):
        renewal_type = (
            NotificationType.YEARLY_RENEWAL
            if is_yearly_or_longer(subscription.interval)
            else NotificationType.RENEWAL
        )
        yield _notification(
            renewal_type,
            subscription,
            payment.due_date - timedelta(days=lead_days),
            payment.id,
            params,
        )
    yield _notification(
        NotificationType.OVERDUE,
        subscription,
        payment.due_date + timedelta(days=1),
        payment.id,
        params,
    )


def _subscription_triggers(
    today: date, settings: ReminderSettings, subscription: ReminderSubscription
) -> Iterator[PlannedNotification]:
    trial_end_date = subscription.trial_end_date
    if (
        subscription.status is SubscriptionStatus.TRIAL
        and trial_end_date is not None
        and trial_end_date >= today
    ):
        yield _notification(
            NotificationType.TRIAL_ENDING,
            subscription,
            trial_end_date - timedelta(days=settings.trial_reminder_days),
            None,
            NotificationParams(name=subscription.name, date=trial_end_date),
        )
    ends_on = subscription.ends_on
    if (
        subscription.status is SubscriptionStatus.CANCELLED
        and ends_on is not None
        and ends_on >= today
    ):
        params = NotificationParams(name=subscription.name, date=ends_on)
        for trigger_date in (ends_on - timedelta(days=settings.ending_reminder_days), ends_on):
            yield _notification(NotificationType.ENDING, subscription, trigger_date, None, params)


def _renewal_lead_days(settings: ReminderSettings, subscription: ReminderSubscription) -> int:
    if subscription.reminder_days is not None:
        return subscription.reminder_days
    if is_yearly_or_longer(subscription.interval):
        return settings.yearly_reminder_days
    return settings.default_reminder_days


def _notification(
    type_: NotificationType,
    subscription: ReminderSubscription,
    trigger_date: date,
    payment_id: int | None,
    params: NotificationParams,
) -> PlannedNotification:
    return PlannedNotification(
        type=type_,
        severity=_SEVERITY[type_],
        trigger_date=trigger_date,
        dedupe_key=dedupe_key(type_, subscription.id, trigger_date),
        subscription_id=subscription.id,
        payment_id=payment_id,
        params=params,
    )


def _notification_order(notification: PlannedNotification) -> tuple[date, int, str]:
    return (notification.trigger_date, notification.subscription_id, notification.type.value)
