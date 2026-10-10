# Local development

## Prerequisites

| Tool | Version used | Notes |
|---|---|---|
| Java (JDK) | 21 | `java -version` |
| Maven | 3.8+ | |
| Node.js | 22 | `npm` 10+ |
| Python | 3.12 | exactly 3.12 (RapidOCR / pinned ML deps) |
| Docker | with Compose v2 | PostgreSQL + Testcontainers |

## Quick start: `./dev.sh`

One script installs what's needed and runs everything:

```bash
./dev.sh            # first run installs everything; later runs just start (Ctrl+C stops all)
```

What it does, in order:

1. Checks prerequisites (Java 21+, Maven, Node, Python 3.12, a running Docker).
2. **Installs only what changed.** It remembers a hash of each dependency manifest in `.dev/stamps/`:
   `inference/pyproject.toml` → `pip install -e '.[rapid,embed,dev]'`, `frontend/package*.json` → `npm ci`,
   `backend/pom.xml` → Maven dependency resolution. Unchanged manifests are skipped, so a normal start
   takes a few seconds. After a `git pull` that changes dependencies, the next `./dev.sh` updates them.
3. Creates `inference/.env` from `.env.example` if missing and stops with a message if the Gemini key is empty.
4. Starts PostgreSQL (docker compose, port 5433) and waits until it is healthy.
5. Starts inference (:8000, auto-reload), backend (:8080) and frontend (:5173), waits for each health
   check (printing the log tail if one fails), then streams all logs with a coloured prefix.

| Command | Use |
|---|---|
| `./dev.sh` | install if needed + run in the foreground; Ctrl+C stops every service |
| `./dev.sh -d` | same, in the background |
| `./dev.sh setup` | force a full reinstall (first time on a machine, or to repair a broken env) |
| `./dev.sh status` | what is running and healthy |
| `./dev.sh logs [inference\|backend\|frontend]` | follow logs (files in `.dev/logs/`) |
| `./dev.sh stop` | stop the services (database keeps running) |
| `./dev.sh down` | stop services and the database container (data stays in the volume) |
| `./dev.sh test` | run all three test suites |

**`--local-embedding`** (combine with `./dev.sh`, `up` or `-d`): computes embeddings in-house
(Qwen3-Embedding-0.6B on the CPU, no API calls) instead of the Gemini API, by starting inference with
`VED_INFERENCE_CONFIG=settings.yaml,settings.local-embedding.yaml`
([overlay](configuration.md#in-house-embeddings-provider-type-onnx)). Gemini stays the default. The startup summary
prints the active embedding model. If inference is already running in the other mode, the script warns and keeps it
running: `./dev.sh stop` first to switch. Switching either way re-embeds each document in the background the next
time it is searched (the model, 614 MB, downloads on first use to `~/.cache/huggingface`).

The rest of this guide describes the manual steps the script automates, for when you need one
service on its own (e.g. running the backend from your IDE).

## Manual setup

```bash
# Database (PostgreSQL 17 on localhost:5433, db/user/password: ved)
docker compose up -d

# Inference service
cd inference
python3.12 -m venv .venv
.venv/bin/pip install -e '.[rapid,dev]'      # add ',local' only on a big-GPU machine
cp .env.example .env                          # then set VED_INFERENCE_GEMINI_API_KEY (required for study features)

# Frontend
cd ../frontend && npm install
```

The backend downloads its dependencies on first build.

## Manual run (three terminals)

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
| `learner@ved.dev` | `demo-password-1` | STUDENT (has a studied biology handout, a test and a report) |

`docker compose down -v` wipes the database (and these accounts).

## Test

```bash
cd backend   && mvn verify          # unit (*Test) + integration (*IT, needs Docker)
cd inference && .venv/bin/pytest    # add -m "not slow" to skip the real OCR model test (no test calls the real LLM)
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
The Gemini key (`VED_INFERENCE_GEMINI_API_KEY`) goes only in `inference/.env`, which is gitignored.
Without it the inference service refuses to start (by design: study features can't work without an LLM).

Frontend: `VITE_BACKEND_URL` (dev proxy target, default `http://localhost:8080`),
`VITE_API_BASE` (default `/api`).

## Troubleshooting

- **`./dev.sh`: "port … is used by another process"**: something else (often a manually started
  server) holds the port. Stop it, or `./dev.sh stop` if it was started by the script.
- **`./dev.sh`: a dependency seems stale**: run `./dev.sh setup` to force a reinstall.
- **Port 5432 already in use**: compose maps Postgres to **5433** on purpose. Keep it.
- **Document stuck in "Queued"/"Processing"**: check that the inference service is running
  (`curl localhost:8000/health`). Restarting the backend re-queues unfinished documents.
- **"The AI service is busy right now"**: Gemini returned 429/5xx after retries and all fallback
  models. Wait a moment and retry; check the provider key in `inference/.env` and the model routes in
  `inference/settings.yaml` ([configuration.md](configuration.md)).
- **Document FAILED during "Building study guide"**: use *Retry* in the library. Extraction is not repeated.
- **Logged out after every reload**: the refresh cookie is `Secure` only if
  `VED_COOKIE_SECURE=true`. Over plain http://localhost it must stay `false`.
