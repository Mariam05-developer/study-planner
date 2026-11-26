import datetime as dt
import re

from pydantic import (
    AwareDatetime,
    BaseModel,
    ConfigDict,
    Field,
    field_validator,
    model_validator,
)


class RequestModel(BaseModel):
    # error out on unknown fields so typos don't get ignored
    model_config = ConfigDict(extra="forbid")


def strip_text(value):
    if isinstance(value, str):
        return value.strip()
    return value


def check_year(value):
    if value is not None and not 2000 <= value.year <= 2200:
        raise ValueError("due_at must be between the years 2000 and 2200")
    return value


def check_nulls(model, allowed=()):
    # in an update, leaving a field out is fine but sending null usually isn't
    for name in model.model_fields_set:
        if name not in allowed and getattr(model, name) is None:
            raise ValueError(f"{name} cannot be null")


# accounts


class RegisterRequest(RequestModel):
    username: str
    password: str = Field(min_length=8, max_length=128)

    @field_validator("username")
    @classmethod
    def check_username(cls, value):
        value = value.strip().lower()
        if not re.fullmatch(r"[a-z0-9_.-]{3,32}", value):
            raise ValueError("username must be 3-32 letters, numbers, _ . or -")
        return value


class LoginRequest(RequestModel):
    username: str = Field(max_length=100)
    password: str = Field(max_length=128)


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    username: str
    daily_study_hours: float
    reminder_hours: int


class TokenOut(BaseModel):
    token: str
    expires_at: dt.datetime
    user: UserOut


class SettingsUpdate(RequestModel):
    daily_study_hours: float | None = Field(None, gt=0, le=24)
    reminder_hours: int | None = Field(None, ge=0, le=1440)

    @model_validator(mode="after")
    def no_nulls(self):
        check_nulls(self)
        return self


# courses


class CourseCreate(RequestModel):
    code: str = Field(min_length=1, max_length=20)
    name: str = Field(min_length=1, max_length=100)

    _strip = field_validator("code", "name", mode="before")(strip_text)

    @field_validator("code")
    @classmethod
    def upper_code(cls, value):
        return value.upper()


class CourseUpdate(RequestModel):
    code: str | None = Field(None, min_length=1, max_length=20)
    name: str | None = Field(None, min_length=1, max_length=100)

    _strip = field_validator("code", "name", mode="before")(strip_text)

    @field_validator("code")
    @classmethod
    def upper_code(cls, value):
        return value.upper() if value else value

    @model_validator(mode="after")
    def no_nulls(self):
        check_nulls(self)
        return self


class CourseOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    code: str
    name: str


# assignments


class AssignmentCreate(RequestModel):
    title: str = Field(min_length=1, max_length=200)
    due_at: AwareDatetime
    estimated_hours: float = Field(gt=0, le=1000)
    course_id: int | None = None
    notes: str = Field("", max_length=2000)
    reminder_hours: int | None = Field(None, ge=0, le=1440)

    _strip = field_validator("title", mode="before")(strip_text)
    _year = field_validator("due_at")(check_year)


class AssignmentUpdate(RequestModel):
    title: str | None = Field(None, min_length=1, max_length=200)
    due_at: AwareDatetime | None = None
    estimated_hours: float | None = Field(None, gt=0, le=1000)
    hours_spent: float | None = Field(None, ge=0, le=10000)
    course_id: int | None = None
    notes: str | None = Field(None, max_length=2000)
    reminder_hours: int | None = Field(None, ge=0, le=1440)
    completed: bool | None = None

    _strip = field_validator("title", mode="before")(strip_text)
    _year = field_validator("due_at")(check_year)

    @model_validator(mode="after")
    def no_nulls(self):
        # these two can be cleared
        check_nulls(self, allowed=("course_id", "reminder_hours"))
        return self


class LogHoursRequest(RequestModel):
    hours: float = Field(gt=0, le=24)


class AssignmentOut(BaseModel):
    id: int
    title: str
    notes: str
    course_id: int | None
    course_code: str | None
    due_at: dt.datetime
    estimated_hours: float
    hours_spent: float
    remaining_hours: float
    reminder_hours: int | None
    completed: bool
    completed_at: dt.datetime | None


# planner


class RankedAssignment(BaseModel):
    rank: int
    score: float
    risk: str
    reason: str
    assignment: AssignmentOut


class Reminder(BaseModel):
    kind: str
    message: str
    assignment: AssignmentOut


class DueItem(BaseModel):
    assignment_id: int
    title: str
    course_code: str | None
    due_at: dt.datetime
    remaining_hours: float
    completed: bool


class PlannedItem(BaseModel):
    assignment_id: int
    title: str
    course_code: str | None
    hours: float


class DayWorkload(BaseModel):
    date: dt.date
    is_today: bool
    planned_hours: float
    capacity_hours: float
    overloaded: bool
    due: list[DueItem]
    plan: list[PlannedItem]


class WeekWorkload(BaseModel):
    week_start: dt.date
    week_end: dt.date
    timezone: str
    total_planned_hours: float
    total_capacity_hours: float
    days: list[DayWorkload]
