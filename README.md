# Study Planner

A study planner for keeping track of courses, assignments and deadlines. It has a
Python REST API (FastAPI + SQLite) and a desktop app written in Java (JavaFX).

## Features

- User accounts, or use it as a guest (saved on your computer)
- Courses and assignments with due dates and estimated hours
- "Next up" list that ranks what to work on
- Weekly view showing how many hours to do each day
- Reminders for upcoming and overdue work

## Requirements

- Windows
- JDK 17+
- Python 3.10+ (only needed for accounts)

## Running it

If PowerShell blocks the scripts, run this first:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

Start the server (skip this for guest mode):

```powershell
.\run-server.ps1
```

Then start the app in another window:

```powershell
.\study-gui.ps1
```

The first run downloads JavaFX and builds the app. After changing the Java code,
rebuild with:

```powershell
.\client\build.ps1
```

Java may print some warnings about JavaFX. They can be ignored.

## How the ranking works

Overdue assignments always come first. Everything else gets a score out of 100:

```text
available = days until due * study hours per day
pressure  = hours left / available          (max 1)
closeness = 1 / (1 + days until due / 3)
score     = 100 * (0.6 * pressure + 0.4 * closeness)
```

An assignment is "at risk" if pressure is 1 or more, "tight" if it is at least
0.5, and "on track" otherwise.

For the weekly view, the hours left on each assignment are split evenly over the
days from today until it's due.

## Guest mode

Guest data is saved in `.studyplanner/guest.json` in your home folder. It is
separate from account data and isn't encrypted.

Guest mode doesn't use the server, so the planning logic is written twice:
`backend/app/planner.py` and `LocalRules.java`. Changes need to go in both.

## API

Interactive docs are at http://127.0.0.1:8000/docs while the server is running.

| Endpoint | Description |
| --- | --- |
| `POST /api/auth/register`, `/login`, `/logout` | accounts |
| `GET`, `PATCH /api/me` | settings |
| `GET`, `POST /api/courses` | list / add courses |
| `PATCH`, `DELETE /api/courses/{id}` | edit / delete a course |
| `GET`, `POST /api/assignments` | list / add assignments |
| `GET`, `PATCH`, `DELETE /api/assignments/{id}` | one assignment |
| `POST /api/assignments/{id}/log` | log hours |
| `POST /api/assignments/{id}/dismiss-reminder` | dismiss a reminder |
| `GET /api/planner/next` | ranking |
| `GET /api/planner/week` | weekly workload |
| `GET /api/reminders` | reminders |

## Tests

Backend tests:

```powershell
cd backend
```

```powershell
.venv\Scripts\python -m pip install -r requirements-dev.txt
```

```powershell
.venv\Scripts\python -m pytest
```

## Limitations / TODO

- No password reset
- No way to move guest data into an account
- No pop-up notifications, reminders only show while the app is open
- No database migrations
- Meant to be run locally (would need HTTPS to host it)
