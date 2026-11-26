def ranking(client, headers):
    return client.get("/api/planner/next?limit=100", headers=headers).json()


def titles(items):
    return [item["assignment"]["title"] for item in items]


def week(client, headers, query=""):
    response = client.get(f"/api/planner/week{query}", headers=headers)
    assert response.status_code == 200, response.text
    return response.json()


def reminders(client, headers):
    return client.get("/api/reminders", headers=headers).json()


# ranking


def test_overdue_comes_first_then_urgency(client, alice, add):
    add("far", effort=1, days=30)
    add("soon", effort=2, days=1)
    add("late", effort=1, days=-1)
    result = ranking(client, alice)
    assert titles(result) == ["late", "soon", "far"]
    assert result[0]["risk"] == "overdue"
    assert [r["rank"] for r in result] == [1, 2, 3]


def test_more_effort_ranks_higher_for_the_same_deadline(client, alice, add):
    add("small", effort=1, days=4)
    add("big", effort=8, days=4)
    assert titles(ranking(client, alice)) == ["big", "small"]


def test_a_big_task_can_beat_a_nearer_small_one(client, alice, add):
    add("quiz", effort=0.5, days=3)
    add("project", effort=20, days=6)
    result = ranking(client, alice)
    assert titles(result) == ["project", "quiz"]
    assert result[0]["risk"] == "at_risk"
    assert result[1]["risk"] == "on_track"


def test_logging_work_lowers_the_score(client, alice, add):
    a = add(effort=8, days=4)
    before = ranking(client, alice)[0]["score"]
    client.post(f"/api/assignments/{a['id']}/log", headers=alice, json={"hours": 6})
    assert ranking(client, alice)[0]["score"] < before


def test_completed_work_is_not_ranked_and_limit_applies(client, alice, add):
    done = add("done")
    add("one")
    add("two")
    client.patch(
        f"/api/assignments/{done['id']}", headers=alice, json={"completed": True}
    )
    assert len(ranking(client, alice)) == 2
    limited = client.get("/api/planner/next?limit=1", headers=alice).json()
    assert len(limited) == 1
    assert client.get("/api/planner/next?limit=0", headers=alice).status_code == 422


# weekly workload (the test clock starts Wednesday 2026-03-11 12:00 UTC)


def test_effort_is_spread_up_to_the_due_date(client, alice, add):
    add("essay", effort=6, days=2)  # due Friday
    result = week(client, alice)
    assert (result["week_start"], result["week_end"]) == ("2026-03-09", "2026-03-15")
    assert [d["planned_hours"] for d in result["days"]] == [0, 0, 2, 2, 2, 0, 0]
    assert [len(d["due"]) for d in result["days"]] == [0, 0, 0, 0, 1, 0, 0]
    assert result["days"][2]["is_today"]
    assert result["total_planned_hours"] == 6
    assert result["total_capacity_hours"] == 21


def test_overdue_work_lands_on_today_and_flags_overload(client, alice, add):
    add("late", effort=4, days=-2)
    today = week(client, alice)["days"][2]
    assert today["planned_hours"] == 4
    assert today["overloaded"]


def test_a_full_day_is_not_overloaded(client, alice, add):
    for name in "abc":
        add(name, effort=1, hours=1)
    today = week(client, alice)["days"][2]
    assert today["planned_hours"] == 3
    assert not today["overloaded"]


def test_work_spanning_weeks_is_split(client, alice, add):
    add("big", effort=10, days=9)  # 10 days, 1 hour each
    this_week = week(client, alice)
    next_week = week(client, alice, "?start=2026-03-18")
    assert this_week["total_planned_hours"] == 5
    assert next_week["total_planned_hours"] == 5
    assert next_week["week_start"] == "2026-03-16"
    assert week(client, alice, "?start=2026-03-02")["total_planned_hours"] == 0


def test_days_follow_the_requested_time_zone(client, alice, add):
    client.post(
        "/api/assignments",
        headers=alice,
        json={"title": "lab", "due_at": "2026-03-13T02:00:00Z", "estimated_hours": 1},
    )
    due_days = lambda q: [d["date"] for d in week(client, alice, q)["days"] if d["due"]]
    assert due_days("?tz=UTC") == ["2026-03-13"]
    assert due_days("?tz=America/New_York") == ["2026-03-12"]


def test_fixed_offset_time_zones(client, alice):
    client.post(
        "/api/assignments",
        headers=alice,
        json={"title": "lab", "due_at": "2026-03-13T02:00:00Z", "estimated_hours": 1},
    )
    for name, expected in (
        ("GMT-06:00", "2026-03-12"),
        ("-06:00", "2026-03-12"),
        ("UTC%2B05:30", "2026-03-13"),
    ):
        result = week(client, alice, f"?tz={name}")
        assert [d["date"] for d in result["days"] if d["due"]] == [expected], name


def test_bad_week_parameters(client, alice):
    for query in ("tz=Mars/Base", "tz=../etc", "tz=", "tz=GMT%2B99:00", "start=soon", "start=9999-12-31"):
        response = client.get(f"/api/planner/week?{query}", headers=alice)
        assert response.status_code == 422, query


# reminders


def test_reminders_appear_inside_the_window(client, alice, add, clock):
    add("essay", days=3)
    assert reminders(client, alice) == []
    clock.advance(days=1, minutes=1)
    [reminder] = reminders(client, alice)
    assert reminder["kind"] == "upcoming"
    assert "essay" in reminder["message"]


def test_dismissing_upcoming_still_allows_overdue(client, alice, add, clock):
    a = add(days=1)
    url = f"/api/assignments/{a['id']}/dismiss-reminder"
    assert client.post(url, headers=alice).status_code == 204
    assert reminders(client, alice) == []
    assert client.post(url, headers=alice).status_code == 409

    clock.advance(days=2)
    assert [r["kind"] for r in reminders(client, alice)] == ["overdue"]
    assert client.post(url, headers=alice).status_code == 204
    assert reminders(client, alice) == []


def test_moving_the_deadline_restores_the_reminder(client, alice, add, clock):
    a = add(days=1)
    url = f"/api/assignments/{a['id']}"
    client.post(url + "/dismiss-reminder", headers=alice)
    # Re-sending the same deadline must not bring the reminder back.
    client.patch(url, headers=alice, json={"due_at": a["due_at"]})
    assert reminders(client, alice) == []
    client.patch(url, headers=alice, json={"due_at": clock.iso(hours=20)})
    assert len(reminders(client, alice)) == 1


def test_per_assignment_lead_time(client, alice, add):
    a = add(days=5)
    url = f"/api/assignments/{a['id']}"
    client.patch(url, headers=alice, json={"reminder_hours": 24 * 7})
    assert len(reminders(client, alice)) == 1
    client.patch(url, headers=alice, json={"reminder_hours": None})
    assert reminders(client, alice) == []


def test_completed_work_has_no_reminder(client, alice, add):
    a = add(days=-1)
    client.patch(
        f"/api/assignments/{a['id']}", headers=alice, json={"completed": True}
    )
    assert reminders(client, alice) == []
