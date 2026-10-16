-- Classroom: teachers, their students, shared documents, class tests, notices and a calendar
-- (docs/adr/0015-classroom.md). A teacher sees a student's work only on what the teacher gave them.

-- ---------------------------------------------------------------- roster
-- An invitation by email; the student (once they have an account with that email) accepts or declines it.
CREATE TABLE class_memberships
(
    id            UUID PRIMARY KEY,
    teacher_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    student_email VARCHAR(320) NOT NULL, -- normalised (User.normalizeEmail)
    student_id    UUID REFERENCES users (id) ON DELETE CASCADE, -- set when the student answers
    status        VARCHAR(16)  NOT NULL, -- PENDING | ACCEPTED | DECLINED
    invited_at    TIMESTAMPTZ  NOT NULL,
    responded_at  TIMESTAMPTZ,
    UNIQUE (teacher_id, student_email)
);
CREATE INDEX ix_class_memberships_email ON class_memberships (student_email);
CREATE INDEX ix_class_memberships_student ON class_memberships (student_id);

-- ---------------------------------------------------------------- notifications (any module may post)
CREATE TABLE notifications
(
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       VARCHAR(32)  NOT NULL,
    title      VARCHAR(300) NOT NULL,
    body       TEXT,
    link       VARCHAR(500),
    actor_name VARCHAR(200),
    created_at TIMESTAMPTZ  NOT NULL,
    read_at    TIMESTAMPTZ
);
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at DESC);

-- ---------------------------------------------------------------- documents shared with students
-- Each student gets their own copy (DocumentCloner); this links it back to the teacher's document.
CREATE TABLE class_shares
(
    id                 UUID PRIMARY KEY,
    teacher_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    source_document_id UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    student_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    copy_document_id   UUID REFERENCES documents (id) ON DELETE SET NULL, -- null once the student deleted it
    shared_at          TIMESTAMPTZ NOT NULL,
    first_opened_at    TIMESTAMPTZ,
    last_opened_at     TIMESTAMPTZ,
    UNIQUE (source_document_id, student_id)
);
CREATE INDEX ix_class_shares_teacher ON class_shares (teacher_id);
CREATE INDEX ix_class_shares_copy ON class_shares (copy_document_id);

-- ---------------------------------------------------------------- class tests
-- Written (AI-drafted, teacher-reviewed) on the teacher's document; on sending, each student gets an ordinary
-- assessment on their copy, so the test runner, grading and report are the usual ones.
CREATE TABLE class_tests
(
    id          UUID PRIMARY KEY,
    teacher_id  UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    document_id UUID         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    title       VARCHAR(200) NOT NULL,
    format      VARCHAR(16)  NOT NULL, -- OBJECTIVE | SUBJECTIVE | MIXED
    difficulty  VARCHAR(16)  NOT NULL,
    status      VARCHAR(16)  NOT NULL, -- DRAFT | SENT
    due_at      TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL,
    sent_at     TIMESTAMPTZ
);
CREATE INDEX ix_class_tests_document ON class_tests (document_id);

CREATE TABLE class_test_questions
(
    id               UUID PRIMARY KEY,
    test_id          UUID         NOT NULL REFERENCES class_tests (id) ON DELETE CASCADE,
    position         INT          NOT NULL,
    topic_position   INT          NOT NULL, -- the topic in the learning path (copies keep the order)
    topic_title      VARCHAR(400) NOT NULL,
    type             VARCHAR(20)  NOT NULL,
    prompt           TEXT         NOT NULL,
    options          TEXT[]       NOT NULL DEFAULT '{}',
    correct_option   INT,
    reference_answer TEXT,
    explanation      TEXT         NOT NULL DEFAULT '',
    source_paths     TEXT[]       NOT NULL DEFAULT '{}' -- tree paths ("2.3"): the same in every copy
);
CREATE INDEX ix_class_test_questions_test ON class_test_questions (test_id, position);

CREATE TABLE class_test_assignments
(
    id            UUID PRIMARY KEY,
    test_id       UUID        NOT NULL REFERENCES class_tests (id) ON DELETE CASCADE,
    student_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    assessment_id UUID REFERENCES assessments (id) ON DELETE SET NULL,
    assigned_at   TIMESTAMPTZ NOT NULL,
    UNIQUE (test_id, student_id)
);
CREATE INDEX ix_class_test_assignments_assessment ON class_test_assignments (assessment_id);

-- An assigned test is an ordinary assessment that says who set it.
ALTER TABLE assessments ADD COLUMN title VARCHAR(200);
ALTER TABLE assessments ADD COLUMN assigned_by VARCHAR(200);
ALTER TABLE assessments ADD COLUMN due_at TIMESTAMPTZ;

-- Where a copy came from: EXPLORE (imported by the learner) or CLASS (shared by their teacher).
ALTER TABLE documents ADD COLUMN origin_kind VARCHAR(16);
UPDATE documents SET origin_kind = 'EXPLORE' WHERE imported_at IS NOT NULL;

-- ---------------------------------------------------------------- notices and calendar
CREATE TABLE class_notices
(
    id         UUID PRIMARY KEY,
    teacher_id UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title      VARCHAR(200) NOT NULL,
    body       TEXT         NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_class_notices_teacher ON class_notices (teacher_id, created_at DESC);

CREATE TABLE class_events
(
    id          UUID PRIMARY KEY,
    teacher_id  UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title       VARCHAR(200) NOT NULL,
    description TEXT,
    starts_at   TIMESTAMPTZ  NOT NULL,
    ends_at     TIMESTAMPTZ,
    all_day     BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_class_events_teacher ON class_events (teacher_id, starts_at);
