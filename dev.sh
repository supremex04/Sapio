#!/usr/bin/env bash
# Ved local development runner: one command to install (when needed) and run everything.
#
#   ./dev.sh              install what changed, start db + inference + backend + frontend, stream logs
#                         (Ctrl+C stops everything)
#   ./dev.sh -d | up -d   same, but run in the background
#   ./dev.sh setup        force a full (re)install of all dependencies, then exit
#   ./dev.sh stop         stop services started by this script (database keeps running)
#   ./dev.sh down         stop services and the database container
#   ./dev.sh status       show what is running
#   ./dev.sh logs [name]  follow logs (inference | backend | frontend; default all)
#   ./dev.sh test         run all test suites
#
# Options (combine with up / -d):
#   --local-embedding     compute embeddings in-house (Qwen3-Embedding on CPU, no API calls) instead of the
#                         Gemini API: loads inference/settings.local-embedding.yaml over settings.yaml
#
# Dependencies are reinstalled automatically when their manifest changes
# (inference/pyproject.toml, frontend/package*.json, backend/pom.xml): a hash of each is
# remembered in .dev/stamps. See docs/guides/local-development.md.

set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEV_DIR="$ROOT/.dev"
LOG_DIR="$DEV_DIR/logs"
PID_DIR="$DEV_DIR/pids"
STAMP_DIR="$DEV_DIR/stamps"
SERVICES=(inference backend frontend)

declare -A PORT=([inference]=8000 [backend]=8080 [frontend]=5173)
declare -A HEALTH=(
  [inference]="http://localhost:8000/health"
  [backend]="http://localhost:8080/actuator/health"
  [frontend]="http://localhost:5173/"
)
declare -A COLOR=([inference]=35 [backend]=34 [frontend]=32)
# Seconds to wait for a service to become healthy (the backend compiles on first start).
declare -A TIMEOUT=([inference]=90 [backend]=240 [frontend]=60)

# ----------------------------------------------------------------------------- output helpers

if [[ -t 1 ]]; then BOLD=$'\e[1m'; DIM=$'\e[2m'; RED=$'\e[31m'; GREEN=$'\e[32m'; YELLOW=$'\e[33m'; RESET=$'\e[0m'
else BOLD=""; DIM=""; RED=""; GREEN=""; YELLOW=""; RESET=""; fi

step() { printf '%s==>%s %s\n' "$BOLD" "$RESET" "$*"; }
info() { printf '    %s\n' "$*"; }
ok()   { printf '    %s✓%s %s\n' "$GREEN" "$RESET" "$*"; }
warn() { printf '    %s!%s %s\n' "$YELLOW" "$RESET" "$*"; }
die()  { printf '%serror:%s %s\n' "$RED" "$RESET" "$*" >&2; exit 1; }

# ----------------------------------------------------------------------------- prerequisites

PYTHON=""

find_python() {
  for candidate in python3.12 python3; do
    if command -v "$candidate" >/dev/null && "$candidate" -c 'import sys; sys.exit(sys.version_info[:2] != (3, 12))' 2>/dev/null; then
      PYTHON="$candidate"; return
    fi
  done
  die "Python 3.12 is required (found: $(python3 --version 2>&1 || echo none))"
}

check_prerequisites() {
  step "Checking prerequisites"
  local missing=()
  for tool in java mvn node npm docker curl; do command -v "$tool" >/dev/null || missing+=("$tool"); done
  ((${#missing[@]} == 0)) || die "missing tools: ${missing[*]} (see docs/guides/local-development.md)"
  java -version 2>&1 | grep -qE 'version "(2[1-9]|[3-9][0-9])' || die "Java 21+ is required"
  find_python
  docker info >/dev/null 2>&1 || die "Docker is not running"
  ok "java, maven, node, python 3.12, docker"
}

# ----------------------------------------------------------------------------- dependency installs

hash_files() { cat "$@" 2>/dev/null | sha256sum | cut -d' ' -f1; }

# needs_install <name> <must-exist-path> <manifest files...>
needs_install() {
  local name=$1 marker=$2; shift 2
  [[ ${FORCE_INSTALL:-0} == 1 ]] && return 0
  [[ -e $marker ]] || return 0
  [[ "$(cat "$STAMP_DIR/$name" 2>/dev/null)" == "$(hash_files "$@")" ]] && return 1
  return 0
}

mark_installed() { local name=$1; shift; hash_files "$@" > "$STAMP_DIR/$name"; }

install_inference() {
  local dir="$ROOT/inference"
  if needs_install inference "$dir/.venv/bin/uvicorn" "$dir/pyproject.toml"; then
    step "Installing inference dependencies (Python)"
    [[ -x $dir/.venv/bin/python ]] || "$PYTHON" -m venv "$dir/.venv"
    "$dir/.venv/bin/pip" install -q --upgrade pip
    (cd "$dir" && .venv/bin/pip install -q -e '.[rapid,embed,dev]')
    mark_installed inference "$dir/pyproject.toml"
    ok "inference ready"
  fi
}

install_frontend() {
  local dir="$ROOT/frontend"
  if needs_install frontend "$dir/node_modules/.bin/vite" "$dir/package.json" "$dir/package-lock.json"; then
    step "Installing frontend dependencies (npm)"
    (cd "$dir" && if [[ -f package-lock.json ]]; then npm ci --no-audit --no-fund --loglevel=error >/dev/null; else npm install --no-audit --no-fund --loglevel=error >/dev/null; fi)
    mark_installed frontend "$dir/package.json" "$dir/package-lock.json"
    ok "frontend ready"
  fi
}

install_backend() {
  local dir="$ROOT/backend"
  if needs_install backend "$STAMP_DIR/backend" "$dir/pom.xml"; then
    step "Resolving backend dependencies (Maven)"
    (cd "$dir" && mvn -q -B -DskipTests dependency:resolve dependency:resolve-plugins)
    mark_installed backend "$dir/pom.xml"
    ok "backend ready"
  fi
}

install_all() {
  mkdir -p "$STAMP_DIR"
  install_inference
  install_frontend
  install_backend
}

ensure_env() {
  local env="$ROOT/inference/.env"
  if [[ ! -f $env ]]; then
    cp "$ROOT/inference/.env.example" "$env"
    chmod 600 "$env"
    warn "created inference/.env from .env.example"
  fi
  if ! grep -qE '^VED_INFERENCE_GEMINI_API_KEY=.+' "$env"; then
    die "set VED_INFERENCE_GEMINI_API_KEY in inference/.env (study features need an LLM key)"
  fi
}

# ----------------------------------------------------------------------------- process management

pid_of() { cat "$PID_DIR/$1" 2>/dev/null || true; }
is_running() { local pid; pid=$(pid_of "$1"); [[ -n $pid ]] && kill -0 "$pid" 2>/dev/null; }
port_in_use() { (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null; }

# Kills a process and all its descendants (mvn -> java, npm -> node, ...).
kill_tree() {
  local pid=$1 child
  for child in $(pgrep -P "$pid" 2>/dev/null); do kill_tree "$child"; done
  kill "$pid" 2>/dev/null || true
}

embedding_mode() { if [[ $LOCAL_EMBEDDING == 1 ]]; then echo local; else echo api; fi; }

start_service() {
  local name=$1 cmd dir
  if is_running "$name"; then
    ok "$name already running (pid $(pid_of "$name"))"
    if [[ $name == inference && "$(cat "$PID_DIR/inference.mode" 2>/dev/null || echo api)" != "$(embedding_mode)" ]]; then
      warn "inference runs with $(cat "$PID_DIR/inference.mode" 2>/dev/null || echo api) embeddings; run ./dev.sh stop first to switch to $(embedding_mode)"
    fi
    return
  fi
  if port_in_use "${PORT[$name]}"; then
    die "port ${PORT[$name]} ($name) is used by another process. Stop it or run ./dev.sh stop"
  fi
  case $name in
    inference) dir="$ROOT/inference"; cmd=".venv/bin/uvicorn ved_inference.main:app --port 8000 --reload --reload-dir src" ;;
    backend)   dir="$ROOT/backend";   cmd="mvn -q -B spring-boot:run" ;;
    frontend)  dir="$ROOT/frontend";  cmd="npx vite --port 5173 --strictPort" ;;
  esac
  : > "$LOG_DIR/$name.log"
  (
    cd "$dir"
    if [[ $name == inference && $LOCAL_EMBEDDING == 1 ]]; then
      export VED_INFERENCE_CONFIG="settings.yaml,settings.local-embedding.yaml"
    fi
    exec nohup $cmd >> "$LOG_DIR/$name.log" 2>&1
  ) &
  echo $! > "$PID_DIR/$name"
  [[ $name == inference ]] && embedding_mode > "$PID_DIR/inference.mode"
  info "$name starting (pid $!, log .dev/logs/$name.log)"
}

wait_healthy() {
  local name=$1 deadline=$((SECONDS + TIMEOUT[$1]))
  until curl -sf -o /dev/null "${HEALTH[$name]}"; do
    if ! is_running "$name"; then
      printf '%s%s exited during startup. Last log lines:%s\n' "$RED" "$name" "$RESET" >&2
      tail -n 25 "$LOG_DIR/$name.log" >&2
      return 1
    fi
    if ((SECONDS > deadline)); then
      printf '%s%s not healthy after %ss. Last log lines:%s\n' "$RED" "$name" "${TIMEOUT[$name]}" "$RESET" >&2
      tail -n 25 "$LOG_DIR/$name.log" >&2
      return 1
    fi
    sleep 1
  done
  ok "$name healthy on :${PORT[$name]}"
}

stop_services() {
  local name stopped=0
  for name in "${SERVICES[@]}"; do
    if is_running "$name"; then
      kill_tree "$(pid_of "$name")"
      stopped=1
      info "stopped $name"
    fi
    rm -f "$PID_DIR/$name"
  done
  ((stopped)) || info "no services were running"
}

start_database() {
  step "Starting PostgreSQL (docker compose)"
  local out
  out=$(docker compose -f "$ROOT/docker-compose.yml" up -d --wait 2>&1) || { echo "$out" >&2; die "could not start PostgreSQL"; }
  ok "postgres healthy on :5433"
}

LOG_PIDS=()

stream_logs() {
  local names=("$@") name
  for name in "${names[@]}"; do
    # $! is the tail itself; sed ends when tail's output closes.
    tail -n 0 -F "$LOG_DIR/$name.log" 2>/dev/null \
      > >(sed -u "s/^/$(printf '\e[%sm%-9s\e[0m| ' "${COLOR[$name]}" "$name")/") &
    LOG_PIDS+=($!)
  done
  wait "${LOG_PIDS[@]}" 2>/dev/null || true
}

stop_log_streams() {
  local pid
  for pid in "${LOG_PIDS[@]}"; do kill "$pid" 2>/dev/null || true; done
}

# ----------------------------------------------------------------------------- commands

cmd_up() {
  local detach=$1
  mkdir -p "$LOG_DIR" "$PID_DIR" "$STAMP_DIR"
  check_prerequisites
  install_all
  ensure_env
  start_database
  step "Starting services"
  for name in "${SERVICES[@]}"; do start_service "$name"; done
  if [[ $detach == 0 ]]; then
    trap 'echo; step "Stopping services"; stop_log_streams; stop_services; exit 0' INT TERM
  fi
  for name in "${SERVICES[@]}"; do
    wait_healthy "$name" || { stop_services; exit 1; }
  done
  printf '\n%sVed is running%s\n' "$GREEN$BOLD" "$RESET"
  info "App        http://localhost:5173"
  info "Backend    http://localhost:8080   ${DIM}(API under /api)${RESET}"
  info "Inference  http://localhost:8000   ${DIM}(internal)${RESET}"
  local embedding
  embedding=$(curl -sf http://localhost:8000/health | sed -n 's/.*"embeddingModel":"\([^"]*\)".*/\1/p' || true)
  if [[ "$(cat "$PID_DIR/inference.mode" 2>/dev/null || echo api)" == local ]]; then
    info "Embeddings ${embedding:-unknown}   ${DIM}(in-house; start without --local-embedding for the API)${RESET}"
  else
    info "Embeddings ${embedding:-unknown}   ${DIM}(API; ./dev.sh --local-embedding to run them in-house)${RESET}"
  fi
  if [[ $detach == 1 ]]; then
    info "Logs: ./dev.sh logs    Stop: ./dev.sh stop"
  else
    printf '\n%sStreaming logs. Ctrl+C stops everything.%s\n\n' "$DIM" "$RESET"
    stream_logs "${SERVICES[@]}"
  fi
}

cmd_status() {
  local name
  for name in "${SERVICES[@]}"; do
    if is_running "$name"; then
      if curl -sf -o /dev/null "${HEALTH[$name]}"; then state="${GREEN}healthy${RESET}"; else state="${YELLOW}starting${RESET}"; fi
      printf '  %-10s %s  pid %s  :%s\n' "$name" "$state" "$(pid_of "$name")" "${PORT[$name]}"
    elif port_in_use "${PORT[$name]}"; then
      printf '  %-10s %sport %s used by another process%s\n' "$name" "$YELLOW" "${PORT[$name]}" "$RESET"
    else
      printf '  %-10s %sstopped%s\n' "$name" "$DIM" "$RESET"
    fi
  done
  if docker compose -f "$ROOT/docker-compose.yml" ps --status running -q 2>/dev/null | grep -q .; then
    printf '  %-10s %srunning%s  :5433\n' postgres "$GREEN" "$RESET"
  else
    printf '  %-10s %sstopped%s\n' postgres "$DIM" "$RESET"
  fi
}

cmd_logs() {
  local names=("${SERVICES[@]}")
  if [[ $# -gt 0 ]]; then
    [[ -n ${PORT[$1]:-} ]] || die "unknown service '$1' (inference | backend | frontend)"
    names=("$1")
  fi
  for name in "${names[@]}"; do [[ -f $LOG_DIR/$name.log ]] && tail -n 30 "$LOG_DIR/$name.log" | sed "s/^/$name | /"; done
  trap 'stop_log_streams; exit 0' INT TERM
  stream_logs "${names[@]}"
}

cmd_test() {
  check_prerequisites
  install_all
  step "Backend tests (unit + integration, needs Docker)"
  (cd "$ROOT/backend" && mvn -q -B verify) && ok "backend"
  step "Inference tests"
  (cd "$ROOT/inference" && .venv/bin/ruff check -q src tests && .venv/bin/pytest -q) && ok "inference"
  step "Frontend checks"
  (cd "$ROOT/frontend" && npx tsc --noEmit && npx vitest run --silent && npx vite build --logLevel error) && ok "frontend"
}

usage() { awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; }

# Global options may appear anywhere; the rest is the command.
LOCAL_EMBEDDING=0
args=()
for arg in "$@"; do
  case $arg in
    --local-embedding) LOCAL_EMBEDDING=1 ;;
    *) args+=("$arg") ;;
  esac
done
set -- "${args[@]+"${args[@]}"}"

case "${1:-up}" in
  up)            if [[ ${2:-} == "-d" ]]; then cmd_up 1; else cmd_up 0; fi ;;
  -d|--detach)   cmd_up 1 ;;
  setup)         mkdir -p "$STAMP_DIR"; check_prerequisites; FORCE_INSTALL=1 install_all; ensure_env; ok "setup complete. Run ./dev.sh" ;;
  stop)          stop_services ;;
  down)          stop_services; docker compose -f "$ROOT/docker-compose.yml" down; ok "database stopped (data kept in the volume)" ;;
  status)        cmd_status ;;
  logs)          shift; cmd_logs "$@" ;;
  test)          cmd_test ;;
  -h|--help|help) usage ;;
  *)             usage; exit 1 ;;
esac
