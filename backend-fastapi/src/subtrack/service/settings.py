from dataclasses import dataclass

from subtrack.clock.clock import Clock
from subtrack.clock.home_zone import HomeZone
from subtrack.repository.setting_key import SettingKey
from subtrack.repository.transaction import Transaction


@dataclass(frozen=True)
class UserSettings:
    default_reminder_days: int
    trial_reminder_days: int
    yearly_reminder_days: int
    ending_reminder_days: int
    payment_horizon_days: int
    logo_dev_token: str | None
    locale: str | None
    theme: str
    font_size: str
    reduced_motion: str
    week_start: int
    lock_policy: str
    home_time_zone: str | None
    display_time_zone: str


_USER_KEYS = (
    SettingKey.DEFAULT_REMINDER_DAYS,
    SettingKey.TRIAL_REMINDER_DAYS,
    SettingKey.YEARLY_REMINDER_DAYS,
    SettingKey.ENDING_REMINDER_DAYS,
    SettingKey.PAYMENT_HORIZON_DAYS,
    SettingKey.LOGO_DEV_TOKEN,
    SettingKey.LOCALE,
    SettingKey.THEME,
    SettingKey.FONT_SIZE,
    SettingKey.REDUCED_MOTION,
    SettingKey.WEEK_START,
    SettingKey.LOCK_POLICY,
    SettingKey.HOME_TIME_ZONE,
    SettingKey.DISPLAY_TIME_ZONE,
)


class SettingsService:
    def __init__(self, transaction: Transaction, clock: Clock, home_zone: HomeZone) -> None:
        self._transaction = transaction
        self._clock = clock
        self._home_zone = home_zone

    def get(self) -> UserSettings:
        with self._transaction() as repositories:
            return _settings(repositories.settings.values(_USER_KEYS))

    def update(self, settings: UserSettings) -> UserSettings:
        now = self._clock.now()
        with self._transaction() as repositories:
            repositories.settings.save(_values(settings), now)
            saved = _settings(repositories.settings.values(_USER_KEYS))
        self._home_zone.set(saved.home_time_zone)
        return saved


def _settings(values: dict[SettingKey, str | None]) -> UserSettings:
    def integer(key: SettingKey) -> int:
        return int(text(key))

    def text(key: SettingKey) -> str:
        value = values[key]
        if value is None:
            msg = f"setting {key} has no value"
            raise ValueError(msg)
        return value

    return UserSettings(
        default_reminder_days=integer(SettingKey.DEFAULT_REMINDER_DAYS),
        trial_reminder_days=integer(SettingKey.TRIAL_REMINDER_DAYS),
        yearly_reminder_days=integer(SettingKey.YEARLY_REMINDER_DAYS),
        ending_reminder_days=integer(SettingKey.ENDING_REMINDER_DAYS),
        payment_horizon_days=integer(SettingKey.PAYMENT_HORIZON_DAYS),
        logo_dev_token=values[SettingKey.LOGO_DEV_TOKEN],
        locale=values[SettingKey.LOCALE],
        theme=text(SettingKey.THEME),
        font_size=text(SettingKey.FONT_SIZE),
        reduced_motion=text(SettingKey.REDUCED_MOTION),
        week_start=integer(SettingKey.WEEK_START),
        lock_policy=text(SettingKey.LOCK_POLICY),
        home_time_zone=values[SettingKey.HOME_TIME_ZONE],
        display_time_zone=text(SettingKey.DISPLAY_TIME_ZONE),
    )


def _values(settings: UserSettings) -> dict[SettingKey, str | None]:
    return {
        SettingKey.DEFAULT_REMINDER_DAYS: str(settings.default_reminder_days),
        SettingKey.TRIAL_REMINDER_DAYS: str(settings.trial_reminder_days),
        SettingKey.YEARLY_REMINDER_DAYS: str(settings.yearly_reminder_days),
        SettingKey.ENDING_REMINDER_DAYS: str(settings.ending_reminder_days),
        SettingKey.PAYMENT_HORIZON_DAYS: str(settings.payment_horizon_days),
        SettingKey.LOGO_DEV_TOKEN: settings.logo_dev_token,
        SettingKey.LOCALE: settings.locale,
        SettingKey.THEME: settings.theme,
        SettingKey.FONT_SIZE: settings.font_size,
        SettingKey.REDUCED_MOTION: settings.reduced_motion,
        SettingKey.WEEK_START: str(settings.week_start),
        SettingKey.LOCK_POLICY: settings.lock_policy,
        SettingKey.HOME_TIME_ZONE: settings.home_time_zone,
        SettingKey.DISPLAY_TIME_ZONE: settings.display_time_zone,
    }
