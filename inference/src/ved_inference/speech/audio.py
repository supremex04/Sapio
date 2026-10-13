"""Decodes any audio or video container (mp3, m4a, wav, ogg, flac, webm, mp4, mov, mkv…) to 16 kHz mono windows.

PyAV ships its own FFmpeg libraries in its wheels (Linux, macOS, Windows), so no system FFmpeg is needed. Video
streams are ignored: only the first audio track is decoded. Windows keep memory flat for long recordings.
"""

from collections.abc import Iterator
from typing import BinaryIO

import numpy as np

from ved_inference.errors import DocumentError
from ved_inference.speech.base import AudioWindow

SAMPLE_RATE = 16_000


def probe_duration(source: BinaryIO) -> float:
    """Duration in seconds of the first audio track; DocumentError when there is none or the file is unreadable."""
    import av

    try:
        with av.open(source, mode="r") as container:
            if not container.streams.audio:
                raise DocumentError("This file has no audio track")
            stream = container.streams.audio[0]
            if stream.duration is not None and stream.time_base is not None:
                return float(stream.duration * stream.time_base)
            return float(container.duration or 0) / 1_000_000
    except DocumentError:
        raise
    except Exception as exc:  # av raises its own error hierarchy for corrupt input
        raise DocumentError("Unreadable or corrupt audio/video file") from exc
    finally:
        source.seek(0)


def decode_windows(source: BinaryIO, window_seconds: int) -> Iterator[AudioWindow]:
    import av

    window = window_seconds * SAMPLE_RATE
    buffer: list[np.ndarray] = []
    buffered = 0
    offset = 0
    try:
        with av.open(source, mode="r") as container:
            stream = container.streams.audio[0]
            resampler = av.AudioResampler(format="s16", layout="mono", rate=SAMPLE_RATE)
            for frame in container.decode(stream):
                for out in resampler.resample(frame):
                    chunk = out.to_ndarray().reshape(-1)
                    buffer.append(chunk)
                    buffered += chunk.size
                    while buffered >= window:
                        samples = np.concatenate(buffer)
                        cut = quiet_cut(samples, window)
                        yield AudioWindow(offset / SAMPLE_RATE, _to_float(samples[:cut]))
                        offset += cut
                        rest = samples[cut:]
                        buffer, buffered = ([rest] if rest.size else []), rest.size
            for out in resampler.resample(None):
                buffer.append(out.to_ndarray().reshape(-1))
    except DocumentError:
        raise
    except Exception as exc:
        raise DocumentError("Unreadable or corrupt audio/video file") from exc
    if buffer:
        samples = np.concatenate(buffer)
        if samples.size:
            yield AudioWindow(offset / SAMPLE_RATE, _to_float(samples))


def quiet_cut(samples: np.ndarray, window: int, search_seconds: float = 3.0) -> int:
    """Where to end a window: the quietest 100 ms in its last seconds, so a word is rarely cut in half."""
    frame = SAMPLE_RATE // 10
    start = max(0, window - int(search_seconds * SAMPLE_RATE))
    tail = samples[start:window].astype(np.float32)
    frames = tail[: tail.size - tail.size % frame].reshape(-1, frame)
    if frames.size == 0:
        return window
    quietest = int(np.argmin(np.abs(frames).mean(axis=1)))
    return start + quietest * frame + frame // 2


def _to_float(samples: np.ndarray) -> np.ndarray:
    return samples.astype(np.float32) / 32768.0
