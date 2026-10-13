"""Service configuration.

Sources, highest priority first:
  1. environment variables, prefix ``SAPIO_INFERENCE_``, nested with ``__``
     (e.g. ``SAPIO_INFERENCE_LLM__DEFAULT__PRIMARY__MODEL=gpt-4.1-mini``)
  2. ``.env`` in the working directory (same names; also loaded into the process for ``api_key_env`` lookups)
  3. ``settings.yaml`` (path overridable with ``SAPIO_INFERENCE_CONFIG``): providers, model routes, tunings
  4. the defaults below

Secrets never live in ``settings.yaml``: a provider names the environment variable that holds its key
(``api_key_env``). See docs/guides/configuration.md.
"""

import os
from functools import lru_cache
from pathlib import Path
from typing import Any, Literal

from dotenv import load_dotenv
from pydantic import BaseModel, Field, SecretStr, model_validator
from pydantic_settings import (
    BaseSettings,
    InitSettingsSource,
    PydanticBaseSettingsSource,
    SettingsConfigDict,
    YamlConfigSettingsSource,
)

EngineName = Literal["rapid", "unlimited-remote", "unlimited-local"]
# "openai" = any OpenAI-compatible API (OpenAI, vLLM, Ollama, …); "onnx" = embedding models run in-process (CPU)
ProviderType = Literal["gemini", "openai", "onnx"]
TaskName = Literal[
    "outline",
    "lesson",
    "questions",
    "grade",
    "feedback",
    "answer",
    "notes",
    "figures",
    "summaries",
    "lecture_notes",
]
TASKS: tuple[str, ...] = TaskName.__args__  # type: ignore[attr-defined]


class ProviderConfig(BaseModel):
    """A model vendor / API endpoint. Add as many as you like and reference them by name."""

    type: ProviderType
    base_url: str | None = None  # default per type (Gemini public API / api.openai.com)
    api_key_env: str | None = None  # NAME of the environment variable holding the key
    timeout_seconds: float = 180.0
    max_retries: int = Field(default=4, ge=0)  # per model, on 429 / 5xx
    max_backoff_seconds: float = (
        45.0  # cap per wait; a longer server hint means "quota exhausted" → fall back
    )
    headers: dict[str, str] = Field(default_factory=dict)  # extra HTTP headers (gateways, org ids)
    json_mode: Literal["schema", "object"] = (
        "schema"  # openai: strict JSON schema, or json_object + schema in prompt
    )

    def api_key(self) -> str:
        return os.environ.get(self.api_key_env, "") if self.api_key_env else ""


class ModelRef(BaseModel):
    provider: str
    model: str
    # Provider-specific generation options, e.g. Gemini {"thinking_level": "low"}, OpenAI {"reasoning_effort": "low"}.
    options: dict[str, Any] = Field(default_factory=dict)


class Route(BaseModel):
    """Primary model plus fallbacks (tried in order when a model is unavailable or out of quota)."""

    primary: ModelRef
    fallbacks: list[ModelRef] = Field(default_factory=list)

    def chain(self) -> list[ModelRef]:
        return [self.primary, *self.fallbacks]


class LlmConfig(BaseModel):
    default: Route
    # Optional per-task overrides (e.g. a cheaper model for summaries, a stronger one for notes).
    tasks: dict[TaskName, Route] = Field(default_factory=dict)

    def route(self, task: str) -> Route:
        return self.tasks.get(task, self.default)  # type: ignore[call-overload]


class EmbeddingConfig(BaseModel):
    provider: str
    model: str
    # Must equal the backend's vector column (V4 migration: vector(768)); changing it needs a migration.
    dimensions: int = 768
    # True: figures are embedded from the image itself (+ caption); False: from caption + description text.
    multimodal: bool = True
    batch_size: int = Field(default=64, ge=1)
    # Model-specific settings for in-process (onnx) models: file, pooling, query instruction… (see OnnxEmbeddingOptions)
    options: dict[str, Any] = Field(default_factory=dict)


class RemoteOcrConfig(BaseModel):
    """unlimited-remote: an OpenAI-compatible server (SGLang / vLLM) serving baidu/Unlimited-OCR."""

    base_url: str = "http://localhost:10000/v1"
    model: str = "Unlimited-OCR"
    api_key_env: str | None = None
    flavor: Literal["sglang", "vllm"] = "sglang"
    timeout_seconds: float = 1200.0
    pages_per_request: int = Field(default=8, ge=1)
    extra_body: dict[str, Any] = Field(default_factory=dict)


class LocalOcrConfig(BaseModel):
    """unlimited-local: in-process transformers model (needs the "local" extra and a big enough GPU)."""

    model_id: str = "baidu/Unlimited-OCR"
    device: str = "cuda"
    pages_per_request: int = Field(default=8, ge=1)


class OcrConfig(BaseModel):
    engine: EngineName = "rapid"  # rapid = RapidOCR inside the layout model (CPU)
    remote: RemoteOcrConfig = Field(default_factory=RemoteOcrConfig)
    local: LocalOcrConfig = Field(default_factory=LocalOcrConfig)


class FigureFilterConfig(BaseModel):
    max_side_px: int = 1600  # figures are downscaled to this
    min_side_px: int = 100
    min_page_area_fraction: float = 0.012
    max_aspect: float = 6.0
    repeat_pages: int = 3  # the same SMALL image on this many pages is a logo/ornament
    logo_max_area_fraction: float = 0.08  # repeated images larger than this are reused content, not logos
    background_area_fraction: float = 0.85
    max_figures: int = 400


class ParsingConfig(BaseModel):
    max_upload_bytes: int = 25 * 1024 * 1024
    max_pages: int = 200
    dpi: int = Field(default=200, ge=72, le=400)
    max_concurrency: int = Field(default=1, ge=1)  # concurrent parses (keep 1 on a single GPU)
    native_min_chars: int = Field(
        default=50, ge=1
    )  # letters/digits for a page to count as having a text layer
    chunk_target_chars: int = 900  # leaf chunk size (~150-250 tokens)
    chunk_max_chars: int = 1400
    max_depth: int = 4
    max_heading_chars: int = 120
    pages_per_fallback_node: int = 5  # documents without headings get one node per N pages
    figures: FigureFilterConfig = Field(default_factory=FigureFilterConfig)


class IndexingConfig(BaseModel):
    figures_per_call: int = Field(default=6, ge=1)  # images per vision request
    nodes_per_call: int = Field(default=12, ge=1)  # sections per summary request
    node_text_chars: int = 6_000  # text per section sent for summarising
    transcript_chars: int = 2_000  # max text kept from a text-only image (terminal, code)


class SpeechConfig(BaseModel):
    """Speech-to-text for audio and video uploads (lectures, recorded courses).

    faster-whisper runs OpenAI's open Whisper models on CTranslate2: multilingual, punctuated, with timestamps,
    on any CPU (Linux, macOS Intel and Apple Silicon, Windows) or an NVIDIA GPU when present. "small" is the
    measured sweet spot: ~7x real time on an 8-core CPU, ~38x on a laptop GPU, far fewer errors than "base".
    """

    engine: Literal["faster-whisper"] = "faster-whisper"
    #: tiny | base | small | medium | large-v3 | distil-large-v3 | a local path / Hugging Face repo id
    model: str = "small"
    #: auto = GPU (CUDA) when available, else CPU
    device: Literal["auto", "cpu", "cuda"] = "auto"
    #: auto = int8_float16 on GPU, int8 on CPU
    compute_type: str = "auto"
    cpu_threads: int = Field(default=0, ge=0)  # 0 = CTranslate2 default
    beam_size: int = Field(default=1, ge=1, le=10)  # 1 = greedy (fastest); 5 = a little more accurate
    #: Force a language ("en", "ne", "hi"…); None detects it from the first minute
    language: str | None = None
    #: Audio is decoded and transcribed in windows of this length, so memory stays flat for long recordings
    window_seconds: int = Field(default=600, ge=60)
    max_duration_seconds: int = Field(default=4 * 3600, ge=60)
    max_upload_bytes: int = 1024 * 1024 * 1024  # 1 GiB (video); the backend enforces its own limits too
    #: Concurrent transcriptions per process (each holds its audio window and the model's working memory)
    max_concurrency: int = Field(default=1, ge=1)


class LectureNotesConfig(BaseModel):
    """Turning a transcript into study notes (LLM task "lecture_notes")."""

    #: Transcript sent per notes request; long lectures are processed window by window
    window_seconds: int = Field(default=900, ge=120)
    #: Spoken words per minute is ~130-160; a window of 15 min is ~2.5k words, well inside any model's context
    min_words: int = Field(default=40, ge=1)  # less speech than this is rejected as "no speech"


class StudyConfig(BaseModel):
    max_topics: int = 12
    outline_summary_chars: int = 400


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="SAPIO_INFERENCE_", env_nested_delimiter="__", env_file=".env", extra="ignore"
    )

    # Shared secret the backend sends in X-Internal-Api-Key. Empty disables the check (tests only).
    api_key: SecretStr = SecretStr("dev-internal-key")

    providers: dict[str, ProviderConfig] = Field(
        default_factory=lambda: {
            "gemini": ProviderConfig(type="gemini", api_key_env="SAPIO_INFERENCE_GEMINI_API_KEY")
        }
    )
    llm: LlmConfig = Field(
        default_factory=lambda: LlmConfig(
            default=Route(
                primary=ModelRef(
                    provider="gemini", model="gemini-3.5-flash", options={"thinking_level": "low"}
                ),
                fallbacks=[
                    ModelRef(
                        provider="gemini", model="gemini-3.5-flash-lite", options={"thinking_level": "low"}
                    )
                ],
            )
        )
    )
    embeddings: EmbeddingConfig = Field(
        default_factory=lambda: EmbeddingConfig(provider="gemini", model="gemini-embedding-2")
    )
    ocr: OcrConfig = Field(default_factory=OcrConfig)
    parsing: ParsingConfig = Field(default_factory=ParsingConfig)
    indexing: IndexingConfig = Field(default_factory=IndexingConfig)
    study: StudyConfig = Field(default_factory=StudyConfig)
    speech: SpeechConfig = Field(default_factory=SpeechConfig)
    lecture_notes: LectureNotesConfig = Field(default_factory=LectureNotesConfig)

    @model_validator(mode="after")
    def _routes_reference_known_providers(self) -> "Settings":
        refs = [self.embeddings.provider]
        for route in [self.llm.default, *self.llm.tasks.values()]:
            refs += [m.provider for m in route.chain()]
        unknown = sorted({r for r in refs if r not in self.providers})
        if unknown:
            raise ValueError(f"Unknown provider(s) {unknown}; define them under 'providers'")
        return self

    @classmethod
    def settings_customise_sources(
        cls,
        settings_cls: type[BaseSettings],
        init_settings: PydanticBaseSettingsSource,
        env_settings: PydanticBaseSettingsSource,
        dotenv_settings: PydanticBaseSettingsSource,
        file_secret_settings: PydanticBaseSettingsSource,
    ) -> tuple[PydanticBaseSettingsSource, ...]:
        sources: list[PydanticBaseSettingsSource] = [init_settings, env_settings, dotenv_settings]
        files = config_files()
        if files:
            sources.append(LayeredYamlSource(settings_cls, files))
        return tuple(sources)


def config_files() -> list[Path]:
    """``SAPIO_INFERENCE_CONFIG``: one file or a comma-separated list (base first, overlays after)."""
    spec = os.environ.get("SAPIO_INFERENCE_CONFIG", "settings.yaml")
    paths = [Path(p.strip()) for p in spec.split(",") if p.strip()]
    missing = [p for p in paths[1:] if not p.is_file()]
    if missing:
        raise FileNotFoundError(f"Config overlay not found: {', '.join(map(str, missing))}")
    return [p for p in paths if p.is_file()]


def deep_merge(base: dict, overlay: dict) -> dict:
    """Overlay wins; nested mappings merge key by key (so an overlay can add one provider or change one setting)."""
    out = dict(base)
    for key, value in overlay.items():
        out[key] = (
            deep_merge(out[key], value)
            if isinstance(value, dict) and isinstance(out.get(key), dict)
            else value
        )
    return out


class LayeredYamlSource(InitSettingsSource):
    """settings.yaml plus optional overlays (e.g. settings.local-embedding.yaml), deep-merged in order."""

    def __init__(self, settings_cls: type[BaseSettings], files: list[Path]) -> None:
        merged: dict[str, Any] = {}
        for path in files:
            merged = deep_merge(merged, YamlConfigSettingsSource(settings_cls, yaml_file=path).init_kwargs)
        super().__init__(settings_cls, init_kwargs=merged)


@lru_cache
def get_settings() -> Settings:
    load_dotenv(".env", override=False)  # so providers' api_key_env can be set in .env
    return Settings()
