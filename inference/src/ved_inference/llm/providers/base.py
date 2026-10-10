from abc import ABC, abstractmethod

from ved_inference.config import ModelRef
from ved_inference.llm.base import EmbedItem, EmbedTask, ImagePart, T


class Provider(ABC):
    """One model vendor / endpoint. Adding a vendor = one subclass + a branch in registry.build_provider.

    ``generate``/``embed`` raise ``retry.ModelUnavailable`` when the router should try the next model, and
    ``errors.EngineError`` for requests that would fail on any model (bad request, auth).
    """

    #: Whether this provider can embed images (else figures are embedded from their text).
    supports_image_embeddings: bool = False

    @abstractmethod
    def generate(
        self,
        model: ModelRef,
        *,
        system: str,
        prompt: str,
        schema: type[T],
        temperature: float,
        images: list[ImagePart],
    ) -> T: ...

    @abstractmethod
    def embed(
        self, model: str, dimensions: int, items: list[EmbedItem], task: EmbedTask
    ) -> list[list[float]]: ...

    def close(self) -> None:
        """Release resources."""
