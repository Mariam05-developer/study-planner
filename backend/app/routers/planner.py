import re
from datetime import date, datetime, timedelta, timezone
from math import fsum
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session

from .. import planner, schemas
from ..deps import get_current_user, get_now, get_session
from ..models import User
from .assignments import get_user_assignments, to_response

router = APIRouter(prefix="/api", tags=["planner"])


def parse_zone(name):
    # Java sometimes sends an offset like GMT-06:00 instead of a zone name
    match = re.fullmatch(r"(?:GMT|UTC)?([+-])(0\d|1[0-8]):([0-5]\d)", name)
    if match:
        sign, hours, minutes = match.groups()
        offset = timedelta(hours=int(hours), minutes=int(minutes))
        if sign == "-":
            offset = -offset
        return timezone(offset)
    try:
        return ZoneInfo(name)
    except (KeyError, ValueError, OSError):
        raise HTTPException(422, f"Unknown time zone: {name}")


@router.get("/planner/next", response_model=list[schemas.RankedAssignment])
def what_next(
    limit: int = Query(5, ge=1, le=100),
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
    now: datetime = Depends(get_now),
):
    ranked = planner.rank_assignments(get_user_assignments(session, user), user, now)

    results = []
    for rank, (score, risk, reason, a) in enumerate(ranked[:limit], start=1):
        results.append(
            schemas.RankedAssignment(
                rank=rank,
                score=score,
                risk=risk,
                reason=reason,
                assignment=to_response(a),
            )
        )
    return results


@router.get("/planner/week", response_model=schemas.WeekWorkload)
def weekly_workload(
    start: date | None = None,
    tz: str = Query("UTC", max_length=64),
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
    now: datetime = Depends(get_now),
):
    zone = parse_zone(tz)
    day = start or now.astimezone(zone).date()
    if not 2000 <= day.year <= 2200:
        raise HTTPException(422, "start must be between the years 2000 and 2200")

    week_start = planner.monday_of(day)
    assignments = get_user_assignments(session, user)
    days = planner.week_workload(assignments, user, week_start, zone, now)
    total = fsum(item["hours"] for d in days for item in d["plan"])

    return schemas.WeekWorkload(
        week_start=week_start,
        week_end=week_start + timedelta(days=6),
        timezone=tz,
        total_planned_hours=round(total, 1),
        total_capacity_hours=round(user.daily_study_hours * 7, 2),
        days=days,
    )


@router.get("/reminders", response_model=list[schemas.Reminder])
def get_reminders(
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
    now: datetime = Depends(get_now),
):
    results = []
    for a in get_user_assignments(session, user):
        reminder = planner.active_reminder(a, user, now)
        if reminder:
            kind, message = reminder
            results.append(
                schemas.Reminder(kind=kind, message=message, assignment=to_response(a))
            )
    return results
