# Ved

An interactive learning platform. Students learn and practise; teachers track progress and
assign personalised tasks, homework and exams, powered by analytics and AI.

**Status:** M1. Accounts and sessions, plus a document-to-Markdown OCR tool.

| Folder | What | Stack |
|---|---|---|
| [`backend/`](backend) | Public REST API, auth, data, job orchestration | Java 21, Spring Boot 4, PostgreSQL, Flyway |
| [`inference/`](inference) | Internal AI/ML service (OCR) | Python 3.12, FastAPI, PyMuPDF, RapidOCR / Unlimited-OCR |
| [`frontend/`](frontend) | Web UI | TypeScript, React 19, Vite |
| [`docs/`](docs) | Architecture, specs, ADRs, guides | Markdown + Mermaid |

## Quick start

```bash
docker compose up -d                                                    # PostgreSQL on :5433
(cd inference && python3.12 -m venv .venv && .venv/bin/pip install -e '.[rapid,dev]')
(cd frontend && npm install)

cd inference && .venv/bin/uvicorn ved_inference.main:app --port 8000    # terminal 1
cd backend && mvn spring-boot:run                                       # terminal 2
cd frontend && npm run dev                                              # terminal 3 → http://localhost:5173
```

Full guide: [docs/guides/local-development.md](docs/guides/local-development.md).
Start reading at [docs/README.md](docs/README.md).
