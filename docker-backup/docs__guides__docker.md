# Run all of Ved with Docker Compose

This setup builds and runs the web UI, Java API, Python inference service, and PostgreSQL with
pgvector. It is separate from the existing `docker-compose.yml`, which `dev.sh` uses for the
database during local development.

## Start Ved

From the repository root:

```bash
cp .env.docker.example .env.docker
```

Edit `.env.docker` and set `VED_INFERENCE_GEMINI_API_KEY` to enable AI generation and embeddings.
Then build and start the stack:

```bash
docker compose --env-file .env.docker -f docker-compose.app.yml up --build
```

Open <http://localhost:5173>. The API is also reachable at <http://localhost:8080>; the UI sends
`/api` requests through its Nginx container to the backend. On first start, the backend applies
database migrations. The inference service uses CPU RapidOCR by default.

Useful commands (include the same `--env-file` and `-f` options):

```bash
docker compose --env-file .env.docker -f docker-compose.app.yml ps
docker compose --env-file .env.docker -f docker-compose.app.yml logs -f backend
docker compose --env-file .env.docker -f docker-compose.app.yml down
```

The database and uploaded document files persist in named Docker volumes. To delete both volumes
and all stored app data, use `down -v`.

## Build services separately

```bash
docker build -t ved-inference ./inference
docker build -t ved-backend ./backend
docker build -t ved-frontend ./frontend
```

Compose is generally easier because the services need to reach one another by their Compose names
(`postgres`, `inference`, and `backend`).

## Configuration and deployment

The sample environment values are only for local demos. Before exposing Ved beyond your machine,
set unique strong values for `VED_DB_PASSWORD`, `VED_INFERENCE_API_KEY`, `VED_JWT_SECRET` (at least
32 characters), and `VED_FIGURE_URL_SECRET`, and serve the UI over HTTPS with secure cookies. Keep
`.env.docker` private. For a production deployment, also configure TLS and a managed backup plan
for both named volumes.
