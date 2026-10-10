import pytest
from pydantic import ValidationError

from ved_inference.config import Settings, get_settings

YAML = """
providers:
  local: {type: openai, base_url: "http://localhost:11434/v1", json_mode: object}
  gemini: {type: gemini, api_key_env: G_KEY}
llm:
  default:
    primary: {provider: gemini, model: gemini-3.5-flash}
    fallbacks: [{provider: local, model: qwen3}]
  tasks:
    summaries: {primary: {provider: local, model: qwen3}}
embeddings: {provider: local, model: nomic-embed-text, dimensions: 768, multimodal: false}
parsing: {chunk_target_chars: 700}
"""


def test_yaml_file_with_env_overrides(tmp_path, monkeypatch):
    path = tmp_path / "settings.yaml"
    path.write_text(YAML)
    monkeypatch.setenv("VED_INFERENCE_CONFIG", str(path))
    monkeypatch.setenv("VED_INFERENCE_PARSING__CHUNK_TARGET_CHARS", "800")
    monkeypatch.setenv("VED_INFERENCE_LLM__DEFAULT__PRIMARY__MODEL", "gemini-3.8-flash")
    s = Settings()
    assert s.providers["local"].json_mode == "object"
    assert s.llm.route("summaries").primary.model == "qwen3"
    assert s.llm.route("notes").primary.model == "gemini-3.8-flash"  # env beats yaml
    assert s.parsing.chunk_target_chars == 800
    assert s.embeddings.model == "nomic-embed-text"


def test_routes_must_reference_defined_providers(tmp_path, monkeypatch):
    path = tmp_path / "settings.yaml"
    path.write_text("llm:\n  default:\n    primary: {provider: nowhere, model: x}\n")
    monkeypatch.setenv("VED_INFERENCE_CONFIG", str(path))
    with pytest.raises(ValidationError, match="nowhere"):
        Settings()


def test_api_keys_come_from_the_named_environment_variable(monkeypatch, tmp_path):
    (tmp_path / ".env").write_text("VED_INFERENCE_GEMINI_API_KEY=from-dotenv\n")
    monkeypatch.delenv("VED_INFERENCE_GEMINI_API_KEY", raising=False)
    get_settings.cache_clear()
    settings = get_settings()
    assert settings.providers["gemini"].api_key() == "from-dotenv"


def test_shipped_settings_yaml_is_valid(monkeypatch):
    from pathlib import Path

    shipped = Path(__file__).resolve().parents[1] / "settings.yaml"
    monkeypatch.setenv("VED_INFERENCE_CONFIG", str(shipped))
    s = Settings()
    assert s.embeddings.dimensions == 768
    assert s.llm.route("summaries").primary.model == "gemini-3.5-flash-lite"
