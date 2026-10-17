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

from sapio_renderer import safety, speech

QUALITY = {"preview": "l", "final": "m"}  # Manim: l = 480p15, m = 720p30
TEMPLATE = Path(__file__).with_name("tex_template.tex")
MAX_SECONDS = 120.0  # longest video accepted (six narrated steps run to about 70 s)

_one_at_a_time = threading.Semaphore(1)


@dataclass
class Rendered:
    video: bytes
    thumbnail: bytes
    duration_seconds: float
    narrated: bool = False


@dataclass
class RenderFailed(Exception):
    """``kind``: code (rejected before running), error (Manim raised), timeout, output (no usable video)."""

    kind: str
    message: str

    def __str__(self) -> str:
        return self.message


@dataclass(frozen=True)
class Limits:
    timeout_seconds: float = 240.0
    cpu_seconds: int = 300
    memory_bytes: int = 3 * 1024**3
    file_bytes: int = 300 * 1024**2


DEFAULT_LIMITS = Limits()


def render(
    code: str, quality: str = "final", narration: list[str] | None = None, limits: Limits = DEFAULT_LIMITS
) -> Rendered:
    """``narration``: the spoken line of each beat, in order; the scene plays them with sapio_narration.narrate."""
    safety.check(code)  # raises safety.Rejected
    if not _one_at_a_time.acquire(timeout=limits.timeout_seconds):
        raise RenderFailed("timeout", "The renderer is busy; please retry.")
    try:
        with tempfile.TemporaryDirectory(prefix="scene-") as tmp:
            narrated = _prepare_narration(Path(tmp), narration or [])
            result = _render_in(Path(tmp), code, QUALITY.get(quality, "m"), limits)
            result.narrated = narrated and result.narrated  # prepared, and actually in the video
            return result
    finally:
        _one_at_a_time.release()


def _prepare_narration(tmp: Path, lines: list[str]) -> bool:
    """Speaks each line into the scene folder and lists them in narration.json (read by sapio_narration), with their
    text, which the scene shows as the subtitles. Returns whether there is a voice: without one (speech
    unavailable) the subtitles still show, for an estimated time each."""
    clips = [speech.synthesize(text) for text in lines]
    voiced = bool(lines) and all(c is not None for c in clips)  # all or nothing: half a narration is worse
    manifest = []
    for i, (text, clip) in enumerate(zip(lines, clips, strict=True), 1):
        entry = {"text": " ".join(text.split()), "file": None, "seconds": round(speech.estimate(text), 3)}
        if voiced:
            name = f"beat-{i}.wav"
            (tmp / name).write_bytes(clip.wav)
            entry.update(file=str(tmp / name), seconds=round(clip.seconds, 3))
        manifest.append(entry)
    if manifest:
        (tmp / "narration.json").write_text(json.dumps(manifest), encoding="utf-8")
    return voiced


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
    _trim_audio(video, duration, env)
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
    return Rendered(
        video.read_bytes(),
        thumb.read_bytes() if thumb.exists() else b"",
        duration,
        narrated=_has_audio(video),
    )


def _limit(limits: Limits) -> None:
    """Runs in the child before Manim starts."""
    os.setsid()
    resource.setrlimit(resource.RLIMIT_CPU, (limits.cpu_seconds, limits.cpu_seconds))
    resource.setrlimit(resource.RLIMIT_AS, (limits.memory_bytes, limits.memory_bytes))
    resource.setrlimit(resource.RLIMIT_FSIZE, (limits.file_bytes, limits.file_bytes))
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))


def _duration(video: Path) -> float:
    """Length of the picture (the video stream), not of the file: audio must not make the video look longer."""
    probe = shutil.which("ffprobe")
    if not probe:
        return 1.0
    out = subprocess.run(
        [
            probe,
            "-v",
            "error",
            "-select_streams",
            "v:0",
            "-show_entries",
            "stream=duration",
            "-of",
            "json",
            str(video),
        ],
        check=False,
        capture_output=True,
        text=True,
        timeout=30,
    )
    try:
        return float(json.loads(out.stdout)["streams"][0]["duration"])
    except (ValueError, KeyError, IndexError, json.JSONDecodeError):
        return 0.0


def _has_audio(video: Path) -> bool:
    probe = shutil.which("ffprobe")
    if not probe or not video.exists():
        return False
    out = subprocess.run(
        [
            probe,
            "-v",
            "error",
            "-select_streams",
            "a",
            "-show_entries",
            "stream=index",
            "-of",
            "csv=p=0",
            str(video),
        ],
        check=False,
        capture_output=True,
        text=True,
        timeout=30,
    )
    return bool(out.stdout.strip())


def _trim_audio(video: Path, duration: float, env: dict) -> None:
    """Cuts any sound that runs past the last frame, so players show the same length as the picture."""
    if duration <= 0 or not shutil.which("ffmpeg"):
        return
    trimmed = video.with_name("trimmed.mp4")
    done = subprocess.run(
        [
            "ffmpeg",
            "-loglevel",
            "error",
            "-i",
            str(video),
            "-t",
            f"{duration:.3f}",
            "-c",
            "copy",
            "-movflags",
            "+faststart",
            "-y",
            str(trimmed),
        ],
        check=False,
        timeout=60,
        env=env,
    )
    if done.returncode == 0 and trimmed.exists() and trimmed.stat().st_size > 0:
        trimmed.replace(video)


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
