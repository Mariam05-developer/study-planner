from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select, update
from sqlalchemy.orm import Session

from .. import schemas
from ..deps import get_current_user, get_session
from ..models import Assignment, Course, User

router = APIRouter(prefix="/api/courses", tags=["courses"])


def get_course(session, user, course_id):
    course = session.get(Course, course_id)
    # 404 for other people's courses too
    if course is None or course.user_id != user.id:
        raise HTTPException(404, "Course not found")
    return course


def check_code_free(session, user, code, course_id=None):
    query = select(Course).where(Course.user_id == user.id, Course.code == code)
    existing = session.scalar(query)
    if existing and existing.id != course_id:
        raise HTTPException(409, "You already have a course with that code")


@router.get("", response_model=list[schemas.CourseOut])
def list_courses(
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
):
    query = select(Course).where(Course.user_id == user.id).order_by(Course.code)
    return session.scalars(query).all()


@router.post("", status_code=201, response_model=schemas.CourseOut)
def create_course(
    body: schemas.CourseCreate,
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
):
    check_code_free(session, user, body.code)
    course = Course(user_id=user.id, code=body.code, name=body.name)
    session.add(course)
    session.commit()
    return course


@router.patch("/{course_id}", response_model=schemas.CourseOut)
def update_course(
    course_id: int,
    body: schemas.CourseUpdate,
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
):
    course = get_course(session, user, course_id)
    if body.code:
        check_code_free(session, user, body.code, course.id)
    for name, value in body.model_dump(exclude_unset=True).items():
        setattr(course, name, value)
    session.commit()
    return course


@router.delete("/{course_id}", status_code=204)
def delete_course(
    course_id: int,
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
):
    course = get_course(session, user, course_id)
    # keep the assignments, just take them out of the course
    session.execute(
        update(Assignment).where(Assignment.course_id == course.id).values(course_id=None)
    )
    session.delete(course)
    session.commit()
