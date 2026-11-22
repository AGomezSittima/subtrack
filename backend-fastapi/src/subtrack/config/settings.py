"""Typed application settings, read from environment variables."""

from importlib.metadata import version
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Every setting the backend reads. Nothing else reads the environment."""

    model_config = SettingsConfigDict(extra="ignore", frozen=True)

    db_path: Path = Field(default=Path("subs.db"), validation_alias="DB_PATH")
    test_mode: bool = Field(default=False, validation_alias="SUBTRACK_TEST_MODE")
    template_db_path: Path | None = Field(default=None, validation_alias="SUBTRACK_TEMPLATE_DB")
    version: str = Field(default_factory=lambda: version("subtrack"))
