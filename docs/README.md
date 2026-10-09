# Ved — Technical Documentation

Ved is an interactive learning platform. Students learn and submit work; teachers follow their
progress and assign personalised tasks, homework and exams, driven by analytics and AI.

**Current milestone (M1):** user accounts and sessions, plus a document-to-text (OCR) tool.

## Contents

| Area | Document | Read it when… |
|---|---|---|
| Architecture | [architecture/overview.md](architecture/overview.md) | you need the big picture: services, data flow, data model |
| | [architecture/conventions.md](architecture/conventions.md) | you write code: layering, SOLID in practice, how to add a feature |
| Specs | [specs/auth.md](specs/auth.md) | working on sign-up / login / sessions |
| | [specs/ocr.md](specs/ocr.md) | working on document upload & conversion |
| | [specs/api-conventions.md](specs/api-conventions.md) | adding or consuming any public REST endpoint |
| | [specs/inference-api.md](specs/inference-api.md) | changing the backend ↔ Python contract |
| Decisions | [adr/](adr/README.md) | you wonder *why* something is the way it is |
| Guides | [guides/local-development.md](guides/local-development.md) | setting up, running and testing locally |
| | [guides/ocr-engines.md](guides/ocr-engines.md) | switching OCR engines / running Unlimited-OCR on a GPU |
| Planning | [roadmap.md](roadmap.md) | picking up the next piece of work; known limitations |

## Keeping docs alive

- A change to an API contract updates the matching spec **in the same commit**.
- A significant or hard-to-reverse choice gets an ADR (copy [adr/template.md](adr/template.md)).
- Specs describe *what the system does now*; future ideas go to [roadmap.md](roadmap.md).
