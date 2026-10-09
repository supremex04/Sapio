# 0007. One study material for both modes; content separate from learner state

- Status: Accepted
- Date: 2026-10-09

## Context

Learn and Test must use "the same processed document and underlying content representation".
Teachers will later share materials with whole classes, so content can't be tied to one learner.

## Decision

- After extraction, a document becomes a **study material**: ~3.5k-character **chunks** (with page
  ranges) organised into ordered **topics** that reference their chunks. Lessons, checks,
  assessment questions, grading and the tutor all receive only the relevant topic's chunks, so
  everything is grounded in the same representation.
- **Content** tables (`study_materials`, `study_chunks`, `study_topics`, `study_lessons`,
  `study_questions`) are shared. **Learner state** (`topic_progress`, `question_attempts`,
  `assessments`, `review_cards`) is per user.
- One question bank serves lesson checks, assessments and spaced review.
- Lessons are generated lazily on first open and cached; questions are generated per assessment
  (avoiding prompts the learner has already seen).
- Authorisation goes through one class (`MaterialAccess`); today it checks ownership.

## Consequences

- Sharing a material with a class later means changing `MaterialAccess`, not the schema.
- Content can be regenerated (new model, better prompts) without touching learner history.
- Re-analysing a document replaces its material; attempts on old questions go with it (cascade).
  Acceptable while materials are private; versioning materials is a roadmap item.
