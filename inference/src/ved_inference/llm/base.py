from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Literal, TypeVar

from pydantic import BaseModel

T = TypeVar("T", bound=BaseModel)


@dataclass(frozen=True)
class ImagePart:
    data: bytes
    mime: str


class LlmClient(ABC):
    """Provider-agnostic structured generation. Implementations: ``gemini.GeminiClient``.

    ``generate`` must return an instance of ``schema`` or raise ``EngineError``. ``images`` are sent
    before the prompt (vision); keep them few and small.
    """

    @property
    @abstractmethod
    def model_name(self) -> str: ...

    @abstractmethod
    def generate(
        self,
        *,
        system: str,
        prompt: str,
        schema: type[T],
        temperature: float = 0.4,
        images: list[ImagePart] | None = None,
    ) -> T: ...

    def close(self) -> None:
        """Release resources."""


EmbedTask = Literal["RETRIEVAL_DOCUMENT", "RETRIEVAL_QUERY"]


@dataclass(frozen=True)
class EmbedItem:
    """Text, an image, or both (multimodal: a figure with its caption)."""

    text: str | None = None
    image: ImagePart | None = None


class Embedder(ABC):
    """Dense vectors in one space for text and images (multimodal)."""

    @property
    @abstractmethod
    def model_name(self) -> str: ...

    @property
    @abstractmethod
    def dimensions(self) -> int: ...

    @abstractmethod
    def embed(self, items: list[EmbedItem], task: EmbedTask) -> list[list[float]]: ...

    def close(self) -> None:
        """Release resources."""
