from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from .. import planner, schemas
from ..deps import get_current_user, get_now, get_session
from ..models import Assignment, User
from .courses import get_course

router = APIRouter(prefix="/api/assignments", tags=["assignments"])


def to_response(a):
    return schemas.AssignmentOut(
        id=a.id,
        title=a.title,
        notes=a.notes,
        course_id=a.course_id,
        course_code=a.course_code,
        due_at=a.due_at,
        estimated_hours=a.estimated_hours,
        hours_spent=a.hours_spent,
        remaining_hours=planner.remaining_hours(a),
        reminder_hours=a.reminder_hours,
        completed=a.completed,
        completed_at=a.completed_at,
    )


def get_user_assignments(session, user):
    query = (
        select(Assignment)
        .where(Assignment.user_id == user.id)
        .order_by(Assignment.due_at, Assignment.id)
    )
    return session.scalars(query).all()


def get_assignment_or_404(session, user, assignment_id):
    assignment = session.get(Assignment, assignment_id)
    if assignment is None or assignment.user_id != user.id:
        raise HTTPException(404, "Assignment not found")
    return assignment


def save(session, assignment):
    session.commit()
    session.refresh(assignment)  # reloads the course too
    return to_response(assignment)


@router.get("", response_model=list[schemas.AssignmentOut])
def list_assignments(
    status: str = "open",
    course_id: int | None = None,
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
):
    if status not in ("open", "done", "all"):
        raise HTTPException(422, "status must be open, done or all")

    results = []
    for a in get_user_assignments(session, user):
        if status == "open" and a.completed:
            continue
        if status == "done" and not a.completed:
            continue
        if course_id is not None and a.course_id != course_id:
            continue
        results.append(to_response(a))
    return results


@router.post("", status_code=201, response_model=schemas.AssignmentOut)
def create_assignment(
    body: schemas.AssignmentCreate,
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
    now: datetime = Depends(get_now),
):
    if body.course_id is not None:
        get_course(session, user, body.course_id)  # must be one of theirs
    assignment = Assignment(user_id=user.id, created_at=now, **body.model_dump())
    session.add(assignment)
    return save(session, assignment)


@router.get("/{assignment_id}", response_model=schemas.AssignmentOut)
def get_assignment(
    assignment_id: int,
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
):
    return to_response(get_assignment_or_404(session, user, assignment_id))


@router.patch("/{assignment_id}", response_model=schemas.AssignmentOut)
def update_assignment(
    assignment_id: int,
    body: schemas.AssignmentUpdate,
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
    now: datetime = Depends(get_now),
):
    assignment = get_assignment_or_404(session, user, assignment_id)
    changes = body.model_dump(exclude_unset=True)
    if changes.get("course_id") is not None:
        get_course(session, user, changes["course_id"])

    # "completed" isn't a column, it sets completed_at
    completed = changes.pop("completed", None)
    if completed is True and not assignment.completed:
        assignment.completed_at = now
    elif completed is False:
        assignment.completed_at = None

    old_due = assignment.due_at
    old_reminder = assignment.reminder_hours
    for name, value in changes.items():
        setattr(assignment, name, value)

    # show the reminder again if the deadline or reminder time changed
    if assignment.due_at != old_due or assignment.reminder_hours != old_reminder:
        assignment.dismissed_stage = None

    return save(session, assignment)


@router.post("/{assignment_id}/log", response_model=schemas.AssignmentOut)
def log_hours(
    assignment_id: int,
    body: schemas.LogHoursRequest,
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
):
    assignment = get_assignment_or_404(session, user, assignment_id)
    total = assignment.hours_spent + body.hours
    assignment.hours_spent = round(min(total, 10000), 2)
    return save(session, assignment)


@router.post("/{assignment_id}/dismiss-reminder", status_code=204)
def dismiss_reminder(
    assignment_id: int,
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
    now: datetime = Depends(get_now),
):
    assignment = get_assignment_or_404(session, user, assignment_id)
    reminder = planner.active_reminder(assignment, user, now)
    if reminder is None:
        raise HTTPException(409, "That assignment has no active reminder")
    assignment.dismissed_stage = reminder[0]
    session.commit()


@router.delete("/{assignment_id}", status_code=204)
def delete_assignment(
    assignment_id: int,
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
):
    session.delete(get_assignment_or_404(session, user, assignment_id))
    session.commit()
