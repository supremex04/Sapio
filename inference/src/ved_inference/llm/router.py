"""Routes each request to the configured model chain (vendor-neutral, cross-provider fallbacks)."""

import logging
from contextvars import ContextVar

from ved_inference.config import EmbeddingConfig, LlmConfig
from ved_inference.errors import EngineError
from ved_inference.llm.base import Embedder, EmbedItem, EmbedTask, ImagePart, LlmClient, T
from ved_inference.llm.providers.base import Provider
from ved_inference.llm.retry import ModelUnavailable

log = logging.getLogger(__name__)

# Per request (each request runs in its own context), so concurrent requests never see each other's model.
_served: ContextVar[str | None] = ContextVar("served_model", default=None)


class RoutedLlm(LlmClient):
    def __init__(self, providers: dict[str, Provider], config: LlmConfig) -> None:
        self._providers = providers
        self._config = config
        ref = config.default.primary
        self._default = f"{ref.provider}:{ref.model}"

    @property
    def model_name(self) -> str:
        """The model that served this request's latest generation ("provider:model"); else the default route's."""
        return _served.get() or self._default

    def generate(
        self,
        *,
        system,
        prompt,
        schema: type[T],
        temperature=0.4,
        images: list[ImagePart] | None = None,
        task: str = "default",
    ) -> T:
        last: Exception | None = None
        for ref in self._config.route(task).chain():
            try:
                result = self._providers[ref.provider].generate(
                    ref,
                    system=system,
                    prompt=prompt,
                    schema=schema,
                    temperature=temperature,
                    images=images or [],
                )
                _served.set(f"{ref.provider}:{ref.model}")
                return result
            except ModelUnavailable as exc:
                log.warning("%s → falling back (%s)", f"{ref.provider}:{ref.model}", exc)
                last = exc
        raise EngineError(
            "The AI service is busy or out of quota right now. Please try again later."
        ) from last

    def close(self) -> None:
        for p in self._providers.values():
            p.close()


class RoutedEmbedder(Embedder):
    def __init__(self, provider: Provider, config: EmbeddingConfig) -> None:
        self._provider = provider
        self._config = config

    @property
    def model_name(self) -> str:
        return f"{self._config.provider}:{self._config.model}"

    @property
    def dimensions(self) -> int:
        return self._config.dimensions

    @property
    def multimodal(self) -> bool:
        return self._config.multimodal and self._provider.supports_image_embeddings

    def embed(self, items: list[EmbedItem], task: EmbedTask) -> list[list[float]]:
        if not self.multimodal:
            items = [EmbedItem(text=i.text) for i in items]  # figures fall back to caption + description text
        vectors: list[list[float]] = []
        size = self._config.batch_size
        try:
            for start in range(0, len(items), size):
                vectors.extend(
                    self._provider.embed(
                        self._config.model,
                        self._config.dimensions,
                        items[start : start + size],
                        task,
                        self._config.options,
                    )
                )
        except ModelUnavailable as exc:
            raise EngineError(
                "The embedding service is busy or out of quota. Processing will continue later."
            ) from exc
        for v in vectors:
            if len(v) != self._config.dimensions:
                raise EngineError(
                    f"Embedding model returned {len(v)} dimensions, configured {self._config.dimensions}; "
                    "align embeddings.dimensions with the model and the database column"
                )
        return vectors
