#!/bin/sh
set -eu

until /usr/lib/postgresql/17/bin/pg_isready -h 127.0.0.1 -p 5432 -U "$VED_DB_USERNAME"; do
  sleep 1
done

exec /opt/java/openjdk/bin/java -jar /app/backend.jar
