---
title: Ved Learning Platform
emoji: 📚
colorFrom: blue
colorTo: indigo
sdk: docker
app_port: 7860
---

# Ved

An interactive learning platform. Students learn and practise; teachers track progress and
assign personalised tasks, homework and exams, powered by analytics and AI.

**Status:** M3. Upload any educational document and Ved turns it into a guided course:
**Learn** with an AI tutor, **Test my knowledge** with grounded questions and a report, spaced review,
XP, streaks and learning analytics. Every design decision is grounded in learning science
([docs/architecture/learning-science.md](docs/architecture/learning-science.md)).

| Folder | What | Stack |
|---|---|---|
| [`backend/`](backend) | Public REST API, auth, data, job orchestration | Java 21, Spring Boot 4, PostgreSQL, Flyway |
| [`inference/`](inference) | Internal AI/ML service (extraction + LLM study content) | Python 3.12, FastAPI, PyMuPDF, RapidOCR / Unlimited-OCR, Gemini or any OpenAI-compatible LLM |
| [`frontend/`](frontend) | Web UI | TypeScript, React 19, Vite, Motion, Lucide |
| [`docs/`](docs) | Architecture, specs, ADRs, guides | Markdown + Mermaid |

## Quick start



**Requirement:** Bash 4.0 or higher.

```bash
./docker-app.sh          # installs what's missing or changed, starts everything → http://localhost:5173
```

The first run installs all dependencies and creates `inference/.env`: put your Gemini key in it
(`VED_INFERENCE_GEMINI_API_KEY`) and run again. Later runs only start the services, and reinstall a
service's dependencies only when its manifest changed. `./dev.sh help` lists the other commands
(`-d`, `stop`, `status`, `logs`, `setup`, `test`).

Full guide: [docs/guides/local-development.md](docs/guides/local-development.md).
Start reading at [docs/README.md](docs/README.md).

To run the full stack in Docker, follow [docs/guides/docker.md](docs/guides/docker.md). The
Docker setup and its change log are documented there.

## Configuration

AI vendors, models per task (with fallbacks), embeddings, OCR engine and every tuning knob are
configuration, not code: `inference/settings.yaml` and `backend/src/main/resources/application.yml`,
both overridable by environment variables; keys stay in `inference/.env`. See
[docs/guides/configuration.md](docs/guides/configuration.md). What the platform can do today:
[docs/capabilities.md](docs/capabilities.md).
