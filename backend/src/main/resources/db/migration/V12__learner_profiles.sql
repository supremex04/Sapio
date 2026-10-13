-- Personalisation: who the learner is and how they like to learn. Lessons, notes, tests and the tutor are written
-- for it; grading never depends on it. Generated notes remember the profile they were written for, so they are
-- rewritten when it changes.
CREATE TABLE learner_profiles
(
    user_id        UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    academic_level VARCHAR(20),
    occupation     VARCHAR(20),
    field_of_study VARCHAR(100),
    depth          VARCHAR(20),
    preferences    TEXT[]      NOT NULL DEFAULT '{}',
    goal           VARCHAR(20),
    updated_at     TIMESTAMPTZ NOT NULL
);

ALTER TABLE study_notes ADD COLUMN profile_signature VARCHAR(40);
ALTER TABLE study_lessons ADD COLUMN profile_signature VARCHAR(40);
