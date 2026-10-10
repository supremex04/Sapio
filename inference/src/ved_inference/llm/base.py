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
    """Vendor-neutral structured generation, as the use-cases see it.

    ``task`` selects the model route from configuration (llm.tasks / llm.default). ``generate`` returns an
    instance of ``schema`` or raises ``EngineError``. ``images`` are sent before the prompt (vision).
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
        task: str = "default",
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
    """Dense vectors (text, and images when the model is multimodal)."""

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
