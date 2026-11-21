from enum import StrEnum


class BillingErrorCode(StrEnum):
    INTERVAL_COUNT_NOT_POSITIVE = "interval_count_not_positive"
    NEGATIVE_INDEX = "negative_index"
    NEGATIVE_CENTS = "negative_cents"
    TRIAL_END_DATE_REQUIRED = "trial_end_date_required"
    PAUSED_AT_REQUIRED = "paused_at_required"
    ENDS_ON_REQUIRED = "ends_on_required"
    PRICE_HISTORY_EMPTY = "price_history_empty"
    PRICE_NOT_IN_FORCE = "price_not_in_force"


class BillingInputError(ValueError):
    def __init__(self, code: BillingErrorCode) -> None:
        super().__init__(code.value)
        self.code = code
