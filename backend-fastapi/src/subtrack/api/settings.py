from dataclasses import asdict
from typing import Annotated, Literal

from fastapi import APIRouter, Depends
from pydantic import AfterValidator, BaseModel, ConfigDict, Field
from pydantic_core import PydanticCustomError

from subtrack.api.dependencies import settings_service
from subtrack.api.types import NonBlankText
from subtrack.clock.zones import zone_names
from subtrack.service.settings import SettingsService, UserSettings


def _known_zone(name: str) -> str:
    if name not in zone_names():
        raise PydanticCustomError("time_zone_unknown", "Input should be a known IANA time zone")
    return name


type ZoneShape = Annotated[
    str,
    Field(max_length=64, pattern=r"^[A-Za-z][A-Za-z0-9_+-]*(/[A-Za-z0-9_+-]+)*$"),
]
type TimeZoneName = Annotated[ZoneShape, AfterValidator(_known_zone)]
type ReminderDays = Annotated[int, Field(ge=0, le=365)]


class SettingsBody(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True, frozen=True)

    default_reminder_days: ReminderDays
    trial_reminder_days: ReminderDays
    yearly_reminder_days: ReminderDays
    ending_reminder_days: ReminderDays
    payment_horizon_days: Annotated[int, Field(ge=1, le=366)]
    logo_dev_token: Annotated[NonBlankText, Field(max_length=200)] | None
    locale: Literal["en", "es"] | None
    theme: Literal["system", "light", "dark"]
    font_size: Literal["sm", "md", "lg"]
    reduced_motion: Literal["system", "always"]
    week_start: Annotated[int, Field(ge=1, le=7)]
    lock_policy: Literal["on_open", "idle_5", "idle_15", "idle_30", "idle_60", "manual"]
    home_time_zone: TimeZoneName | None
    display_time_zone: TimeZoneName | None


class SettingsResponse(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

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
    display_time_zone: str | None


router = APIRouter(prefix="/settings", tags=["settings"])

type Settings = Annotated[SettingsService, Depends(settings_service)]


@router.api_route("", methods=["GET", "HEAD"])
def get_settings(settings: Settings) -> SettingsResponse:
    return _response(settings.get())


@router.put("")
def update_settings(body: SettingsBody, settings: Settings) -> SettingsResponse:
    return _response(settings.update(UserSettings(**body.model_dump())))


def _response(settings: UserSettings) -> SettingsResponse:
    return SettingsResponse(**asdict(settings))
