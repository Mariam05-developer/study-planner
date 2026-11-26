# dependencies shared by the routers
from datetime import datetime, timezone

from fastapi import Depends, HTTPException, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from .models import AuthToken, User
from .security import hash_token

bearer = HTTPBearer(auto_error=False)


def get_session(request: Request):
    with request.app.state.sessions() as session:
        yield session


def get_now():
    # a dependency so the tests can fake the time
    return datetime.now(timezone.utc)


def get_token(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer),
    session: Session = Depends(get_session),
    now: datetime = Depends(get_now),
) -> AuthToken:
    if credentials is not None:
        token = session.get(AuthToken, hash_token(credentials.credentials))
        if token is not None and token.expires_at > now:
            return token
    raise HTTPException(401, "Not logged in or session expired")


def get_current_user(token: AuthToken = Depends(get_token)) -> User:
    return token.user
