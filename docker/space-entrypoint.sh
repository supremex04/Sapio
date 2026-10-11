#!/bin/sh
set -eu

: "${VED_DB_PASSWORD:?Set VED_DB_PASSWORD in the Space Secrets}"
: "${VED_INFERENCE_API_KEY:?Set VED_INFERENCE_API_KEY in the Space Secrets}"
: "${VED_JWT_SECRET:?Set VED_JWT_SECRET in the Space Secrets}"
: "${VED_FIGURE_URL_SECRET:?Set VED_FIGURE_URL_SECRET in the Space Secrets}"
: "${VED_INFERENCE_GEMINI_API_KEY:?Set VED_INFERENCE_GEMINI_API_KEY in the Space Secrets}"

mkdir -p "$PGDATA" "$VED_STORAGE_DIR" /tmp/nginx/client_temp /tmp/nginx/proxy_temp
if [ ! -s "$PGDATA/PG_VERSION" ]; then
  password_file=/tmp/ved-postgres-password
  umask 077
  printf '%s' "$VED_DB_PASSWORD" > "$password_file"
  /usr/lib/postgresql/17/bin/initdb -D "$PGDATA" --username="$VED_DB_USERNAME" --pwfile="$password_file" \
    --auth-local=trust --auth-host=scram-sha-256
  rm -f "$password_file"
fi

# Create the application database on both fresh and existing data volumes before Spring Boot starts.
/usr/lib/postgresql/17/bin/pg_ctl -D "$PGDATA" \
  -o "-h 127.0.0.1 -k /tmp -p 5432" -w start
if ! /usr/lib/postgresql/17/bin/psql -h /tmp -U "$VED_DB_USERNAME" -d postgres \
  -tAc "SELECT 1 FROM pg_database WHERE datname = 'ved'" | grep -q 1; then
  /usr/lib/postgresql/17/bin/createdb -h /tmp -U "$VED_DB_USERNAME" ved
fi
/usr/lib/postgresql/17/bin/pg_ctl -D "$PGDATA" -m fast -w stop

exec supervisord -n -c /etc/supervisor/conf.d/ved.conf
