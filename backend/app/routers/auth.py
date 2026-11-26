from datetime import datetime, timedelta

from fastapi import APIRouter, Depends, HTTPException, Request
from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from .. import schemas
from ..deps import get_current_user, get_now, get_session, get_token
from ..models import AuthToken, User
from ..security import hash_password, hash_token, new_token, verify_password

TOKEN_DAYS = 30

# checked when the username doesn't exist, so it takes the same time
DUMMY_HASH = hash_password("not a real password")

router = APIRouter(prefix="/api", tags=["accounts"])


def create_token(session, user, now):
    token = new_token()
    expires_at = now + timedelta(days=TOKEN_DAYS)

    # clean up old tokens while we're here
    session.execute(delete(AuthToken).where(AuthToken.expires_at <= now))
    session.add(
        AuthToken(token_hash=hash_token(token), user_id=user.id, expires_at=expires_at)
    )
    session.commit()
    return {"token": token, "expires_at": expires_at, "user": user}


def find_user(session, username):
    return session.scalar(select(User).where(User.username == username))


@router.post("/auth/register", status_code=201, response_model=schemas.TokenOut)
def register(
    body: schemas.RegisterRequest,
    session: Session = Depends(get_session),
    now: datetime = Depends(get_now),
):
    if find_user(session, body.username):
        raise HTTPException(409, "That username is already taken")

    user = User(
        username=body.username,
        password_hash=hash_password(body.password),
        created_at=now,
    )
    session.add(user)
    session.commit()
    return create_token(session, user, now)


@router.post("/auth/login", response_model=schemas.TokenOut)
def login(
    body: schemas.LoginRequest,
    request: Request,
    session: Session = Depends(get_session),
    now: datetime = Depends(get_now),
):
    username = body.username.strip().lower()
    throttle = request.app.state.login_throttle

    wait = throttle.retry_after(username)
    if wait > 0:
        raise HTTPException(
            429,
            f"Too many failed logins. Try again in {wait} seconds",
            headers={"Retry-After": str(wait)},
        )

    user = find_user(session, username)
    stored = user.password_hash if user else DUMMY_HASH
    if not verify_password(body.password, stored) or user is None:
        throttle.record_failure(username)
        raise HTTPException(401, "Wrong username or password")

    throttle.reset(username)
    return create_token(session, user, now)


@router.post("/auth/logout", status_code=204)
def logout(
    token: AuthToken = Depends(get_token),
    session: Session = Depends(get_session),
):
    session.delete(token)
    session.commit()


@router.get("/me", response_model=schemas.UserOut)
def get_me(user: User = Depends(get_current_user)):
    return user


@router.patch("/me", response_model=schemas.UserOut)
def update_settings(
    body: schemas.SettingsUpdate,
    user: User = Depends(get_current_user),
    session: Session = Depends(get_session),
):
    for name, value in body.model_dump(exclude_unset=True).items():
        setattr(user, name, value)
    session.commit()
    return user
