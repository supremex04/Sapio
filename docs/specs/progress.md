# Spec: Progress, gamification & learning analytics

Status: **implemented (M2)** · Code: `backend/.../progress`, `frontend/src/features/progress`

## Purpose

Keep learners motivated (XP, levels, streaks) and record learning analytics (logins per day,
active time per day, learning activity). Teacher dashboards will build on the same data.

## Requirements

| ID | Requirement |
|---|---|
| PRG-1 | Count logins per user per local day (password login and registration). |
| PRG-2 | Measure active time per local day from client heartbeats sent only while the tab is visible and the user interacted in the last 60 s. |
| PRG-3 | Record every learning action as an event (type, document, topic, assessment, question, verdict, XP). |
| PRG-4 | Award XP by fixed rules; total XP determines the level. |
| PRG-5 | Streak = consecutive local days with learning activity (XP earned) or ≥ 5 active minutes. Today not yet active does not break the streak. |
| PRG-6 | Days are computed in the user's time zone (IANA id from the browser; default UTC). |
| PRG-7 | The learner sets a **daily goal** in minutes (5, 10, 15, 20, 30; default 10); the summary reports progress toward it. |

## XP rules

| Event | XP |
|---|---|
| Lesson check answered correctly (first correct attempt per check) | 10 |
| Assessment answer: correct / partial | 10 / 5 |
| Topic completed (first time) | 50 |
| Assessment completed | 25, +25 if score ≥ 80 % |
| Spaced review answered: correct / partial | 5 / 2 |
| Daily goal reached (once per day) | 20 |

Levels: reaching level *n* needs `50 · n · (n − 1)` XP in total (L2 = 100, L3 = 300, L4 = 600, L5 = 1000, …).

## Active time

The client sends `POST /api/progress/heartbeat` every **30 s** while the user is active. The server
credits `min(now − lastHeartbeat, 30 s)` when the previous heartbeat was ≤ 120 s ago; the first
heartbeat of a session credits nothing. This caps abuse and needs no client-side clock.

## API

`POST /api/progress/heartbeat` `{ "timeZone": "Asia/Kathmandu" }` → **204**

`GET /api/progress/summary` → **200**

```jsonc
{
  "xp": { "total": 420, "level": 3, "levelStartXp": 300, "nextLevelXp": 600 },
  "streak": { "current": 4, "longest": 9, "activeToday": true },
  "goal": { "dailyMinutes": 10, "todayMinutes": 21, "reached": true },
  "today": { "date": "2026-10-09", "activeSeconds": 1260, "logins": 1, "lessonsCompleted": 1,
             "questionsAnswered": 12, "correctAnswers": 9, "xpEarned": 140 },
  "totals": { "activeSeconds": 54000, "lessonsCompleted": 14, "questionsAnswered": 210,
              "correctAnswers": 160, "assessmentsCompleted": 6 }
}
```

`PUT /api/progress/goal` `{ "dailyMinutes": 15 }` → **200** `{ "dailyMinutes": 15 }` (allowed: 5, 10, 15, 20, 30)

`GET /api/progress/daily?days=14` (1–366) → **200** `{ "days": [DailyActivity…] }`, oldest first, one
entry per day including zero days. `DailyActivity` has the same fields as `today`.

`GET /api/progress/activity?limit=20` → **200** `{ "items": [ { "type", "documentId", "documentTitle", "topicTitle", "xp", "occurredAt" } ] }`.
Recent learning events, newest first.

## Data model

| Table | Purpose |
|---|---|
| `learning_events` | append-only log of learning actions (+ XP awarded); the analytics source of truth |
| `user_daily_activity` | per user per local day counters (active seconds, logins, lessons, answers, XP), upserted |
| `user_presence` | last heartbeat timestamp (for active-time crediting) |

Learning modules publish `LearningActivityEvent`s; auth publishes `UserLoggedInEvent`. The
progress module consumes them in the same transaction (see ADR-0008). It never calls back into
the publishers.
