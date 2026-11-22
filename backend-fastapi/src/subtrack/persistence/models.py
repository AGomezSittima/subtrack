from datetime import datetime

from sqlalchemy import Text
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column

from subtrack.persistence.types import UtcTimestamp


class Base(DeclarativeBase):
    pass


class SettingRow(Base):
    __tablename__ = "setting"

    id: Mapped[int] = mapped_column(primary_key=True)
    key: Mapped[str] = mapped_column(Text, unique=True)
    value: Mapped[str | None] = mapped_column(Text)
    updated_at: Mapped[datetime] = mapped_column(UtcTimestamp)


class SessionRow(Base):
    __tablename__ = "session"

    id: Mapped[int] = mapped_column(primary_key=True)
    token_hash: Mapped[str] = mapped_column(Text, unique=True)
    created_at: Mapped[datetime] = mapped_column(UtcTimestamp)
    last_activity_at: Mapped[datetime] = mapped_column(UtcTimestamp)
