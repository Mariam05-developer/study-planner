from datetime import timezone

from sqlalchemy import DateTime, create_engine, event
from sqlalchemy.orm import DeclarativeBase
from sqlalchemy.pool import StaticPool
from sqlalchemy.types import TypeDecorator


class Base(DeclarativeBase):
    pass


# SQLite doesn't keep time zones, so everything is saved as UTC
class UTCDateTime(TypeDecorator):
    impl = DateTime
    cache_ok = True

    def process_bind_param(self, value, dialect):
        if value is None:
            return None
        if value.tzinfo is None:
            raise ValueError("datetime needs a time zone")
        return value.astimezone(timezone.utc).replace(tzinfo=None)

    def process_result_value(self, value, dialect):
        if value is None:
            return None
        return value.replace(tzinfo=timezone.utc)


def make_engine(url):
    options = {}
    if url.startswith("sqlite"):
        options["connect_args"] = {"check_same_thread": False}
    if url == "sqlite://":
        # in-memory database for the tests, has to share one connection
        options["poolclass"] = StaticPool

    engine = create_engine(url, **options)

    if url.startswith("sqlite"):
        # foreign keys are off by default in SQLite
        @event.listens_for(engine, "connect")
        def turn_on_foreign_keys(connection, record):
            connection.execute("PRAGMA foreign_keys=ON")

    return engine
