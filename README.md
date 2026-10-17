# Sapio

> "While the world races to create smarter AI, we intend to make *smarter humans*."

**Your own material, turned into a course built for you: taught, tested, visualized, and shared with your
teacher.**

Learners have more study material than ever (PDFs, slides, scans, recorded lectures) and spend their time
re-reading it, one of the least effective ways to learn. One explanation is pitched at everyone, hard ideas stay
flat on the page, and teachers cannot see who is struggling until the exam. Sapio takes a learner's own material
and runs the whole study loop on it: lessons, checks, tests, spaced recall and mastery, written for that learner,
cited to the page they came from, and visible to their teacher.

## What makes Sapio different

1. **A personal learning path.** Any document becomes a structured course: topics in learning order, short lessons
   that open with a warm-up question, checks, interleaved tests, spaced recall and per-topic mastery, pitched at the
   learner's level, field and depth. Not a chat box that waits for questions.
2. **Hard ideas, visualized.** Narrated animations, 3D molecules and proteins, programs run line by line, and
   electric circuits solved exactly. The AI plans and explains; for molecules, proteins, programs and circuits,
   what is shown is computed or confirmed by exact tools (a chemistry toolkit, the Protein Data Bank's structure
   files, a real run of the program, a circuit solver), and animation frames are checked for layout before the
   learner sees them.
3. **Teachers and students, connected.** Teachers share notes and AI-drafted tests they review and edit; each
   student gets their own copy on their own path; results, mastery per topic and who needs help on what come
   straight back to the teacher.

Underneath all three: **grounded** answers. Every lesson, note and tutor reply cites the section of the learner's
document it came from, and the design follows well-replicated learning science
([docs/architecture/learning-science.md](docs/architecture/learning-science.md)).

## Features

**Learn from anything**
- PDF, Word, images and scans (native text plus OCR), lecture audio and video (speech-to-text, then AI notes)
- Documents read into a tree of sections, figures described and searchable, summaries and a search index

**Study**
- A study guide of topics in learning order
- Lessons with a warm-up, concept, example and analogy sections, checks and "explain it back"
- An AI tutor scoped to the topic, answering from the learner's own document
- Tests (objective, written or mixed), graded with explanations, confidence ratings and a report
- Spaced recall cards (Leitner schedule) and recency-weighted mastery per topic
- Expand and Revision notes for every study unit
- Personalize: level, field, depth and preferences shape every lesson, note and test

**Visualize**
- Narrated animations (Manim) with a local voice and subtitles
- 3D molecules (RDKit) with the named groups highlighted, and protein structures from the Protein Data Bank
- Python programs traced line by line: variables, call stack and output from a real run
- DC circuits solved exactly: live values, current flowing, the response over time

**Classroom**
- Invite students by email; share documents (a personal copy each)
- Class tests drafted by the AI, reviewed and edited by the teacher, sent with a due date
- Results per student and per topic, mastery, and who needs attention on which topics
- Notices, a class calendar and notifications

**Motivation and community**
- A daily goal, forgiving streaks, XP and levels tied to learning actions; one recommended next step on Home
- Share documents to Explore; others import their own copy; votes and comments

## Tech stack

| Part | Stack |
|---|---|
| Web app | React 19, TypeScript, Vite; KaTeX, 3Dmol.js, Motion |
| Backend | Java 21, Spring Boot 4, PostgreSQL 17 (pgvector, ltree), Flyway; a modular monolith |
| AI service | Python 3.12, FastAPI; Gemini (generation and multimodal embeddings) behind a provider-agnostic port; RapidOCR; faster-whisper |
| Renderer | Manim CE, RDKit, Kokoro-82M voice, NumPy and SciPy, a sandboxed Python tracer; no secrets and no internet |
| Cloud | Google Cloud Run, Cloud SQL, Cloud Storage, Secret Manager, Cloud Build |

Search is hybrid (keyword plus meaning, fused by rank) inside PostgreSQL. AI-written code runs only in the sealed
renderer. 359 automated tests and 18 architecture decision records
([docs/adr](docs/adr)). Architecture: [docs/architecture/overview.md](docs/architecture/overview.md).

| Folder | What |
|---|---|
| [`frontend/`](frontend) | the web app |
| [`backend/`](backend) | public REST API, accounts, data, job orchestration |
| [`inference/`](inference) | internal AI service: parsing, OCR, speech, study content |
| [`renderer/`](renderer) | internal visualization service (runs in Docker) |
| [`docs/`](docs) | architecture, specs, decisions, guides |

## How to run

**Prerequisites:** Java 21, Maven 3.8+, Node.js 22, Python 3.12, Docker with Compose v2, Bash 4+, and a Gemini API
key.

```bash
./dev.sh          # installs what is missing, starts everything: http://localhost:5173
```

The first run installs all dependencies and creates `inference/.env`: put your Gemini key in it
(`SAPIO_INFERENCE_GEMINI_API_KEY`) and run `./dev.sh` again. Then open http://localhost:5173 and create an
account (a learner, or a teacher for the classroom).

| Command | Use |
|---|---|
| `./dev.sh -d` | run in the background |
| `./dev.sh status` | what is running |
| `./dev.sh logs` | follow the logs |
| `./dev.sh stop` | stop the services |
| `./dev.sh test` | run the test suites |

More: [local development](docs/guides/local-development.md), [configuration](docs/guides/configuration.md)
(models per task, fallbacks, OCR engine), [deploying to Google Cloud](docs/guides/deploy-google-cloud.md), and
[what Sapio can do today](docs/capabilities.md).
