from pydantic import BaseModel

from sapio_inference.engines.base import EngineOutput, OcrEngine, OcrSegment
from sapio_inference.llm.base import Embedder, LlmClient


class FakeOcrEngine(OcrEngine):
    name = "fake-ocr"

    def __init__(self) -> None:
        self.calls: list[list[int]] = []

    def extract(self, pages):
        self.calls.append([p.number for p in pages])
        return EngineOutput(
            segments=[
                OcrSegment(p.number, p.number, f"# Scanned {p.number}\n\nocr page {p.number}") for p in pages
            ]
        )


class FakeLlm(LlmClient):
    """Returns queued responses (model instances or dicts) and records prompts and images."""

    def __init__(self, *responses) -> None:
        self.responses = list(responses)
        self.prompts: list[str] = []
        self.images: list[int] = []
        self.tasks: list[str] = []

    @property
    def model_name(self) -> str:
        return "fake-llm"

    def generate(self, *, system, prompt, schema, temperature=0.4, images=None, task="default"):
        self.prompts.append(prompt)
        self.tasks.append(task)
        self.images.append(len(images or []))
        response = self.responses.pop(0)
        return response if isinstance(response, BaseModel) else schema.model_validate(response)


class FakeEmbedder(Embedder):
    def __init__(self) -> None:
        self.calls: list[tuple[int, str]] = []

    @property
    def model_name(self) -> str:
        return "fake-embed"

    @property
    def dimensions(self) -> int:
        return 4

    def embed(self, items, task):
        self.calls.append((len(items), task))
        return [[float(len(i.text or "")), 1.0 if i.image else 0.0, 0.0, 1.0] for i in items]


class FakeSpeech:
    """Speech engine double: consumes the audio windows and returns fixed segments."""

    name = "fake-stt"

    def __init__(self, segments=None) -> None:
        from sapio_inference.speech.base import TranscriptSegment

        self.segments = segments or [
            TranscriptSegment(0.0, 6.0, "Today we talk about database indexes."),
            TranscriptSegment(6.0, 12.0, "A B-tree keeps keys sorted."),
        ]
        self.windows: list[float] = []

    def transcribe(self, windows, duration):
        from sapio_inference.speech.base import Transcript

        self.windows = [w.offset for w in windows]
        return Transcript(language="en", duration=duration, engine=self.name, segments=self.segments)

    def close(self) -> None:
        pass
