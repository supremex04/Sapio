# Spec: Personalize

Learners tell Sapio who they are and how they like to learn; lessons, Expand / Revision notes, test questions, tutor
answers and test-report feedback are written for it. Sidebar: **Personalize** (`/personalize`), editable any time.
New learners land on it right after creating their account (`/personalize?start=1`: "Tell us about you", with
**Skip for now** and **Save and continue**), so their first lesson is already written for them; then the daily
goal (`/welcome`). Visualizations use the level, occupation, field, depth and preferences, not the goal: they only
explain.

## Rules

| # | Rule |
|---|---|
| PZ-1 | Every field is optional: academic level, occupation, field of study (free text, at most 100 characters), depth, preferences (any of six), goal. Saving replaces the whole profile; clearing everything returns to "not configured". |
| PZ-2 | The profile shapes depth, vocabulary, examples and emphasis. It never changes facts, the SOURCE's scope or how answers are graded: a score means the same for every learner. |
| PZ-3 | Preferences are evidence-compatible: worked examples, analogies, real-world applications, formal maths, code, step-by-step. "Learning styles" (visual / auditory / kinesthetic matching) are deliberately not offered: matching teaching to them does not improve learning (Pashler et al., 2008). |
| PZ-4 | Content is written for the material's owner (study materials are private per learner). |
| PZ-5 | Generated notes record the profile they were written for (a signature); opening them after the profile changed rewrites them. Lessons record it too but are not rewritten: their checks carry the learner's answers and recall cards. Topics opened after a change get the new style. |
| PZ-6 | The free-text field is passed to the AI as quoted data (control characters, quotes and braces removed), never as instructions. |
| PZ-7 | Home invites learners who have not personalised anything ("Make Sapio yours"). |

## API

| Method & path | Body | Response |
|---|---|---|
| `GET /api/me/personalization` | | `{configured, academicLevel, occupation, fieldOfStudy, depth, preferences[], goal, updatedAt}` |
| `PUT /api/me/personalization` | `{academicLevel?, occupation?, fieldOfStudy?, depth?, preferences?, goal?}` | as above; 400 for unknown values or a field longer than 100 |

Values: `academicLevel` SCHOOL, HIGHER_SECONDARY, UNDERGRADUATE, POSTGRADUATE, DOCTORAL, NONE; `occupation`
STUDENT, PROFESSIONAL, EDUCATOR, SELF_LEARNER; `depth` CONCISE, BALANCED, IN_DEPTH; `preferences` EXAMPLES, ANALOGIES,
APPLICATIONS, FORMAL, CODE, STEP_BY_STEP; `goal` EXAM, UNDERSTANDING, WORK, CURIOSITY.

## How it reaches the AI

`StudyAi` specs for lessons, questions, answers, notes and feedback carry `learner` (null = a general audience);
the inference service (`study/learner.py`) turns it into a "Who you are teaching" section of the system prompt:
level-specific guidance (e.g. school: plain words and everyday analogies; postgraduate: assume the fundamentals,
derivations and trade-offs), depth, preferences, goal and the field (as data). Inference contract 1.2.
