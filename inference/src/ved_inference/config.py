"""Service configuration, read from environment variables prefixed with ``VED_INFERENCE_``."""

from functools import lru_cache
from typing import Literal

from pydantic import Field, SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict

EngineName = Literal["rapid", "unlimited-remote", "unlimited-local"]
LlmProviderName = Literal["gemini"]


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="VED_INFERENCE_", env_file=".env", extra="ignore")

    # Shared secret the backend sends in X-Internal-Api-Key. Empty disables the check (tests only).
    api_key: SecretStr = SecretStr("dev-internal-key")

    engine: EngineName = "rapid"

    # Document limits
    max_upload_bytes: int = 25 * 1024 * 1024
    max_pages: int = 200
    pdf_render_dpi: int = Field(default=200, ge=72, le=400)
    # Concurrent engine runs. Keep at 1 for a single local GPU.
    max_concurrency: int = Field(default=1, ge=1)
    # A PDF page with at least this many letters/digits in its text layer is read natively (no OCR).
    native_text_min_chars: int = Field(default=50, ge=1)

    # LLM used for study features (outline, lessons, questions, grading, tutor)
    llm_provider: LlmProviderName = "gemini"
    llm_timeout_seconds: float = 180.0
    # Retries per model on rate limits / overload (HTTP 429/5xx). Waits follow the server's retryDelay
    # hint (quota windows) or exponential backoff, each capped at 45 s.
    llm_max_retries: int = Field(default=4, ge=0)
    gemini_api_key: SecretStr = SecretStr("")
    gemini_base_url: str = "https://generativelanguage.googleapis.com/v1beta"
    gemini_model: str = "gemini-3.5-flash"
    # Tried in order when the primary model stays unavailable.
    gemini_fallback_models: list[str] = Field(
        default_factory=lambda: ["gemini-3.8-flash", "gemini-3.5-flash-lite"]
    )
    gemini_thinking_level: Literal["minimal", "low", "medium", "high"] = "low"
    # Multimodal embeddings (text and images in one space). Must match the backend's vector(768) column.
    embedding_model: str = "gemini-embedding-2"
    embedding_dimensions: int = 768

    # unlimited-remote: an OpenAI-compatible server (vLLM / SGLang) serving baidu/Unlimited-OCR
    remote_base_url: str = "http://localhost:10000/v1"
    remote_model: str = "Unlimited-OCR"
    remote_api_key: SecretStr = SecretStr("")
    remote_flavor: Literal["sglang", "vllm"] = "sglang"
    remote_timeout_seconds: float = 1200.0
    # Pages sent per request in multi-page mode; long PDFs are split into batches of this size.
    remote_pages_per_request: int = Field(default=8, ge=1)
    # Optional JSON object merged into every chat-completions request body (server-specific knobs).
    remote_extra_body: dict = Field(default_factory=dict)

    # unlimited-local: in-process transformers model (needs the "local" extra and a big enough GPU)
    local_model_id: str = "baidu/Unlimited-OCR"
    local_device: str = "cuda"
    local_pages_per_request: int = Field(default=8, ge=1)


@lru_cache
def get_settings() -> Settings:
    return Settings()
