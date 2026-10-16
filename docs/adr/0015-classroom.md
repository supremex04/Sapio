# 0015. Classroom: copies for students, class tests as ordinary assessments

- Status: Accepted
- Date: 2026-10-16

## Context

Teachers had the same experience as students. They need to run a class: invite students, give them documents,
set tests, see progress and results, find who needs help on which topic, and post notices and events.

## Decision

- **One roster per teacher**, invitation by email, accepted by the student. No class groups yet: a handful of
  students per teacher is the expected size; groups can be added on top of memberships later.
- **Sharing gives each student an independent copy** (the existing `DocumentCloner`, origin `CLASS`), not shared
  access to the teacher's document. Personalisation rewrites notes and lessons per learner profile, and progress,
  notes and edits are per document, so one shared document would thrash between students. A `class_shares` row
  links copy and original; everything the teacher sees is read through it. Trade-off: the teacher's later edits do
  not reach existing copies.
- **Class tests are drafted, reviewed, then materialised as ordinary assessments** on each student's copy. The
  teacher's questions are stored once (with topics by learning-path position and sources by tree path, both
  identical in every copy) and copied into each student's assessment on sending. Students therefore get the usual
  runner, sealed answers, AI grading, report, recall cards and XP, and the teacher reads the same reports. The AI
  question call gained an optional `format` (objective, subjective, mixed), enforced after generation too.
- **Privacy by construction**: every teacher query starts from the teacher's class shares, class tests and
  accepted memberships, so a teacher only reaches what they gave the student: lesson progress on shared documents
  and results of their class tests. Tests a student sets themselves, even on a shared document, stay private, and
  are left out of the mastery the teacher sees.
- **Notifications** are a small generic inbox (`notifications` table) any module can post to, polled by the
  client; no push channel yet.
- **Teachers do not get gamification** (XP, streaks, recall, activity): their Home is class analytics and their
  rail the calendar.

## Consequences

- No AI cost per student when sending a test: one AI call drafts it; grading written answers costs as usual.
- Deleting a copy (student) or removing a student (teacher) ends visibility without deleting anyone's work.
- Insights rely on existing signals (mastery from practice, report levels); a richer model (trends over time,
  per-question difficulty) can be added from the same data.
- Later: class groups, re-syncing a copy with the teacher's latest version, exams with time limits, real-time
  notifications.
