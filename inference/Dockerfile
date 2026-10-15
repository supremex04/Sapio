# Sapio inference service (FastAPI). Build: docker build -t sapio-inference inference/
# CPU only: RapidOCR for scans, faster-whisper for recordings; the LLM and embeddings are API calls (Gemini).
# Configuration: settings.yaml plus SAPIO_INFERENCE_* environment variables (keys come from the platform's secrets).
FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    HF_HOME=/opt/hf
# OpenCV (used by RapidOCR) needs these shared libraries.
RUN apt-get update && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY pyproject.toml ./
COPY src src
RUN pip install ".[rapid,stt]"
# Bake the speech model (~480 MB) into the image: no download on every cold start.
RUN python -c "from faster_whisper import WhisperModel; WhisperModel('small', device='cpu', compute_type='int8')"
COPY settings.yaml ./
RUN useradd --system --uid 10001 sapio
USER sapio
ENV SAPIO_INFERENCE_OCR__ENGINE=rapid \
    SAPIO_INFERENCE_SPEECH__DEVICE=cpu
EXPOSE 8000
# Cloud Run sets PORT; locally it defaults to 8000.
CMD ["sh", "-c", "exec uvicorn sapio_inference.main:app --host 0.0.0.0 --port ${PORT:-8000} --timeout-graceful-shutdown 20"]
