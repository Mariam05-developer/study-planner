# Ranking, weekly workload and reminders.
# Guest mode in the app does the same thing in LocalRules.java, so if you change
# something here change it there too.
from datetime import timedelta
from math import fsum


def remaining_hours(assignment):
    if assignment.completed:
        return 0.0
    left = assignment.estimated_hours - assignment.hours_spent
    # if it isn't done there is at least a bit of work left
    return round(max(left, 0.5), 2)


def format_hours(hours):
    return f"{round(hours, 1):g}h"


def format_duration(delta):
    seconds = abs(delta.total_seconds())
    if seconds >= 48 * 3600:
        amount = int(seconds // 86400)
        unit = "day"
    elif seconds >= 3600:
        amount = int(seconds // 3600)
        unit = "hour"
    else:
        amount = max(int(seconds // 60), 1)
        unit = "minute"
    if amount != 1:
        unit += "s"
    return f"{amount} {unit}"


# what to work on next


def score_assignment(assignment, daily_hours, now):
    """Returns (score, risk, reason).

    Overdue assignments get more than 100 so they are always first. Otherwise
    the score is 60% how much of the available study time the work needs, and
    40% how close the deadline is.
    """
    left = remaining_hours(assignment)
    time_left = assignment.due_at - now

    if time_left <= timedelta(0):
        days_late = -time_left.total_seconds() / 86400
        score = 101 + min(days_late, 99)
        reason = (
            f"Overdue by {format_duration(time_left)}; "
            f"about {format_hours(left)} of work left"
        )
        return round(score, 1), "overdue", reason

    days = time_left.total_seconds() / 86400
    available = days * daily_hours
    pressure = left / available
    closeness = 1 / (1 + days / 3)
    score = 100 * (0.6 * min(pressure, 1.0) + 0.4 * closeness)

    if pressure >= 1:
        risk = "at_risk"
    elif pressure >= 0.5:
        risk = "tight"
    else:
        risk = "on_track"

    reason = (
        f"Due in {format_duration(time_left)}; {format_hours(left)} left "
        f"vs about {format_hours(available)} of study time"
    )
    return round(score, 1), risk, reason


def rank_assignments(assignments, user, now):
    """List of (score, risk, reason, assignment), most urgent first."""
    ranked = []
    for a in assignments:
        if not a.completed:
            score, risk, reason = score_assignment(a, user.daily_study_hours, now)
            ranked.append((score, risk, reason, a))

    # highest score first, ties go to the earlier due date then the bigger task
    ranked.sort(key=lambda r: (-r[0], r[3].due_at, -remaining_hours(r[3]), r[3].id))
    return ranked


# weekly workload


def monday_of(day):
    return day - timedelta(days=day.weekday())


def week_workload(assignments, user, week_start, zone, now):
    """One dict per day of the week.

    The work left on each assignment is split evenly between today and its due
    date. Overdue work all goes on today.
    """
    today = now.astimezone(zone).date()
    assignments = sorted(assignments, key=lambda a: (a.due_at, a.id))
    result = []

    for i in range(7):
        day = week_start + timedelta(days=i)
        due = []
        plan = []
        hours = []

        for a in assignments:
            due_day = a.due_at.astimezone(zone).date()
            left = remaining_hours(a)
            info = {"assignment_id": a.id, "title": a.title, "course_code": a.course_code}

            if due_day == day:
                due.append(
                    {**info, "due_at": a.due_at, "remaining_hours": left,
                     "completed": a.completed}
                )
            if a.completed:
                continue

            if a.due_at <= now:
                last_day = today
            else:
                last_day = max(due_day, today)

            if today <= day <= last_day:
                per_day = left / ((last_day - today).days + 1)
                hours.append(per_day)
                plan.append({**info, "hours": round(per_day, 2)})

        # fsum avoids floating point error when adding the hours up
        planned = fsum(hours)
        result.append(
            {
                "date": day,
                "is_today": day == today,
                "planned_hours": round(planned, 2),
                "capacity_hours": user.daily_study_hours,
                "overloaded": planned > user.daily_study_hours + 0.000001,
                "due": due,
                "plan": plan,
            }
        )
    return result


# reminders


def reminder_stage(assignment, user, now):
    """'overdue', 'upcoming' or None."""
    if assignment.completed:
        return None
    if assignment.due_at <= now:
        return "overdue"

    hours_before = assignment.reminder_hours
    if hours_before is None:
        hours_before = user.reminder_hours
    if assignment.due_at - now <= timedelta(hours=hours_before):
        return "upcoming"
    return None


def active_reminder(assignment, user, now):
    """Returns (kind, message), or None if there is nothing to show."""
    stage = reminder_stage(assignment, user, now)
    if stage is None:
        return None
    # dismissing the "upcoming" one shouldn't hide the "overdue" one later
    if assignment.dismissed_stage in (stage, "overdue"):
        return None

    gap = format_duration(assignment.due_at - now)
    if stage == "overdue":
        when = f"is overdue by {gap}"
    else:
        when = f"is due in {gap}"
    left = format_hours(remaining_hours(assignment))
    return stage, f"'{assignment.title}' {when} ({left} of work left)"
