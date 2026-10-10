"""Builds providers, the routed LLM and the embedder from configuration: the only place that knows vendors."""

from ved_inference.config import ProviderConfig, Settings
from ved_inference.llm.base import Embedder, LlmClient
from ved_inference.llm.providers.base import Provider
from ved_inference.llm.router import RoutedEmbedder, RoutedLlm


def build_provider(name: str, config: ProviderConfig) -> Provider:
    if config.type == "gemini":
        from ved_inference.llm.providers.gemini import GeminiProvider

        return GeminiProvider(name, config)
    if config.type == "openai":
        from ved_inference.llm.providers.openai_compatible import OpenAICompatibleProvider

        return OpenAICompatibleProvider(name, config)
    if config.type == "onnx":
        from ved_inference.llm.providers.onnx_embedder import OnnxEmbeddingProvider

        return OnnxEmbeddingProvider(name, config)
    raise ValueError(f"Unknown provider type {config.type}")


def build_llm(settings: Settings) -> LlmClient:
    routes = [settings.llm.default, *settings.llm.tasks.values()]
    names = {ref.provider for route in routes for ref in route.chain()}
    return RoutedLlm({name: build_provider(name, settings.providers[name]) for name in names}, settings.llm)


def build_embedder(settings: Settings) -> Embedder:
    name = settings.embeddings.provider
    return RoutedEmbedder(build_provider(name, settings.providers[name]), settings.embeddings)
