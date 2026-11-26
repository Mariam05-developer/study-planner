from sqlalchemy import select

from app.models import AuthToken, User

from .conftest import register


def test_register_login_logout(client):
    headers = register(client, "Carol")
    assert client.get("/api/me", headers=headers).json()["username"] == "carol"

    login = client.post(
        "/api/auth/login", json={"username": " CAROL ", "password": "correct horse"}
    )
    assert login.status_code == 200
    second = {"Authorization": f"Bearer {login.json()['token']}"}

    assert client.post("/api/auth/logout", headers=headers).status_code == 204
    assert client.get("/api/me", headers=headers).status_code == 401
    # Logging out one session leaves the other alone.
    assert client.get("/api/me", headers=second).status_code == 200


def test_secrets_are_not_stored_in_plain_text(client):
    headers = register(client)
    token = headers["Authorization"].split()[1]
    with client.app.state.sessions() as session:
        user = session.scalar(select(User))
        stored = session.scalar(select(AuthToken))
        assert "correct horse" not in user.password_hash
        assert stored.token_hash != token


def test_duplicate_username_is_rejected(client, alice):
    response = client.post(
        "/api/auth/register", json={"username": "ALICE", "password": "another pass"}
    )
    assert response.status_code == 409


def test_weak_or_malformed_registration_is_rejected(client):
    for body in (
        {"username": "dave", "password": "short"},
        {"username": "no spaces", "password": "long enough"},
        {"username": "ab", "password": "long enough"},
    ):
        assert client.post("/api/auth/register", json=body).status_code == 422


def test_wrong_password_and_unknown_user_look_the_same(client, alice):
    wrong = client.post(
        "/api/auth/login", json={"username": "alice", "password": "nope"}
    )
    unknown = client.post(
        "/api/auth/login", json={"username": "nobody", "password": "nope"}
    )
    assert wrong.status_code == unknown.status_code == 401
    assert wrong.json() == unknown.json()


def test_repeated_failures_are_throttled(client, alice):
    bad = {"username": "alice", "password": "nope"}
    for _ in range(5):
        assert client.post("/api/auth/login", json=bad).status_code == 401
    good = {"username": "alice", "password": "correct horse"}
    blocked = client.post("/api/auth/login", json=good)
    assert blocked.status_code == 429
    assert int(blocked.headers["Retry-After"]) > 0


def test_tokens_expire(client, alice, clock):
    clock.advance(days=31)
    assert client.get("/api/me", headers=alice).status_code == 401


def test_requests_without_a_valid_token_are_rejected(client):
    for path in ("/api/me", "/api/courses", "/api/assignments", "/api/reminders"):
        assert client.get(path).status_code == 401
        bad = {"Authorization": "Bearer made-up"}
        assert client.get(path, headers=bad).status_code == 401


def test_settings(client, alice):
    response = client.patch("/api/me", headers=alice, json={"daily_study_hours": 5})
    assert response.json()["daily_study_hours"] == 5
    assert response.json()["reminder_hours"] == 48
    for body in ({"daily_study_hours": 0}, {"daily_study_hours": None}, {"typo": 1}):
        assert client.patch("/api/me", headers=alice, json=body).status_code == 422
