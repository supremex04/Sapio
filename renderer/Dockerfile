# Sapio renderer: Manim (Cairo, Pango, ffmpeg, a minimal LaTeX for formulas). Build: docker build -t sapio-renderer renderer/
# Runs untrusted (AI-written) scene code: holds no secrets besides its own render key; deploy with no internet access.
FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1
RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential pkg-config libcairo2-dev libpango1.0-dev ffmpeg \
        texlive-latex-base texlive-latex-recommended texlive-latex-extra dvisvgm cm-super-minimal \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
# Narration voice: Kokoro-82M, int8 ONNX (about 120 MB with the voices), run on the CPU. Checked by hash; its own
# layer, so code changes do not download it again.
COPY scripts/fetch_models.py /tmp/fetch_models.py
RUN python /tmp/fetch_models.py && rm /tmp/fetch_models.py
COPY pyproject.toml ./
COPY src src
RUN pip install ".[manim]" \
    && apt-get purge -y build-essential && apt-get autoremove -y
# Warm Manim's LaTeX and font caches once at build time, so the first real render is quick.
RUN mkdir -p /tmp/warm && cd /tmp/warm && printf '%s\n' \
      'from manim import *' \
      'class Visualization(Scene):' \
      '    def construct(self):' \
      '        self.play(Write(MathTex(r"a^2 + b^2 = c^2")), run_time=0.5)' > scene.py \
    && python -m manim render -ql --disable_caching --progress_bar none scene.py Visualization >/dev/null \
    && rm -rf /tmp/warm
RUN useradd --system --uid 10001 --home /tmp sapio
USER sapio
EXPOSE 8100
CMD ["sh", "-c", "exec uvicorn sapio_renderer.main:app --host 0.0.0.0 --port ${PORT:-8100} --timeout-graceful-shutdown 20"]
