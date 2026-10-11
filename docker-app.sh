#!/usr/bin/env bash
# Start and manage Ved's Docker Compose app.
#
#   ./docker-app.sh          start in the background
#   ./docker-app.sh build    rebuild images and start
#   ./docker-app.sh stop     stop containers, keeping their data
#   ./docker-app.sh status   show container status
#   ./docker-app.sh logs     follow logs

set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$ROOT/.env.docker"
COMPOSE_FILE="$ROOT/docker-compose.app.yml"

if [[ ! -f "$ENV_FILE" ]]; then
  printf 'Missing %s. Copy .env.docker.example to .env.docker and add your settings.\n' "$ENV_FILE" >&2
  exit 1
fi

if ! command -v docker >/dev/null 2>&1; then
  printf 'Docker is not installed or is not on PATH.\n' >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  printf 'Docker is not running. Start Docker Desktop, then run this script again.\n' >&2
  exit 1
fi

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

action="${1:-up}"
case "$action" in
  up)
    compose up -d
    printf '\nVed is starting. Open http://localhost:5173\n'
    ;;
  build)
    compose up -d --build
    printf '\nVed is starting. Open http://localhost:5173\n'
    ;;
  stop|down)
    compose down
    ;;
  status|ps)
    compose ps
    ;;
  logs)
    shift || true
    compose logs -f "$@"
    ;;
  *)
    printf 'Usage: %s [up|build|stop|status|logs]\n' "${BASH_SOURCE[0]}" >&2
    exit 2
    ;;
esac
