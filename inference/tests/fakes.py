from pydantic import BaseModel

from ved_inference.engines.base import EngineOutput, OcrEngine, OcrSegment
from ved_inference.llm.base import LlmClient


class FakeOcrEngine(OcrEngine):
    name = "fake-ocr"

    def __init__(self) -> None:
        self.calls: list[list[int]] = []

    def extract(self, pages):
        self.calls.append([p.number for p in pages])
        return EngineOutput(segments=[OcrSegment(p.number, p.number, f"ocr page {p.number}") for p in pages])


class FakeLlm(LlmClient):
    """Returns queued responses (model instances or dicts) and records prompts."""

    def __init__(self, *responses) -> None:
        self.responses = list(responses)
        self.prompts: list[str] = []

    @property
    def model_name(self) -> str:
        return "fake-llm"

    def generate(self, *, system, prompt, schema, temperature=0.4):
        self.prompts.append(prompt)
        response = self.responses.pop(0)
        return response if isinstance(response, BaseModel) else schema.model_validate(response)
