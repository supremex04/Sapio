from ved_inference.config import Settings
from ved_inference.speech.base import SpeechEngine


def build_speech(settings: Settings) -> SpeechEngine:
    """The configured engine. Models load lazily on the first recording, so startup stays fast."""
    from ved_inference.speech.faster_whisper_engine import FasterWhisperEngine

    return FasterWhisperEngine(settings.speech)
