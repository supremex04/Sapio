-- M2: study materials (Learn / Test), spaced review, progress & learning analytics.

-- Users: time zone for "local day" analytics, and the learner's daily goal.
ALTER TABLE users
    ADD COLUMN time_zone          VARCHAR(64) NOT NULL DEFAULT 'UTC',
    ADD COLUMN daily_goal_minutes INT         NOT NULL DEFAULT 10;

-- Documents: richer pipeline (PENDING -> EXTRACTING -> ANALYZING -> READY | FAILED).
-- M1 rows are re-run through the new pipeline.
UPDATE documents SET status = 'PENDING' WHERE status IN ('PROCESSING', 'COMPLETED');
ALTER TABLE documents
    ADD COLUMN title      VARCHAR(300),
    ADD COLUMN text_pages INT,
    ADD COLUMN ocr_pages  INT;

-- Extracted text per page run (a single page, or several pages OCR'd together).
CREATE TABLE document_segments
(
    id          UUID PRIMARY KEY,
    document_id UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    position    INT         NOT NULL,
    page_from   INT         NOT NULL,
    page_to     INT         NOT NULL,
    method      VARCHAR(10) NOT NULL, -- text | ocr
    content     TEXT        NOT NULL,
    UNIQUE (document_id, position)
);

-- ---------------------------------------------------------------- study content (shareable)

CREATE TABLE study_materials
(
    id          UUID PRIMARY KEY,
    document_id UUID         NOT NULL UNIQUE REFERENCES documents (id) ON DELETE CASCADE,
    owner_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title       VARCHAR(300) NOT NULL,
    summary     TEXT         NOT NULL,
    language    VARCHAR(8)   NOT NULL,
    kind        VARCHAR(20)  NOT NULL,
    model       VARCHAR(100),
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE TABLE study_chunks
(
    id          UUID PRIMARY KEY,
    material_id UUID         NOT NULL REFERENCES study_materials (id) ON DELETE CASCADE,
    position    INT          NOT NULL,
    heading     VARCHAR(300) NOT NULL,
    content     TEXT         NOT NULL,
    page_from   INT          NOT NULL,
    page_to     INT          NOT NULL,
    UNIQUE (material_id, position)
);

CREATE TABLE study_topics
(
    id              UUID PRIMARY KEY,
    material_id     UUID         NOT NULL REFERENCES study_materials (id) ON DELETE CASCADE,
    position        INT          NOT NULL,
    title           VARCHAR(300) NOT NULL,
    summary         TEXT         NOT NULL,
    key_concepts    TEXT[]       NOT NULL,
    difficulty      VARCHAR(20)  NOT NULL,
    chunk_positions INT[]        NOT NULL,
    UNIQUE (material_id, position)
);

CREATE TABLE study_lessons
(
    id            UUID PRIMARY KEY,
    topic_id      UUID        NOT NULL UNIQUE REFERENCES study_topics (id) ON DELETE CASCADE,
    sections_json TEXT        NOT NULL,
    key_takeaways TEXT[]      NOT NULL,
    model         VARCHAR(100),
    created_at    TIMESTAMPTZ NOT NULL
);

-- One question bank for both modes: lesson checks (lesson_id set) and assessment questions.
CREATE TABLE study_questions
(
    id                     UUID PRIMARY KEY,
    material_id            UUID        NOT NULL REFERENCES study_materials (id) ON DELETE CASCADE,
    topic_id               UUID        NOT NULL REFERENCES study_topics (id) ON DELETE CASCADE,
    lesson_id              UUID REFERENCES study_lessons (id) ON DELETE CASCADE,
    origin                 VARCHAR(20) NOT NULL, -- LESSON_CHECK | ASSESSMENT
    after_section          INT,
    position               INT         NOT NULL,
    type                   VARCHAR(20) NOT NULL,
    prompt                 TEXT        NOT NULL,
    options                TEXT[]      NOT NULL,
    correct_option         INT,
    reference_answer       TEXT,
    explanation            TEXT        NOT NULL,
    source_chunk_positions INT[]       NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_study_questions_lesson ON study_questions (lesson_id);
CREATE INDEX ix_study_questions_topic ON study_questions (topic_id);

-- ---------------------------------------------------------------- learner state

CREATE TABLE topic_progress
(
    id           UUID PRIMARY KEY,
    user_id      UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    topic_id     UUID        NOT NULL REFERENCES study_topics (id) ON DELETE CASCADE,
    status       VARCHAR(20) NOT NULL,
    started_at   TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    updated_at   TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, topic_id)
);

CREATE TABLE assessments
(
    id             UUID PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    material_id    UUID        NOT NULL REFERENCES study_materials (id) ON DELETE CASCADE,
    status         VARCHAR(20) NOT NULL,
    difficulty     VARCHAR(10) NOT NULL,
    question_count INT         NOT NULL,
    score          DOUBLE PRECISION,
    report_json    TEXT,
    created_at     TIMESTAMPTZ NOT NULL,
    completed_at   TIMESTAMPTZ
);
CREATE INDEX ix_assessments_user_material ON assessments (user_id, material_id, created_at DESC);

CREATE TABLE question_attempts
(
    id              UUID PRIMARY KEY,
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    question_id     UUID        NOT NULL REFERENCES study_questions (id) ON DELETE CASCADE,
    context         VARCHAR(20) NOT NULL, -- LESSON | ASSESSMENT | REVIEW
    assessment_id   UUID REFERENCES assessments (id) ON DELETE CASCADE,
    selected_option INT,
    answer_text     TEXT,
    verdict         VARCHAR(20) NOT NULL,
    score           DOUBLE PRECISION NOT NULL,
    confidence      VARCHAR(10),
    feedback        TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_question_attempts_user_question ON question_attempts (user_id, question_id, created_at DESC);

CREATE TABLE assessment_items
(
    id            UUID PRIMARY KEY,
    assessment_id UUID NOT NULL REFERENCES assessments (id) ON DELETE CASCADE,
    position      INT  NOT NULL,
    question_id   UUID NOT NULL REFERENCES study_questions (id) ON DELETE CASCADE,
    attempt_id    UUID REFERENCES question_attempts (id) ON DELETE SET NULL,
    UNIQUE (assessment_id, position)
);

-- Spaced repetition (Leitner boxes 1..5).
CREATE TABLE review_cards
(
    id               UUID PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    question_id      UUID        NOT NULL REFERENCES study_questions (id) ON DELETE CASCADE,
    box              INT         NOT NULL,
    due_at           TIMESTAMPTZ NOT NULL,
    last_reviewed_at TIMESTAMPTZ,
    lapses           INT         NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, question_id)
);
CREATE INDEX ix_review_cards_due ON review_cards (user_id, due_at);

-- ---------------------------------------------------------------- progress & analytics

-- Append-only log of learning actions. Titles are snapshots so the log survives deletions.
CREATE TABLE learning_events
(
    id             UUID PRIMARY KEY,
    user_id        UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type           VARCHAR(40) NOT NULL,
    document_id    UUID,
    document_title VARCHAR(300),
    topic_id       UUID,
    topic_title    VARCHAR(300),
    assessment_id  UUID,
    question_id    UUID,
    verdict        VARCHAR(20),
    confidence     VARCHAR(10),
    xp             INT         NOT NULL,
    local_date     DATE        NOT NULL,
    occurred_at    TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_learning_events_user_time ON learning_events (user_id, occurred_at DESC);

CREATE TABLE user_daily_activity
(
    user_id            UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day                DATE    NOT NULL,
    active_seconds     INT     NOT NULL DEFAULT 0,
    logins             INT     NOT NULL DEFAULT 0,
    lessons_completed  INT     NOT NULL DEFAULT 0,
    questions_answered INT     NOT NULL DEFAULT 0,
    correct_answers    INT     NOT NULL DEFAULT 0,
    xp_earned          INT     NOT NULL DEFAULT 0,
    goal_reached       BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, day)
);

CREATE TABLE user_presence
(
    user_id           UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    last_heartbeat_at TIMESTAMPTZ NOT NULL
);
