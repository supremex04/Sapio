# Local development

## Prerequisites

| Tool | Version used | Notes |
|---|---|---|
| Java (JDK) | 21 | `java -version` |
| Maven | 3.8+ | |
| Node.js | 22 | `npm` 10+ |
| Python | 3.12 | exactly 3.12 (RapidOCR / pinned ML deps) |
| Docker | with Compose v2 | PostgreSQL + Testcontainers |

## One-time setup

```bash
# Database (PostgreSQL 17 on localhost:5433, db/user/password: ved)
docker compose up -d

# Inference service
cd inference
python3.12 -m venv .venv
.venv/bin/pip install -e '.[rapid,dev]'      # add ',local' only on a big-GPU machine
cp .env.example .env                          # optional, defaults work locally

# Frontend
cd ../frontend && npm install
```

The backend downloads its dependencies on first build.

## Run (three terminals)

```bash
# 1. Inference  → http://localhost:8000  (GET /health)
cd inference && .venv/bin/uvicorn ved_inference.main:app --port 8000 --reload

# 2. Backend    → http://localhost:8080  (Flyway migrates the DB on start)
cd backend && mvn spring-boot:run

# 3. Frontend   → http://localhost:5173  (proxies /api to :8080)
cd frontend && npm run dev
```

Open http://localhost:5173 and create an account.

**Local test accounts** (created during development; they exist only in your local DB volume):

| Email | Password | Role |
|---|---|---|
| `demo@ved.dev` | `demo-password-1` | STUDENT |
| `teacher@ved.dev` | `demo-password-1` | TEACHER |

`docker compose down -v` wipes the database (and these accounts).

## Test

```bash
cd backend   && mvn verify          # unit (*Test) + integration (*IT, needs Docker)
cd inference && .venv/bin/pytest    # add -m "not slow" to skip the real OCR model test
cd inference && .venv/bin/ruff check src tests && .venv/bin/ruff format --check src tests
cd frontend  && npm run typecheck && npm test && npm run build
```

## Configuration

Backend (`backend/src/main/resources/application.yml`, override with env vars):

| Env var | Default | Purpose |
|---|---|---|
| `VED_DB_URL` / `VED_DB_USERNAME` / `VED_DB_PASSWORD` | `jdbc:postgresql://localhost:5433/ved`, `ved`, `ved` | database |
| `VED_JWT_SECRET` | dev value | **must** be set in any shared environment (≥ 32 random bytes) |
| `VED_COOKIE_SECURE` | `false` | `true` behind HTTPS |
| `VED_CORS_ORIGINS` | `http://localhost:5173` | only needed when UI and API are on different origins |
| `VED_STORAGE_DIR` | `./var/storage` | uploaded files |
| `VED_INFERENCE_URL` | `http://localhost:8000` | inference service |
| `VED_INFERENCE_API_KEY` | `dev-internal-key` | must equal the inference service's key |

Inference: see [`inference/.env.example`](../../inference/.env.example) and [ocr-engines.md](ocr-engines.md).

Frontend: `VITE_BACKEND_URL` (dev proxy target, default `http://localhost:8080`),
`VITE_API_BASE` (default `/api`).

## Troubleshooting

- **Port 5432 already in use**: compose maps Postgres to **5433** on purpose. Keep it.
- **Document stuck in "Queued"/"Processing"**: check that the inference service is running
  (`curl localhost:8000/health`). Restarting the backend re-queues unfinished documents.
- **Logged out after every reload**: the refresh cookie is `Secure` only if
  `VED_COOKIE_SECURE=true`. Over plain http://localhost it must stay `false`.
