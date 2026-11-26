def course(client, headers, code="cs101", name="Programming"):
    response = client.post(
        "/api/courses", headers=headers, json={"code": code, "name": name}
    )
    assert response.status_code == 201, response.text
    return response.json()


def test_course_codes_are_unique_per_user(client, alice, bob):
    first = course(client, alice)
    assert first["code"] == "CS101"
    duplicate = client.post(
        "/api/courses", headers=alice, json={"code": "CS101", "name": "Again"}
    )
    assert duplicate.status_code == 409
    course(client, bob)

    other = course(client, alice, "MA1", "Maths")
    renamed = client.patch(
        f"/api/courses/{other['id']}", headers=alice, json={"code": "cs101"}
    )
    assert renamed.status_code == 409


def test_deleting_a_course_keeps_its_assignments(client, alice, clock):
    c = course(client, alice)
    created = client.post(
        "/api/assignments",
        headers=alice,
        json={
            "title": "Lab",
            "due_at": clock.iso(days=1),
            "estimated_hours": 1,
            "course_id": c["id"],
        },
    ).json()
    assert created["course_code"] == "CS101"

    assert client.delete(f"/api/courses/{c['id']}", headers=alice).status_code == 204
    kept = client.get(f"/api/assignments/{created['id']}", headers=alice).json()
    assert kept["course_id"] is None and kept["course_code"] is None


def test_moving_between_courses_updates_the_course_code(client, alice, add):
    c = course(client, alice)
    a = add()
    url = f"/api/assignments/{a['id']}"
    moved = client.patch(url, headers=alice, json={"course_id": c["id"]}).json()
    assert moved["course_code"] == "CS101"
    cleared = client.patch(url, headers=alice, json={"course_id": None}).json()
    assert cleared["course_code"] is None


def test_due_dates_are_converted_to_utc(client, alice):
    created = client.post(
        "/api/assignments",
        headers=alice,
        json={
            "title": "Lab",
            "due_at": "2026-03-20T23:30:00+05:30",
            "estimated_hours": 1,
        },
    ).json()
    assert created["due_at"] == "2026-03-20T18:00:00Z"


def test_invalid_assignments_are_rejected(client, alice, add, clock):
    good = {"title": "x", "due_at": clock.iso(days=1), "estimated_hours": 1}
    for change in (
        {"title": "   "},
        {"due_at": "2026-03-20T10:00:00"},  # no time zone
        {"due_at": "9999-12-31T23:59:59Z"},
        {"estimated_hours": 0},
        {"estimated_hours": -1},
        {"course_id": 999},
    ):
        response = client.post("/api/assignments", headers=alice, json=good | change)
        assert response.status_code in (404, 422), change

    url = f"/api/assignments/{add()['id']}"
    for body in ({"title": None}, {"due_at": None}, {"completed": None}):
        assert client.patch(url, headers=alice, json=body).status_code == 422


def test_complete_reopen_and_filter(client, alice, add):
    a = add()
    url = f"/api/assignments/{a['id']}"
    done = client.patch(url, headers=alice, json={"completed": True}).json()
    assert done["completed"] and done["remaining_hours"] == 0

    listing = lambda status: client.get(
        f"/api/assignments?status={status}", headers=alice
    ).json()
    assert listing("open") == []
    assert len(listing("done")) == len(listing("all")) == 1

    reopened = client.patch(url, headers=alice, json={"completed": False}).json()
    assert not reopened["completed"] and reopened["completed_at"] is None


def test_logging_hours_reduces_remaining_work(client, alice, add):
    url = f"/api/assignments/{add(effort=3)['id']}/log"
    after = client.post(url, headers=alice, json={"hours": 1.25}).json()
    assert (after["hours_spent"], after["remaining_hours"]) == (1.25, 1.75)
    over = client.post(url, headers=alice, json={"hours": 5}).json()
    # Unfinished work never drops to zero.
    assert over["remaining_hours"] == 0.5


def test_users_cannot_touch_each_others_data(client, alice, bob, add, clock):
    a = add()
    c = course(client, alice)
    url = f"/api/assignments/{a['id']}"
    assert client.get(url, headers=bob).status_code == 404
    assert client.patch(url, headers=bob, json={"title": "x"}).status_code == 404
    assert client.post(url + "/log", headers=bob, json={"hours": 1}).status_code == 404
    assert client.post(url + "/dismiss-reminder", headers=bob).status_code == 404
    assert client.delete(url, headers=bob).status_code == 404
    assert client.delete(f"/api/courses/{c['id']}", headers=bob).status_code == 404
    stolen = client.post(
        "/api/assignments",
        headers=bob,
        json={
            "title": "x",
            "due_at": clock.iso(days=1),
            "estimated_hours": 1,
            "course_id": c["id"],
        },
    )
    assert stolen.status_code == 404
    for path in ("/api/assignments", "/api/courses", "/api/planner/next"):
        assert client.get(path, headers=bob).json() == []
    assert client.get(url, headers=alice).json()["title"] == "Essay"


def test_delete_does_not_reuse_ids(client, alice, add):
    first = add()
    client.delete(f"/api/assignments/{first['id']}", headers=alice)
    assert add()["id"] != first["id"]


def test_data_survives_a_restart(tmp_path):
    from fastapi.testclient import TestClient

    from app.main import create_app

    from .conftest import register

    url = f"sqlite:///{(tmp_path / 'db.sqlite').as_posix()}"
    first = create_app(url)
    headers = register(TestClient(first))
    first.state.engine.dispose()
    second = create_app(url)
    assert TestClient(second).get("/api/me", headers=headers).status_code == 200
    second.state.engine.dispose()
