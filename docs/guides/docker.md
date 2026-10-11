# Docker setup and change log

This is the main reference for running Ved in Docker and tracking its Docker setup. Update the
**Change log** whenever Docker files or their configuration change.

## Docker layout

There is one Dockerfile at the repository root. Hugging Face Spaces detects a root file named
exactly `Dockerfile`; it will not automatically detect a custom name such as `root_DOCKERFILE`.
The root README contains the Space metadata: `sdk: docker` and `app_port: 7860`.

The Dockerfile has three targets used by local Compose and one final `space` target:

| Target/service | Purpose | Local access |
|---|---|---|
| `postgres` Compose service | PostgreSQL 17 with pgvector | Internal to Compose |
| `inference` target | FastAPI, document parsing, CPU RapidOCR, Gemini calls | Internal on port 8000 |
| `backend` target | Spring Boot API | `http://localhost:8080` |
| `frontend` target | React app served by Nginx | `http://localhost:5173` |
| `space` target | Nginx, backend, inference, and PostgreSQL in one container | Space port 7860 |

`docker-compose.app.yml` builds the local app service images from the root Dockerfile targets and
runs them with a separate Postgres container. The existing `docker-compose.yml` remains the
database-only setup used by `dev.sh`.

## Main Dockerfile: `./Dockerfile`

This is the Dockerfile Hugging Face builds. It is at the repository root and must be named exactly
`Dockerfile`. It uses multi-stage builds: local Compose builds one target per service, while
Hugging Face builds the final all-in-one `space` target.

| Dockerfile stage | What it does | Edit it when… |
|---|---|---|
| `frontend-build` | Installs the locked npm dependencies and runs `npm run build`. | Frontend build commands or Node version change. |
| `backend-build` | Resolves Maven dependencies and packages the Spring Boot JAR. | Java/Maven version or backend packaging changes. |
| `inference-build` | Installs the Python project plus CPU RapidOCR. | Python version or inference dependencies change. |
| `inference` | Runtime image for FastAPI; copies Python packages and `settings.yaml`. | Inference runtime files or startup command change. |
| `backend` | Runtime image for the JAR; creates the `ved` user and storage directory. | Backend runtime settings or storage location change. |
| `frontend` | Nginx runtime image for static assets and the local `/api/` proxy. | Local frontend serving/proxy configuration changes. |
| `space` | Combines the built frontend, JAR, Python environment, PostgreSQL/pgvector, Nginx, and Supervisor. | Hugging Face image packages, paths, environment defaults, or exposed port change. |

The first three stages are build-only stages. `docker-compose.app.yml` selects one of the
`inference`, `backend`, or `frontend` runtime targets per service. Hugging Face uses the final
`space` stage by default. To build just one local service image manually, run one of these from the
repository root:

```bash
docker build --target inference -t ved-inference .
docker build --target backend -t ved-backend .
docker build --target frontend -t ved-frontend .
```

When a dependency changes, update its real manifest as well as any related Dockerfile build step:
`frontend/package.json` and `frontend/package-lock.json`, `backend/pom.xml`, or
`inference/pyproject.toml`. Keep the runtime image's shared libraries aligned with inference
dependencies in the `space` stage. Keep the `space` stage's `COPY` paths aligned with the project
layout and its `EXPOSE 7860` aligned with `app_port: 7860` in the root README.

The all-in-one target gets runtime secrets from Hugging Face Space Settings. Do not add real keys
to Dockerfile `ENV` or `ARG` instructions. The Space entrypoint checks that the Gemini, database,
internal API, JWT, and figure URL secrets are present before starting.

### How to create and run the container

For the normal local app with four containers, create `.env.docker`, add the Gemini key, then start it in the background with:

```bash
./docker-app.sh build
```

After the first build, start it again with `./docker-app.sh`. The helper also supports
`./docker-app.sh stop`, `./docker-app.sh status`, and `./docker-app.sh logs`. Open
<http://localhost:5173> while the services are running.

To build and run the single-container image that will be used by Hugging Face:

```bash
docker build -t ved-space .
docker run --rm -p 7860:7860 --env-file .env.docker \
  -e VED_COOKIE_SECURE=false -v ved-space-data:/data ved-space
```

Open <http://localhost:7860>. The named volume stores the Space image's PostgreSQL data and
uploads between local container runs. The `VED_COOKIE_SECURE=false` override is only for this
local HTTP check; the Space image defaults it to `true` for HTTPS.

## Run locally with Compose

From the repository root:

```bash
cp .env.docker.example .env.docker
```

Put your Gemini key in `.env.docker` as `VED_INFERENCE_GEMINI_API_KEY`, then build and start:

```bash
./docker-app.sh build
```

Open <http://localhost:5173>. Nginx proxies `/api/` to the backend. Services use Compose DNS names
such as `postgres`, `inference`, and `backend`; `localhost` inside a container refers to itself.

Useful commands:

```bash
./docker-app.sh status
./docker-app.sh logs backend
./docker-app.sh stop
```

`down` retains the database and uploaded files. `down -v` deletes both volumes and their data.

## Deploy to Hugging Face Spaces

1. Create a Space on Hugging Face, choose the **Docker** SDK, and initially keep it private while
   setting it up. Spaces uses Git repositories and rebuilds when commits are pushed. See the
   [Spaces overview](https://huggingface.co/docs/hub/spaces-overview) and [Docker Spaces
   guide](https://huggingface.co/docs/hub/spaces-sdks-docker).

2. In the Space **Settings**, add the secrets listed below before pushing the app files.

3. Clone the Space repository, then copy the Ved working tree into that clone. Replace the source
   path with the location of this Ved repository:

   ```bash
   git clone https://huggingface.co/spaces/<username>/<space-name> ved-space
   cd ved-space
   rsync -a --exclude='.git/' --exclude='.DS_Store' --exclude='.dev/' \
     --exclude='docker-backup/' --exclude='.env.docker' --exclude='**/.env' \
     --exclude='**/node_modules/' --exclude='**/.venv/' --exclude='**/target/' \
     /path/to/ved/ ./
   ```

4. Review the copied files. Confirm the root contains `Dockerfile` and the README front matter
   includes `sdk: docker` and `app_port: 7860`. Ensure no real `.env` or API key was copied.

5. Commit and push to the Space repository:

   ```bash
   git add -A
   git commit -m "Deploy Ved Docker Space"
   git push
   ```

   Hugging Face builds the final `space` stage from the root Dockerfile and exposes port 7860.
   Do not use Compose as the Space entry point; Spaces runs one container, while this root
   Dockerfile starts the services together inside it.

6. Watch the Space **Build logs** and **Logs**. When it reports Running, open the Space app URL.
   The Hugging Face account needs permission to create/run Docker Spaces; current account and
   hardware requirements are listed in the [Spaces overview](https://huggingface.co/docs/hub/spaces-overview).

In the Space Settings, add these as **Secrets**:

| Secret | Purpose |
|---|---|
| `VED_INFERENCE_GEMINI_API_KEY` | Gemini API key for content generation and embeddings. RapidOCR itself needs no key. |
| `VED_INFERENCE_API_KEY` | Shared internal secret used by backend and inference |
| `VED_DB_PASSWORD` | Password for the PostgreSQL instance inside the Space container |
| `VED_JWT_SECRET` | Unique JWT signing key, at least 32 characters |
| `VED_FIGURE_URL_SECRET` | Unique figure URL signing key |

The image starts PostgreSQL with pgvector inside the Space container. Its database and uploaded
files are under `/data`. Space container disk is ephemeral; attach persistent storage at `/data`
and verify it supports PostgreSQL database files and locking before relying on it. For durable
production data, prefer a host designed for Compose and managed PostgreSQL. See [Hugging Face
storage documentation](https://huggingface.co/docs/hub/main/spaces-storage).

The root Dockerfile is the main build file to edit when changing Java, Python, Node, PostgreSQL,
Nginx, or the build stages. Runtime process management is in `docker/space-supervisord.conf`,
startup and database initialization are in `docker/space-entrypoint.sh`, and the Space reverse
proxy is in `docker/space-nginx.conf`. Keep the corresponding local Compose target references in
`docker-compose.app.yml` aligned when changing target names. The backend startup wrapper waits for
the internal database before launching Spring Boot.

## Environment and keys

RapidOCR runs locally in the inference service as an installed Python package. No OCR API key is
needed. Gemini is used by the inference service, not directly by the backend. `VED_INFERENCE_API_KEY`
is a separate shared credential for backend-to-inference calls.

`.env.docker.example` gives development defaults for local Compose. `.env.docker` is ignored by
Git. Never commit real keys or production secrets. Replace the sample secrets before making a
Space public, and use HTTPS-secure cookie settings as configured for the Space target.

## Change log

### 2026-10-11 — Added Docker Compose helper

- Added `docker-app.sh` to start, rebuild, stop, inspect, and follow logs for the local Compose app.

### 2026-10-10 — Initial full-stack Compose setup

- Added full-stack `docker-compose.app.yml`, preserving the database-only `docker-compose.yml` for
  `dev.sh`.
- Added service image builds, Nginx API proxy, persistent local volumes, and an example env file.

### 2026-10-10 — Consolidated Dockerfiles and added Space target

- Consolidated service image builds into the root `Dockerfile` with `inference`, `backend`, and
  `frontend` targets; Compose now builds those targets and the three per-service Dockerfiles were
  removed.
- Added the final `space` target, running Nginx, Spring Boot, FastAPI, and PostgreSQL with pgvector
  in one container, with the UI exposed on port 7860.
- Added Supervisor, startup, and Nginx configuration under `docker/`.
- Added a backend start wrapper so Spring Boot waits for the internal database to accept connections.
- Added Docker Space metadata to the root README and documented Space secrets and storage limits.
- Expanded this guide with a stage-by-stage root Dockerfile walkthrough and manual target build commands.
- Excluded root `.env` and `.env.docker` files from Docker build contexts.
- Removed baked-in database and internal API secret defaults from the Space image; its entrypoint
  now requires runtime secrets.
- Added a named UID 1000 user to the Space image because PostgreSQL initialization requires a
  corresponding system user.
- Added initialization for the `ved` application database before Supervisor starts the backend.
- Built the `space` image locally and confirmed PostgreSQL migrations, inference, backend, Nginx,
  and the frontend page start successfully together.
- Initialize the `ved` database before Supervisor starts the backend.
