# Classroom: teachers and their students

Teachers run a class on Sapio: they invite students, share documents, set tests, follow progress and results,
and post notices and calendar events. Design decisions: [ADR-0015](../adr/0015-classroom.md). Backend:
`com.sapio.classroom` (plus `com.sapio.notification`); frontend: `features/classroom`.

## Who sees what

- A **teacher** account (role `TEACHER`, chosen at sign-up) gets the classroom instead of the learner's
  gamification: no XP, streaks, recall or activity chart; Home is the class overview and the right rail the class
  calendar. Teachers can still read documents and use Explore.
- A teacher sees a student's work **only on what the teacher gave them**: the documents the teacher shared (the
  student's copies) and every test on those copies (the teacher's class tests and the student's own practice
  tests). Their private uploads are never visible. Anything else answers `404`.
- Teacher endpoints refuse other roles with `403 ACCESS_DENIED`.

## Roster

Invite by email (up to 100 at once). The student accepts or declines from their **notifications**; someone
without an account sees the invitation once they sign up with that address. Outcomes per address: `INVITED`,
`ALREADY_INVITED`, `ALREADY_A_STUDENT`, `NOT_A_STUDENT_ACCOUNT` (a teacher's address), `INVALID_EMAIL`, `YOURSELF`.
A declined invitation can be sent again. Removing a student (or a student leaving) ends the teacher's access;
what the student received stays in their library.

## Sharing documents

Sharing a ready document gives each chosen student (or every student) an **independent copy** in their library,
tagged "From your teacher" (`origin.kind = CLASS`). Their notes, lessons and progress are their own; edits the
teacher makes afterwards are not pushed to existing copies. A class share links copy and original; the teacher's
library card and the document's Students tab read from it: shared with, opened by (the student's study hub
reports opening a class copy), tests completed, average score, and per student the topics done, mastery per topic
and their tests.

## Class tests

1. **Create**: on a ready document, choose a title, the question format (`OBJECTIVE`: multiple choice and
   true/false; `SUBJECTIVE`: short written answers graded by the AI against a model answer; `MIXED`: both),
   difficulty and 3-30 questions. One AI call drafts questions over every topic (inference `format`, contract
   1.3); nothing is sent yet.
2. **Review** (status `DRAFT`): edit any question (topic, type, wording, options and the correct one, model answer,
   explanation), delete questions, add your own. Validated: choice questions need 2-6 non-empty options (true/false
   exactly 2) and a correct option; written questions a model answer.
3. **Send** to all or chosen students, with an optional due date. Students without the document get it first.
   Each student receives an ordinary **assessment on their copy**, marked with the test's title, the teacher and
   the due date: the usual runner (answers sealed until the end), grading and report. Once sent the questions
   are fixed; sending again reaches only students who do not have it yet.
4. **Results**: per student (not started, in progress n/m, completed with score), the class average, lowest and
   highest score, the class average per topic (weakest first, from the reports), and each student's full report
   (read-only for the teacher).

## Insights

- **Student profile**: progress on every shared document, all their tests on them, the average score, and
  **topics to work on**: practice mastery under 50 %, or rated weak in a test report.
- **Home overview**: students (and pending invitations), documents shared, class tests sent, tests completed;
  average, lowest and highest score over every completed test on shared documents; students needing attention
  (average under 50 %, with the topics their latest report flagged); the latest results.

## Notices and calendar

A notice goes to every student's notifications and the Notices page. Calendar events (title, optional details,
start, optional end, all-day) appear in each student's calendar, in the right rail and on the Calendar page,
together with the due dates of their unfinished assigned tests; students are notified of new events.

## Notifications

A per-user inbox (bell in the sidebar or the mobile top bar), polled every minute: invitations (with Accept /
Decline), invitation answers (teachers), shared documents, assigned tests, notices and events. Opening the panel
marks them read.

## API (`/api`, authenticated)

| Endpoint | |
|---|---|
| `GET /notifications`, `POST /notifications/read` | inbox `{unread, items}`; mark all read |
| `GET /classroom/overview` | teacher home |
| `GET /classroom/students`, `POST /classroom/students/invite {emails}`, `DELETE /classroom/students/members/{id}` | roster |
| `GET /classroom/students/{studentId}` | student profile |
| `GET /classroom/assessments/{assessmentId}` | a student's test with its report (teacher, shared documents only) |
| `GET /classroom/documents/stats`, `POST /classroom/documents/{id}/share {studentIds}`, `GET /classroom/documents/{id}/students` | sharing |
| `GET /classroom/tests?documentId=`, `POST /classroom/documents/{id}/tests`, `GET/PUT/DELETE /classroom/tests/{id}` | class tests |
| `POST /classroom/tests/{id}/questions`, `PUT/DELETE /classroom/tests/{id}/questions/{qid}` | editing a draft |
| `POST /classroom/tests/{id}/send {studentIds, dueAt}`, `GET /classroom/tests/{id}/results` | sending, results |
| `GET/POST /classroom/notices`, `DELETE /classroom/notices/{id}` | notices (GET: the student's teachers' notices for students) |
| `GET /classroom/calendar?from&to`, `POST /classroom/events`, `PUT/DELETE /classroom/events/{id}` | calendar (up to a year per request) |
| `GET /classroom/invitations`, `POST /classroom/invitations/{id}/accept\|decline` | the student's invitations |
| `GET /classroom/teachers`, `DELETE /classroom/teachers/{id}`, `POST /classroom/opened/{documentId}` | the student's teachers, leaving, "opened" |

Schema: `V14__classroom.sql`.
