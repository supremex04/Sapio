FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PORT=8080

WORKDIR /app
COPY pyproject.toml ./
COPY src ./src
COPY settings.yaml ./settings.yaml
RUN pip install --no-cache-dir '.[rapid,stt]'

EXPOSE 8080
CMD ["sh", "-c", "uvicorn sapio_inference.main:app --host 0.0.0.0 --port ${PORT:-8080}"]
