from abc import ABC, abstractmethod
from typing import TypeVar

from pydantic import BaseModel

T = TypeVar("T", bound=BaseModel)


class LlmClient(ABC):
    """Provider-agnostic structured generation. Implementations: ``gemini.GeminiClient``.

    ``generate`` must return an instance of ``schema`` or raise ``EngineError``.
    """

    @property
    @abstractmethod
    def model_name(self) -> str: ...

    @abstractmethod
    def generate(self, *, system: str, prompt: str, schema: type[T], temperature: float = 0.4) -> T: ...

    def close(self) -> None:
        """Release resources."""
