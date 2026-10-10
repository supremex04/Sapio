# Ved — Technical Documentation

Ved is an interactive learning platform. Students learn and submit work; teachers follow their
progress and assign personalised tasks, homework and exams, driven by analytics and AI.

**Current milestone (M3):** upload any educational document → it is parsed into a navigable knowledge store
(tree, figures, hybrid search) and becomes a guided course with
**Learn** (AI tutor, comprehension checks) and **Test my knowledge** (grounded assessments + report),
plus spaced review, XP/levels/streaks, daily goals and learning analytics.

## Contents

| Area | Document | Read it when… |
|---|---|---|
| Product | [capabilities.md](capabilities.md) | you want to know what Ved can do today (UI vs API-only vs planned) |
| Architecture | [architecture/overview.md](architecture/overview.md) | you need the big picture: services, data flow, data model |
| | [architecture/conventions.md](architecture/conventions.md) | you write code: layering, SOLID in practice, how to add a feature |
| | [architecture/learning-science.md](architecture/learning-science.md) | you design anything learner-facing: the evidence behind every UX decision |
| | [architecture/document-parsing.md](architecture/document-parsing.md) | you work on extraction: layout, OCR, tree building, figures |
| | [architecture/knowledge-storage.md](architecture/knowledge-storage.md) | you work on the tree, chunks, figures, term index and their tables |
| | [architecture/retrieval.md](architecture/retrieval.md) | you work on hybrid search, context building or embeddings |
| | [architecture/backend-inference-integration.md](architecture/backend-inference-integration.md) | you change how the backend calls the inference service (batching, retries, failures) |
| Specs | [specs/auth.md](specs/auth.md) | working on sign-up / login / sessions |
| | [specs/ocr.md](specs/ocr.md) | working on document upload & text extraction |
| | [specs/knowledge.md](specs/knowledge.md) | working on the document tree, figures, search, notes, ask |
| | [specs/study.md](specs/study.md) | working on Learn / Test / review / study material |
| | [specs/progress.md](specs/progress.md) | working on XP, streaks, goals, analytics |
| | [specs/design-system.md](specs/design-system.md) | building UI: tokens, components, motion, theming |
| | [specs/api-conventions.md](specs/api-conventions.md) | adding or consuming any public REST endpoint |
| | [specs/inference-api.md](specs/inference-api.md) | changing the backend ↔ Python contract |
| Decisions | [adr/](adr/README.md) | you wonder *why* something is the way it is |
| Guides | [guides/local-development.md](guides/local-development.md) | setting up, running and testing locally |
| | [guides/configuration.md](guides/configuration.md) | switching AI vendors/models, OCR engines, or tuning retrieval, budgets and processing |
| | [guides/ocr-engines.md](guides/ocr-engines.md) | switching OCR engines / running Unlimited-OCR on a GPU |
| Planning | [roadmap.md](roadmap.md) | picking up the next piece of work; known limitations |

## Keeping docs alive

- A change to an API contract updates the matching spec **in the same commit**.
- A significant or hard-to-reverse choice gets an ADR (copy [adr/template.md](adr/template.md)).
- Specs describe *what the system does now*; future ideas go to [roadmap.md](roadmap.md).
