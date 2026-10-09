# 0008. Progress and analytics via in-transaction domain events

- Status: Accepted
- Date: 2026-10-09

## Context

XP, streaks, daily counters and the learning-event log must reflect every learning action without
the study module knowing about gamification, and must never double-count.

## Decision

- `study` publishes `LearningActivityEvent` and `auth` publishes `UserLoggedInEvent` through
  Spring's `ApplicationEventPublisher`. Each event carries snapshot titles for the activity feed.
- `progress` consumes them with synchronous `@EventListener`s **inside the publisher's
  transaction**: the action, its XP and its counters commit or roll back together.
- XP rules live only in `progress.XpPolicy`. Facts only the publisher knows (e.g. "first correct
  attempt") travel in the event.
- Daily counters are updated with atomic `INSERT … ON CONFLICT DO UPDATE` upserts, so concurrent
  requests can't lose increments. Days are computed in the user's time zone.
- Active time comes from client heartbeats, credited server-side (≤ 30 s per beat, gaps > 120 s ignored).

## Consequences

- The study code stays free of gamification; new consumers (teacher dashboards, notifications)
  subscribe to the same events.
- A bug in progress code fails the learning action. Acceptable: the logic is small and
  well-tested. If consumers get heavier, move them to `@TransactionalEventListener(AFTER_COMMIT)`
  with an outbox.
- `learning_events` is the raw analytics source; `user_daily_activity` is a pre-aggregated read model.
