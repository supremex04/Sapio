"""faster-whisper (Whisper on CTranslate2): CPU everywhere (Linux, macOS, Windows), NVIDIA GPU when available."""

import logging
import threading
from collections.abc import Iterable

from sapio_inference.config import SpeechConfig
from sapio_inference.errors import ConfigurationError, EngineError
from sapio_inference.speech.base import AudioWindow, Transcript, TranscriptSegment

log = logging.getLogger(__name__)


class FasterWhisperEngine:
    def __init__(self, config: SpeechConfig) -> None:
        self._config = config
        self._model = None
        self._load_lock = threading.Lock()
        self._slots = threading.BoundedSemaphore(config.max_concurrency)
        self.name = f"faster-whisper:{config.model}"

    def transcribe(self, windows: Iterable[AudioWindow], duration: float) -> Transcript:
        with self._slots:
            model = self._load()
            language = self._config.language
            segments: list[TranscriptSegment] = []
            try:
                for window in windows:
                    parts, info = model.transcribe(
                        window.samples,
                        language=language,
                        beam_size=self._config.beam_size,
                        vad_filter=True,  # skip silence: faster, and no hallucinated text in pauses
                        condition_on_previous_text=False,  # avoids repetition loops on long recordings
                    )
                    language = language or info.language  # detected once, then kept for every window
                    for part in parts:
                        text = part.text.strip()
                        if text:
                            segments.append(
                                TranscriptSegment(window.offset + part.start, window.offset + part.end, text)
                            )
            except (ConfigurationError, EngineError):
                raise
            except Exception as exc:  # CTranslate2 / decoding failures
                raise EngineError(f"Transcription failed: {exc}") from exc
            return Transcript(
                language=language or "und", duration=duration, engine=self.name, segments=segments
            )

    def _load(self):
        with self._load_lock:
            if self._model is None:
                self._model = self._open()
            return self._model

    def _open(self):
        try:
            import ctranslate2
            from faster_whisper import WhisperModel
        except ImportError as exc:  # pragma: no cover - depends on installed extras
            raise ConfigurationError("Speech-to-text needs the 'stt' extra: pip install -e '.[stt]'") from exc
        device = self._config.device
        if device == "auto":
            device = "cuda" if ctranslate2.get_cuda_device_count() > 0 else "cpu"
        compute = self._config.compute_type
        if compute == "auto":
            compute = "int8_float16" if device == "cuda" else "int8"
        try:
            model = WhisperModel(
                self._config.model, device=device, compute_type=compute, cpu_threads=self._config.cpu_threads
            )
        except Exception as exc:
            if device != "cuda" or self._config.device == "cuda":
                raise ConfigurationError(f"Cannot load speech model {self._config.model}: {exc}") from exc
            # CUDA present but unusable (driver / libraries): fall back to CPU rather than failing.
            log.warning("GPU speech model failed to load (%s); using the CPU", exc)
            device, compute = "cpu", "int8"
            model = WhisperModel(
                self._config.model, device=device, compute_type=compute, cpu_threads=self._config.cpu_threads
            )
        log.info("Speech model %s ready on %s (%s)", self._config.model, device, compute)
        return model

    def close(self) -> None:
        self._model = None
