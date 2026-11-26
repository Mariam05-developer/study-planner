# run with: uvicorn app.main:create_app --factory
import os
from pathlib import Path

from fastapi import FastAPI
from sqlalchemy.orm import sessionmaker

from .database import Base, make_engine
from .routers import assignments, auth, courses, planner
from .security import LoginThrottle

DEFAULT_DATABASE = Path(__file__).resolve().parent.parent / "studyplanner.db"


def create_app(database_url=None):
    if database_url is None:
        database_url = os.environ.get(
            "STUDYPLANNER_DATABASE_URL", f"sqlite:///{DEFAULT_DATABASE.as_posix()}"
        )
    engine = make_engine(database_url)
    Base.metadata.create_all(engine)

    app = FastAPI(title="Study Planner API")
    app.state.engine = engine
    app.state.sessions = sessionmaker(engine, expire_on_commit=False)
    app.state.login_throttle = LoginThrottle()

    app.include_router(auth.router)
    app.include_router(courses.router)
    app.include_router(assignments.router)
    app.include_router(planner.router)

    @app.get("/api/health")
    def health():
        return {"status": "ok"}

    return app
