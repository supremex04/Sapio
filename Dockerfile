# Local Compose targets build each service separately. Hugging Face builds the final `space` stage,
# which runs the web app, API, inference service and PostgreSQL in one container.

FROM node:22-alpine AS frontend-build
WORKDIR /build
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend-build
WORKDIR /build
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
RUN mvn -B -q -DskipTests package

FROM python:3.12-slim-bookworm AS inference-build
WORKDIR /build
COPY inference/pyproject.toml ./
COPY inference/src ./src
RUN pip install --no-cache-dir . 'rapidocr_onnxruntime>=1.4'

FROM python:3.12-slim-bookworm AS inference
WORKDIR /app/inference
COPY --from=inference-build /usr/local /usr/local
COPY inference/settings.yaml ./settings.yaml
EXPOSE 8000
CMD ["uvicorn", "ved_inference.main:app", "--host", "0.0.0.0", "--port", "8000"]

FROM eclipse-temurin:21-jre AS backend
WORKDIR /app
RUN useradd --system --uid 10001 ved
COPY --from=backend-build /build/target/ved-backend-*.jar /app/app.jar
RUN mkdir -p /data/storage && chown -R ved:ved /app /data
USER ved
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM nginx:1.27-alpine AS frontend
COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=frontend-build /build/dist /usr/share/nginx/html
EXPOSE 80

# Space runtime. PostgreSQL with pgvector runs locally in the same container; mount a Space
# Storage Bucket at /data if database and uploaded files must survive Space restarts.
FROM pgvector/pgvector:pg17 AS space
USER root
RUN apt-get update && apt-get install -y --no-install-recommends \
      ca-certificates nginx supervisor postgresql-contrib-17 \
      libgomp1 libglib2.0-0 libstdc++6 \
    && rm -rf /var/lib/apt/lists/* \
    && rm -f /etc/nginx/sites-enabled/default /etc/nginx/conf.d/default.conf \
    && useradd --uid 1000 --create-home --shell /bin/bash user
COPY --from=inference-build /usr/local /usr/local
COPY --from=backend-build /opt/java/openjdk /opt/java/openjdk
ENV JAVA_HOME=/opt/java/openjdk \
    PATH="/opt/java/openjdk/bin:/usr/local/bin:$PATH" \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    VED_DB_URL=jdbc:postgresql://127.0.0.1:5432/ved \
    VED_DB_USERNAME=ved \
    VED_INFERENCE_URL=http://127.0.0.1:8000 \
    VED_STORAGE_DIR=/data/storage \
    VED_PORT=8080 \
    VED_COOKIE_SECURE=true \
    PGDATA=/data/postgres \
    PGHOST=/tmp
WORKDIR /app
COPY --from=backend-build /build/target/ved-backend-*.jar /app/backend.jar
COPY --from=frontend-build /build/dist /app/frontend
COPY inference/settings.yaml /app/inference/settings.yaml
COPY docker/space-nginx.conf /etc/nginx/nginx.conf
COPY docker/space-supervisord.conf /etc/supervisor/conf.d/ved.conf
COPY docker/space-entrypoint.sh /app/space-entrypoint.sh
COPY docker/space-backend-start.sh /app/space-backend-start.sh
RUN chmod +x /app/space-entrypoint.sh \
    /app/space-backend-start.sh \
    && mkdir -p /data/postgres /data/storage /run/nginx /var/lib/nginx/tmp \
    && chown -R 1000:1000 /data /app /run/nginx /var/lib/nginx /var/log/nginx
USER 1000
EXPOSE 7860
ENTRYPOINT ["/app/space-entrypoint.sh"]
