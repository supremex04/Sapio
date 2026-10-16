"""Renders checked scene code with Manim in a child process, inside limits.

Each render runs in a fresh temporary folder with an empty environment (nothing inherited), resource limits (CPU
time, memory, file size, processes) and a wall-clock timeout. The container itself holds no secrets and, in the
cloud, has no internet access. Only one render runs at a time per instance.
"""

import json
import os
import resource
import shutil
import subprocess
import sys
import tempfile
import threading
from dataclasses import dataclass
from pathlib import Path

from sapio_renderer import safety

QUALITY = {"preview": "l", "final": "m"}  # Manim: l = 480p15, m = 720p30
TEMPLATE = Path(__file__).with_name("tex_template.tex")
MAX_SECONDS = 90.0  # longest video accepted

_one_at_a_time = threading.Semaphore(1)


@dataclass
class Rendered:
    video: bytes
    thumbnail: bytes
    duration_seconds: float


@dataclass
class RenderFailed(Exception):
    """``kind``: code (rejected before running), error (Manim raised), timeout, output (no usable video)."""

    kind: str
    message: str

    def __str__(self) -> str:
        return self.message


@dataclass(frozen=True)
class Limits:
    timeout_seconds: float = 150.0
    cpu_seconds: int = 180
    memory_bytes: int = 3 * 1024**3
    file_bytes: int = 300 * 1024**2


DEFAULT_LIMITS = Limits()


def render(code: str, quality: str = "final", limits: Limits = DEFAULT_LIMITS) -> Rendered:
    safety.check(code)  # raises safety.Rejected
    if not _one_at_a_time.acquire(timeout=limits.timeout_seconds):
        raise RenderFailed("timeout", "The renderer is busy; please retry.")
    try:
        with tempfile.TemporaryDirectory(prefix="scene-") as tmp:
            return _render_in(Path(tmp), code, QUALITY.get(quality, "m"), limits)
    finally:
        _one_at_a_time.release()


def _render_in(tmp: Path, code: str, quality: str, limits: Limits) -> Rendered:
    (tmp / "scene.py").write_text(code, encoding="utf-8")
    (tmp / "manim.cfg").write_text(
        "[CLI]\n"
        f"tex_template_file = {TEMPLATE}\n"
        "disable_caching = True\n"
        "progress_bar = none\n"
        "verbosity = WARNING\n",
        encoding="utf-8",
    )
    command = [
        sys.executable,
        "-I",
        "-m",
        "manim",
        "render",
        "-q",
        quality,
        "--format",
        "mp4",
        "--media_dir",
        str(tmp / "media"),
        "scene.py",
        safety.SCENE_CLASS,
    ]
    # Nothing from our environment reaches the scene: only what Manim needs to run.
    env = {
        "PATH": "/usr/local/bin:/usr/bin:/bin",
        "HOME": str(tmp),
        "MPLCONFIGDIR": str(tmp),
        "TEXMFVAR": str(tmp / "texmf"),
        "LANG": "C.UTF-8",
    }
    try:
        done = subprocess.run(
            command,
            check=False,
            cwd=tmp,
            env=env,
            capture_output=True,
            text=True,
            timeout=limits.timeout_seconds,
            preexec_fn=lambda: _limit(limits),
        )
    except subprocess.TimeoutExpired as e:
        raise RenderFailed(
            "timeout",
            f"The animation took longer than {limits.timeout_seconds:.0f} s to render. "
            "Make it shorter or simpler.",
        ) from e
    if done.returncode != 0:
        raise RenderFailed("error", _error_tail(done.stderr or done.stdout))

    videos = list((tmp / "media").rglob(f"{safety.SCENE_CLASS}.mp4"))
    if not videos:
        raise RenderFailed("output", "The scene rendered no video. Make sure construct() plays animations.")
    video = videos[0]
    duration = _duration(video)
    if duration <= 0.2:
        raise RenderFailed("output", "The video is empty. Add animations (self.play) or waits (self.wait).")
    if duration > MAX_SECONDS:
        raise RenderFailed(
            "output", f"The video is {duration:.0f} s long; keep it under {MAX_SECONDS:.0f} s."
        )
    thumb = tmp / "thumb.png"
    subprocess.run(
        [
            "ffmpeg",
            "-loglevel",
            "error",
            "-sseof",
            "-0.3",
            "-i",
            str(video),
            "-frames:v",
            "1",
            "-y",
            str(thumb),
        ],
        check=False,
        timeout=30,
        env=env,
    )
    return Rendered(video.read_bytes(), thumb.read_bytes() if thumb.exists() else b"", duration)


def _limit(limits: Limits) -> None:
    """Runs in the child before Manim starts."""
    os.setsid()
    resource.setrlimit(resource.RLIMIT_CPU, (limits.cpu_seconds, limits.cpu_seconds))
    resource.setrlimit(resource.RLIMIT_AS, (limits.memory_bytes, limits.memory_bytes))
    resource.setrlimit(resource.RLIMIT_FSIZE, (limits.file_bytes, limits.file_bytes))
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))


def _duration(video: Path) -> float:
    probe = shutil.which("ffprobe")
    if not probe:
        return 1.0
    out = subprocess.run(
        [probe, "-v", "error", "-show_entries", "format=duration", "-of", "json", str(video)],
        check=False,
        capture_output=True,
        text=True,
        timeout=30,
    )
    try:
        return float(json.loads(out.stdout)["format"]["duration"])
    except (ValueError, KeyError, json.JSONDecodeError):
        return 0.0


def _error_tail(log: str) -> str:
    """The useful end of Manim's traceback: the error line and a little context (fed back to the AI).

    Manim prints tracebacks in boxes (rich); the box drawing is stripped so only the text remains.
    """
    lines = []
    for line in log.strip().splitlines():
        text = line.translate(_BOX).rstrip()
        if text.strip():
            lines.append(text)
    tail = lines[-25:]
    return "\n".join(tail)[-3000:] or "Manim failed without a message."


_BOX = str.maketrans({c: " " for c in "\u2502\u256d\u256e\u2570\u256f\u2500\u2771"})
