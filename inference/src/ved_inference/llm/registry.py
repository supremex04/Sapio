from ved_inference.config import Settings
from ved_inference.llm.base import Embedder, LlmClient


def build_llm(settings: Settings) -> LlmClient:
    if settings.llm_provider == "gemini":
        from ved_inference.llm.gemini import GeminiClient

        return GeminiClient(settings)
    raise ValueError(f"Unknown LLM provider {settings.llm_provider}")


def build_embedder(settings: Settings) -> Embedder:
    from ved_inference.llm.gemini import GeminiEmbedder

    return GeminiEmbedder(settings)
