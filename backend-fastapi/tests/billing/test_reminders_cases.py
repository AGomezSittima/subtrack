import pytest

from subtrack.billing.payments import PaymentStatus, SubscriptionStatus
from subtrack.billing.reminders import (
    PlannedNotification,
    ReminderPayment,
    ReminderSettings,
    ReminderSubscription,
    plan_reminders,
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


def parse_subscription(value: Json) -> ReminderSubscription:
    return ReminderSubscription(
        id=value["id"],
        name=value["name"],
        interval=parse_interval(value["interval"]),
        status=SubscriptionStatus(value["status"]),
        archived=value["archived"],
        reminder_days=value["reminder_days"],
        trial_end_date=parse_optional_date(value.get("trial_end_date")),
        ends_on=parse_optional_date(value.get("ends_on")),
    )


def parse_payment(value: Json) -> ReminderPayment:
    return ReminderPayment(
        id=value["id"],
        subscription_id=value["subscription_id"],
        due_date=parse_date(value["due_date"]),
        expected_cents=value["expected_cents"],
        status=PaymentStatus(value["status"]),
    )


def notification_json(notification: PlannedNotification) -> Json:
    return {
        "type": notification.type.value,
        "severity": notification.severity.value,
        "trigger_date": notification.trigger_date.isoformat(),
        "dedupe_key": notification.dedupe_key,
        "subscription_id": notification.subscription_id,
        "payment_id": notification.payment_id,
        "params": without_none(
            {
                "name": notification.params.name,
                "date": notification.params.date.isoformat(),
                "amount_cents": notification.params.amount_cents,
            }
        ),
    }


@pytest.mark.parametrize("case", load_cases("reminders.json"))
def test_reminders(case: Json) -> None:
    given = case["input"]
    settings = given["settings"]

    notifications = plan_reminders(
        parse_optional_date(given["last_run_date"]),
        parse_date(given["today"]),
        ReminderSettings(
            default_reminder_days=settings["default_reminder_days"],
            trial_reminder_days=settings["trial_reminder_days"],
            yearly_reminder_days=settings["yearly_reminder_days"],
            ending_reminder_days=settings["ending_reminder_days"],
        ),
        [parse_subscription(subscription) for subscription in given["subscriptions"]],
        [parse_payment(payment) for payment in given["payments"]],
        frozenset(given["existing_dedupe_keys"]),
    )

    actual = {"notifications": [notification_json(n) for n in notifications]}
    assert actual == case["expected"], failure_message(case)
