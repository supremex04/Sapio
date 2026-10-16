"""Narration: text to speech with Kokoro-82M (ONNX, int8, CPU), in the renderer's own trusted process.

The model files are baked into the image (see the Dockerfile); without them, or if synthesis fails, renders go on
silently. Clips are cached by text, so a repaired scene does not synthesize the same captions again.
"""

import io
import logging
import os
import threading
import wave
from collections import OrderedDict
from dataclasses import dataclass
from pathlib import Path

log = logging.getLogger("sapio_renderer.speech")

MODEL_DIR = Path(os.environ.get("SAPIO_RENDERER_TTS_DIR", "/app/models"))
VOICE = os.environ.get("SAPIO_RENDERER_TTS_VOICE", "af_heart")
LANG = "en-us"
SPEED = 1.0
PEAK = 0.89  # about -1 dBFS
PAUSE_SECONDS = 0.25  # silence after each line, so beats do not run into each other
MAX_TEXT = 400

_lock = threading.Lock()
_engine = None
_failed = False
_cache: OrderedDict[str, "Clip"] = OrderedDict()
_CACHE_SIZE = 64


@dataclass(frozen=True)
class Clip:
    wav: bytes
    seconds: float


def available() -> bool:
    return (MODEL_DIR / "kokoro.onnx").exists() and (MODEL_DIR / "voices.bin").exists()


def engine_name() -> str:
    return f"kokoro-82m ({VOICE})" if available() else "none"


def synthesize(text: str) -> Clip | None:
    """A WAV clip of ``text`` spoken, or None when speech is unavailable."""
    text = " ".join(text.split())[:MAX_TEXT]
    if not text:
        return None
    with _lock:
        if text in _cache:
            _cache.move_to_end(text)
            return _cache[text]
        engine = _load()
        if engine is None:
            return None
        try:
            samples, rate = engine.create(text, voice=VOICE, speed=SPEED, lang=LANG)
        except Exception as e:  # noqa: BLE001 - narration is optional; a silent video is still useful
            log.warning("Speech failed: %s", e)
            return None
        clip = _to_wav(samples, rate)
        _cache[text] = clip
        if len(_cache) > _CACHE_SIZE:
            _cache.popitem(last=False)
        return clip


def _load():
    global _engine, _failed
    if _engine is None and not _failed:
        if not available():
            _failed = True
            log.info("No speech model in %s: videos will be silent", MODEL_DIR)
            return None
        try:
            from kokoro_onnx import Kokoro

            _engine = Kokoro(str(MODEL_DIR / "kokoro.onnx"), str(MODEL_DIR / "voices.bin"))
        except Exception as e:  # noqa: BLE001
            _failed = True
            log.warning("Speech model failed to load: %s", e)
    return _engine


def _to_wav(samples, rate: int) -> Clip:
    import numpy as np

    audio = np.asarray(samples, dtype=np.float32)
    peak = float(np.max(np.abs(audio))) if audio.size else 0.0
    if peak > 1e-4:
        audio = audio * (PEAK / peak)  # every line equally loud, near full scale
    pcm = (np.clip(audio, -1.0, 1.0) * 32767).astype("<i2")
    pcm = np.concatenate([pcm, np.zeros(int(rate * PAUSE_SECONDS), dtype="<i2")])
    out = io.BytesIO()
    with wave.open(out, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(pcm.tobytes())
    return Clip(out.getvalue(), len(pcm) / rate)


WORDS_PER_SECOND = 2.6  # timing when there is no voice: roughly a teacher's pace


@dataclass(frozen=True)
class Track:
    audio: bytes | None  # AAC in MP4 (m4a); None when speech is unavailable
    beats: list[tuple[float, float]]  # (start, seconds) of each line


def track(lines: list[str]) -> Track:
    """One audio track of all the lines in order, and where each starts: for viewers that are not videos
    (molecules, proteins, code), which step through the beats in time with it."""
    import subprocess
    import tempfile

    clips = [synthesize(line) for line in lines]
    if not lines or any(c is None for c in clips):
        beats, start = [], 0.0
        for line in lines:
            seconds = max(2.5, len(line.split()) / WORDS_PER_SECOND)
            beats.append((round(start, 3), round(seconds, 3)))
            start += seconds
        return Track(None, beats)

    beats, start, frames, rate = [], 0.0, [], None
    for clip in clips:
        with wave.open(io.BytesIO(clip.wav)) as w:
            rate = w.getframerate()
            frames.append(w.readframes(w.getnframes()))
        beats.append((round(start, 3), round(clip.seconds, 3)))
        start += clip.seconds
    with tempfile.TemporaryDirectory(prefix="track-") as tmp:
        wav, m4a = Path(tmp) / "track.wav", Path(tmp) / "track.m4a"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(rate)
            w.writeframes(b"".join(frames))
        done = subprocess.run(
            [
                "ffmpeg",
                "-loglevel",
                "error",
                "-i",
                str(wav),
                "-c:a",
                "aac",
                "-b:a",
                "64k",
                "-movflags",
                "+faststart",
                "-y",
                str(m4a),
            ],
            check=False,
            timeout=60,
        )
        if done.returncode != 0 or not m4a.exists():
            log.warning("Could not encode the narration track")
            return Track(None, beats)
        return Track(m4a.read_bytes(), beats)
