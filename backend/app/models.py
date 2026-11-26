from datetime import datetime

from sqlalchemy import ForeignKey, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .database import Base, UTCDateTime


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    username: Mapped[str] = mapped_column(String(32), unique=True)
    password_hash: Mapped[str] = mapped_column(String(200))
    daily_study_hours: Mapped[float] = mapped_column(default=3.0)
    reminder_hours: Mapped[int] = mapped_column(default=48)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime)


class AuthToken(Base):
    __tablename__ = "auth_tokens"

    # we store a hash of the token, not the token itself
    token_hash: Mapped[str] = mapped_column(String(64), primary_key=True)
    user_id: Mapped[int] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    expires_at: Mapped[datetime] = mapped_column(UTCDateTime)

    user: Mapped[User] = relationship()


class Course(Base):
    __tablename__ = "courses"
    __table_args__ = (UniqueConstraint("user_id", "code"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    code: Mapped[str] = mapped_column(String(20))
    name: Mapped[str] = mapped_column(String(100))


class Assignment(Base):
    __tablename__ = "assignments"
    # don't reuse the ids of deleted assignments
    __table_args__ = {"sqlite_autoincrement": True}

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(
        ForeignKey("users.id", ondelete="CASCADE"), index=True
    )
    course_id: Mapped[int | None] = mapped_column(
        ForeignKey("courses.id", ondelete="SET NULL")
    )
    title: Mapped[str] = mapped_column(String(200))
    notes: Mapped[str] = mapped_column(String(2000), default="")
    due_at: Mapped[datetime] = mapped_column(UTCDateTime, index=True)
    estimated_hours: Mapped[float]
    hours_spent: Mapped[float] = mapped_column(default=0.0)
    completed_at: Mapped[datetime | None] = mapped_column(UTCDateTime)
    reminder_hours: Mapped[int | None]  # None = use the user's setting
    dismissed_stage: Mapped[str | None] = mapped_column(String(10))
    created_at: Mapped[datetime] = mapped_column(UTCDateTime)

    course: Mapped[Course | None] = relationship(lazy="joined")

    @property
    def completed(self):
        return self.completed_at is not None

    @property
    def course_code(self):
        if self.course is None:
            return None
        return self.course.code
