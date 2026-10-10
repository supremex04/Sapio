# What Ved can do today

A capability map for product and engineering readers, derived from the code (backend controllers,
frontend routes, inference endpoints) as of 2026-10-10. Details live in the linked specs.

**Status legend:** ✅ available in the UI · 🔌 API only (backend endpoint exists, no UI yet) · 🧪 planned

## At a glance

| Area | Status |
|---|---|
| Accounts & sessions | ✅ |
| Document upload & extraction (PDF, images, native text + OCR) | ✅ |
| Knowledge store: tree, term index, figures, hybrid search | 🔌 (used internally by Learn/Test and the document Preview; tree, search and term endpoints not in the UI) |
| Learn mode: learning path, lessons with checks, AI tutor, figures and source chips | ✅ |
| Test mode: assessments, grading, report | ✅ |
| Spaced review | ✅ |
| Study notes & ask-your-document (Read mode) | ✅ notes (Expand / Revision in the document Preview) · 🔌 ask-your-document, search |
| Gamification & analytics | ✅ |
| Design system (light/dark, Diya v2, `/styleguide`) | ✅ |
| AI vendor-neutrality (providers, per-task routes, fallbacks) | ✅ (config) |
| Operations (`dev.sh`, auto-retries, config) | ✅ |

## Accounts & sessions ✅

- Register as **STUDENT** or **TEACHER** (email, password, full name); `ADMIN` is never self-assigned.
- Login, logout per device, sessions survive reloads for 14 days of inactivity (rotating refresh
  token in an HttpOnly cookie, 15-min JWT access token held in memory only).
- The browser's time zone is stored at registration and kept current by heartbeats, so "today",
  streaks and per-day analytics follow the learner's local day.
- Routes: `/login`, `/register`. Spec: [specs/auth.md](specs/auth.md), [ADR-0002](adr/0002-jwt-access-tokens-with-rotating-refresh-sessions.md).

Roles are recorded but **no feature differs by role yet** (see [Not yet](#not-yet)).

## Document upload & extraction ✅

| | |
|---|---|
| Formats | PDF, PNG, JPEG, WEBP, TIFF (multi-page), BMP; type from extension, verified against magic bytes |
| Limits | 25 MB per file (`ved.documents.max-size-bytes`, inference `parsing.max_upload_bytes`); 200 pages (`parsing.max_pages`) |
| Text | **Hybrid**: PDF pages with a text layer are read natively; only scanned pages and images are OCR'd |
| OCR engines | `rapid` (RapidOCR, CPU, default), `unlimited-remote` (baidu/Unlimited-OCR on SGLang/vLLM, full Markdown), `unlimited-local` (in-process, needs ~8 GB+ VRAM) |
| Pipeline | `PENDING → EXTRACTING → INDEXING → ANALYZING → READY`, async (upload returns 202); live stepper in the library |
| Results | document **Preview** on the study hub: *Reading view* (sections in order, figures in place, contents list), *Original* (the uploaded PDF/image in the browser's viewer, `GET /api/documents/{id}/original`), *Raw text* (rendered / raw Markdown, copy, download as `.md`) |
| Text cleanup | inline HTML from extraction (`<mark>`, `<b>`, `<u>`, `<br>`, comments incl. picture-text blocks) becomes Markdown emphasis or is hidden, slide bullet glyphs (🆃…) are stripped; code is left untouched |
| Failures | readable reason; **Retry** resumes the failed stage; transient AI failures retry automatically (1, 4, 15 min) |

Library UI: `/library` (upload dropzone, pipeline stepper, extracted text). Specs:
[specs/ocr.md](specs/ocr.md), [guides/ocr-engines.md](guides/ocr-engines.md),
[architecture/document-parsing.md](architecture/document-parsing.md).

## Knowledge store 🔌

Every document is parsed **once** into a structure all features reuse
([specs/knowledge.md](specs/knowledge.md), [ADR-0009](adr/0009-knowledge-store-tree-hybrid-index-figures.md),
[architecture/knowledge-storage.md](architecture/knowledge-storage.md), [architecture/retrieval.md](architecture/retrieval.md)).

| Capability | What it does | Status |
|---|---|---|
| Document tree | chapter → section → subsection with the book's own labels (`2.3`), page ranges, small leaf chunks | 🔌 `GET /api/documents/{id}/tree`, `/nodes/{nodeId}` |
| Summaries + key terms | every node summarised bottom-up; key terms form a back-of-book **term index** | 🔌 `GET /api/documents/{id}/terms` |
| Figures | raster images and vector diagrams extracted, decorative ones (logos, backgrounds) dropped, described by a vision model, served via signed URLs; text-only images (terminal output, code) become searchable text transcripts instead | 🔌 `GET /api/figures/{id}/image` |
| Hybrid search | Postgres full-text + pgvector, fused with RRF, small-to-big expansion, figure matches, optional subtree scope | 🔌 `GET /api/documents/{id}/search` |
| Whole-document reading view | every node's text and kept figures in reading order, figures as signed URLs | ✅ `GET /api/documents/{id}/content` (Preview → Reading view) |
| Embedding model tracking | each vector records its model; after a model switch only matching vectors are compared and the document is re-embedded in the background | ✅ (automatic) |

Learn and Test already run on this store: the AI only ever sees retrieved sections and figures,
never the whole document. *Why:* compact, structured context keeps generation grounded and cheap.

## Learn mode ✅

| Capability | What it does | Learning-science rationale |
|---|---|---|
| Learning path (`/documents/:id/learn`) | 3–12 ordered topics built from the tree, shown as a path with per-topic mastery and one recommended node | Mastery learning: advance on mastery, not time |
| Lessons (`/documents/:id/learn/:topicId`) | generated on first open and cached; CONCEPT / EXAMPLE / ANALOGY sections ending in SUMMARY, key takeaways | Concrete examples & dual coding; cognitive load (one section at a time) |
| Warm-up question | a pretest before the first section | Pretesting / generation effect |
| Comprehension checks | multiple choice / true-false between sections, graded server-side with elaborative feedback and optional confidence | Retrieval practice, immediate elaborative feedback, hypercorrection |
| Explain-it-back | a final short answer graded by the AI against the source | Elaboration & self-explanation |
| AI tutor | "Ask the tutor" panel on a lesson, grounded in the topic's sections; says when the document doesn't cover something | Self-explanation for "why" questions |
| Figures in lessons | lessons and tutor answers show document figures inline with caption; click to enlarge | Dual coding |
| Source chips | `[§2.3]` citations render as small source chips (each section once per paragraph) | Grounding: learners can see where a claim comes from |

Spec: [specs/study.md](specs/study.md).

## Test mode ✅

- Setup (`/documents/:id/test`): question count (3–20), difficulty `EASY | MIXED | HARD`, topic selection.
- Questions are grounded in retrieved sections, **interleaved** across topics, and avoid prompts
  from the learner's earlier tests of the same document. *Why:* interleaving improves discrimination and transfer.
- Grading: choice questions deterministic; short answers graded by the AI (score clamped to the verdict band).
- Report (`/assessments/:id`): score, per-topic level (STRONG / DEVELOPING / WEAK), strengths first,
  weak areas with advice, next steps, and **confidence calibration**. *Why:* peak-end rule, growth-mindset feedback, metacognition.
- Falls back to a deterministic narrative if the AI is unavailable when the report is written.

Spec: [specs/study.md](specs/study.md#test-my-knowledge).

## Spaced review ✅

Every first answer (lesson check, test item, review) becomes a Leitner card: boxes 1–5 at 1, 3, 7,
16, 35 days; a miss returns in 10 minutes. `/review` runs an interleaved session across documents;
Home shows "N reviews due". Skipped test items are scheduled like misses.
*Why:* spacing beats massed study; reviews are questions, never re-reading (testing effect).

## Notes ✅ & ask-your-document 🔌

| Capability | Endpoint | Status |
|---|---|---|
| Study notes per section in two styles: **Expand** (in-depth: steps, why and how, plain-language jargon, examples and analogies) and **Revision** (terse key points + a "Quick self-test"); chapter notes by map-reduce over child notes; cite sections, embed figures; generated once per style and cached | `GET /api/documents/{id}/nodes/{nodeId}/notes?style=EXPAND\|REVISION` | ✅ |
| Ask the whole document (or a subtree): long-form, cited answer with sources and figures | `POST /api/documents/{id}/ask` | 🔌 |

Notes appear in the document **Preview** (study hub → Preview → Reading view), on **study units** once the course is
`READY`. A unit is the highest section whose whole subtree has 300–6,000 characters of text (`ved.knowledge.reading`):
each passage belongs to at most one unit, the buttons say what they cover ("Covers 1–1.1.3 · pp. 1–5"), and while a
rewrite is shown it stands in for the unit's subsections. Bigger chapters are split into their sections; tiny
fragments are covered by no unit of their own. The rest
of **Read mode** (tree navigation, search, term index, ask the document) is **not built yet**; it is the next frontend
milestone ([roadmap.md](roadmap.md)). The tutor inside lessons is the only ask-style UI today.

## Gamification & analytics ✅

| Capability | Details | Rationale |
|---|---|---|
| XP & levels | fixed rules per learning action (checks, test answers, topic/test completion, reviews, daily goal) | modest, secondary rewards (overjustification risk) |
| Streaks | consecutive local days with XP or ≥ 5 active minutes; today never shows "broken" | habit formation without loss-aversion anxiety |
| Daily goal | 5/10/15/20/30 minutes, picked at onboarding (`/welcome`), ring on Home | implementation intentions, goal-gradient |
| One next step | `GET /api/study/next-step`: reviews due → continue topic → next topic → test → upload | choice overload / defaults |
| Analytics | logins per day, active time per day (visible-tab heartbeats every 30 s), learning-event log, 14-day chart and recent activity on Home | data for future teacher dashboards |

Spec: [specs/progress.md](specs/progress.md).

## Design system ✅

- **Diya v2**: one hue per job, the signature solid "edge" depth, characters, meaningful short motion
  that respects `prefers-reduced-motion`.
- Complete **light and dark** token sets; theme `light | dark | system`, applied before first paint.
- Living reference at **`/styleguide`** in the running app.

Spec: [specs/design-system.md](specs/design-system.md).

## AI vendor-neutrality ✅

- Providers are configured, not coded: **Gemini** and any **OpenAI-compatible** API (OpenAI,
  OpenRouter, Groq, Together, vLLM, LM Studio, Ollama).
- Each AI task (`outline`, `lesson`, `questions`, `grade`, `feedback`, `answer`, `notes`, `figures`,
  `summaries`) can route to its own model with ordered **fallbacks across vendors**; an exhausted
  quota switches to the next model at once instead of waiting.
- Embeddings are configurable (768 dimensions); figures are embedded from the image when the
  provider is multimodal, otherwise from caption + description.
- All model output is validated (references, figure ids, citations, answer keys) before use.

Guide: [guides/configuration.md](guides/configuration.md), [ADR-0010](adr/0010-vendor-neutral-model-configuration.md).

## Operations ✅

- `./dev.sh` installs what changed and starts Postgres (pgvector, port 5433), inference, backend and
  frontend; also `-d`, `stop`, `down`, `status`, `logs`, `setup`, `test`.
- Resilience: provider retries honour server hints (`Retry-After`, `RetryInfo`), model fallbacks,
  automatic document retries (`ved.documents.processing.auto-retry-delays`), resumable, idempotent
  pipeline stages; unfinished documents resume after a backend restart.
- Every tuning knob lives in `inference/settings.yaml` or `backend/src/main/resources/application.yml`,
  overridable by environment variables; the backend checks at startup that the embedding size fits the database.

Guides: [guides/local-development.md](guides/local-development.md), [guides/configuration.md](guides/configuration.md),
[architecture/backend-inference-integration.md](architecture/backend-inference-integration.md).

## Not yet

| Gap | Status |
|---|---|
| Frontend **Read mode** (tree navigation, search, term index, ask the document) | 🧪 next |
| Teacher features: classes & sharing, teacher analytics, personalised assignments, exams | 🧪 later |
| Role-specific behaviour (teacher vs student), admin user management | 🧪 |
| Per-user AI quotas and cost logging; login rate limiting, password reset, refresh-token reuse detection | 🧪 |
| Streaming lesson generation (SSE), pre-generating the first lesson | 🧪 |
| Material versioning, DB-backed job queue for > 1 backend instance, S3/MinIO storage | 🧪 |
| Achievements, class challenges, learning-effectiveness dashboard, real-time notifications | 🧪 |

Full list: [roadmap.md](roadmap.md). Specs: [auth](specs/auth.md) · [ocr](specs/ocr.md) ·
[knowledge](specs/knowledge.md) · [study](specs/study.md) · [progress](specs/progress.md) ·
[design system](specs/design-system.md) · [inference API](specs/inference-api.md) · [API conventions](specs/api-conventions.md).
