from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient

from app.deps import get_now
from app.main import create_app

# A Wednesday, at noon.
START = datetime(2026, 3, 11, 12, 0, tzinfo=timezone.utc)


class Clock:
    def __init__(self):
        self.now = START

    def advance(self, **kwargs):
        self.now += timedelta(**kwargs)

    def iso(self, **kwargs) -> str:
        return (self.now + timedelta(**kwargs)).isoformat()


@pytest.fixture
def clock():
    return Clock()


@pytest.fixture
def client(clock):
    app = create_app("sqlite://")
    app.dependency_overrides[get_now] = lambda: clock.now
    with TestClient(app) as test_client:
        yield test_client
    app.state.engine.dispose()


def register(client, username="alice", password="correct horse"):
    response = client.post(
        "/api/auth/register", json={"username": username, "password": password}
    )
    assert response.status_code == 201, response.text
    return {"Authorization": f"Bearer {response.json()['token']}"}


@pytest.fixture
def alice(client):
    return register(client, "alice")


@pytest.fixture
def bob(client):
    return register(client, "bob")


@pytest.fixture
def add(client, alice, clock):
    # makes an assignment due some time from now

    def _add(title="Essay", effort=2, headers=None, **due):
        response = client.post(
            "/api/assignments",
            headers=headers or alice,
            json={
                "title": title,
                "due_at": clock.iso(**(due or {"days": 3})),
                "estimated_hours": effort,
            },
        )
        assert response.status_code == 201, response.text
        return response.json()

    return _add
