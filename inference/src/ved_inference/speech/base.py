"""Speech-to-text port: an engine turns 16 kHz mono audio into timed text."""

from collections.abc import Iterable
from dataclasses import dataclass
from typing import Protocol

import numpy as np


@dataclass(frozen=True)
class TranscriptSegment:
    start: float  # seconds from the start of the recording
    end: float
    text: str


@dataclass(frozen=True)
class Transcript:
    language: str  # ISO 639-1
    duration: float  # seconds
    engine: str  # "faster-whisper:small"
    segments: list[TranscriptSegment]


@dataclass(frozen=True)
class AudioWindow:
    offset: float  # seconds from the start of the recording
    samples: np.ndarray  # float32, 16 kHz mono, -1..1


class SpeechEngine(Protocol):
    """Implementations raise ``EngineError`` (busy / failed) or ``ConfigurationError`` (missing extra, bad model)."""

    name: str

    def transcribe(self, windows: Iterable[AudioWindow], duration: float) -> Transcript: ...

    def close(self) -> None: ...
