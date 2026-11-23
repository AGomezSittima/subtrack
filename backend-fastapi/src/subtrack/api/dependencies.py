from dataclasses import dataclass
from pathlib import Path
from typing import cast

from fastapi import Request
from sqlalchemy import Engine

from subtrack.clock.clock import Clock
from subtrack.clock.home_zone import HomeZone
from subtrack.clock.settable import SettableClock
from subtrack.clock.system import SystemClock
from subtrack.config.settings import Settings
from subtrack.persistence.engine import create_session_factory, create_sqlite_engine
from subtrack.repository.template import SqlTemplateRepository
from subtrack.repository.transaction import sql_transaction
from subtrack.security.lock import LockEnforcer
from subtrack.service.admin import AdminService
from subtrack.service.catalog import CatalogService
from subtrack.service.health import HealthService
from subtrack.service.settings import SettingsService


@dataclass(frozen=True)
class Container:
    settings: Settings
    engine: Engine
    clock: Clock
    health: HealthService
    catalog: CatalogService
    settings_service: SettingsService
    lock: LockEnforcer
    admin: AdminService | None


def build_container(settings: Settings) -> Container:
    engine = create_sqlite_engine(settings.db_path)
    transaction = sql_transaction(create_session_factory(engine))
    clock: Clock = SystemClock()
    settable = SettableClock(clock) if settings.test_mode else None
    if settable is not None:
        clock = settable
    home_zone = HomeZone(clock, transaction)
    home_zone.reload()
    admin = None
    if settable is not None:
        template = SqlTemplateRepository(engine, _template_path(settings))
        admin = AdminService(template, settable, home_zone)
    return Container(
        settings=settings,
        engine=engine,
        clock=clock,
        health=HealthService(transaction, home_zone),
        catalog=CatalogService(transaction, clock),
        settings_service=SettingsService(transaction, clock, home_zone),
        lock=LockEnforcer(transaction, clock),
        admin=admin,
    )


def _template_path(settings: Settings) -> Path:
    path = settings.template_db_path
    if path is None or not path.is_file():
        msg = f"test mode needs a migrated template database (SUBTRACK_TEMPLATE_DB={path})"
        raise RuntimeError(msg)
    return path


def container(request: Request) -> Container:
    return cast("Container", request.app.state.container)


def settings(request: Request) -> Settings:
    return container(request).settings


def health_service(request: Request) -> HealthService:
    return container(request).health


def catalog_service(request: Request) -> CatalogService:
    return container(request).catalog


def settings_service(request: Request) -> SettingsService:
    return container(request).settings_service


def admin_service(request: Request) -> AdminService:
    admin = container(request).admin
    if admin is None:
        msg = "admin routes are only mounted in test mode"
        raise RuntimeError(msg)
    return admin
