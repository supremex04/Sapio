"""Google Gemini (Generative Language API, ``generateContent``) with JSON-schema output."""

import json
import logging
import time

import httpx
from pydantic import ValidationError

from ved_inference.config import Settings
from ved_inference.errors import EngineError
from ved_inference.llm.base import LlmClient, T
from ved_inference.llm.json_schema import response_schema

log = logging.getLogger(__name__)

_RETRYABLE = {429, 500, 502, 503, 504}


class GeminiClient(LlmClient):
    def __init__(self, settings: Settings, transport: httpx.BaseTransport | None = None) -> None:
        key = settings.gemini_api_key.get_secret_value()
        if not key:
            raise EngineError("VED_INFERENCE_GEMINI_API_KEY is not set")
        self._client = httpx.Client(
            base_url=settings.gemini_base_url.rstrip("/"),
            headers={"x-goog-api-key": key},
            timeout=httpx.Timeout(settings.llm_timeout_seconds, connect=10.0),
            transport=transport,
        )
        self._models = [settings.gemini_model, *settings.gemini_fallback_models]
        self._thinking_level = settings.gemini_thinking_level
        self._max_retries = settings.llm_max_retries
        self._last_model = settings.gemini_model

    @property
    def model_name(self) -> str:
        return self._last_model

    def generate(self, *, system: str, prompt: str, schema: type[T], temperature: float = 0.4) -> T:
        body = {
            "systemInstruction": {"parts": [{"text": system}]},
            "contents": [{"role": "user", "parts": [{"text": prompt}]}],
            "generationConfig": {
                "temperature": temperature,
                "responseMimeType": "application/json",
                "responseJsonSchema": response_schema(schema),
                "thinkingConfig": {"thinkingLevel": self._thinking_level},
            },
        }
        last_error: Exception | None = None
        for model in self._models:
            for attempt in range(self._max_retries + 1):
                try:
                    result = self._call(model, body, schema)
                    self._last_model = model
                    return result
                except _Retryable as exc:
                    last_error = exc
                    log.warning("Gemini %s attempt %d failed: %s", model, attempt + 1, exc)
                    if attempt < self._max_retries:
                        time.sleep(min(2**attempt, 8))
                except _ModelUnavailable as exc:
                    last_error = exc
                    log.warning("Gemini model %s unavailable: %s", model, exc)
                    break
        raise EngineError("The AI service is busy right now. Please try again in a moment.") from last_error

    def _call(self, model: str, body: dict, schema: type[T]) -> T:
        try:
            response = self._client.post(f"/models/{model}:generateContent", json=body)
        except httpx.HTTPError as exc:
            raise _Retryable(str(exc)) from exc
        if response.status_code in _RETRYABLE:
            raise _Retryable(f"HTTP {response.status_code}")
        if response.status_code == 404:
            raise _ModelUnavailable("HTTP 404")
        if response.status_code >= 400:
            raise EngineError(
                f"AI request rejected (HTTP {response.status_code}): {_error_message(response)}"
            )
        try:
            payload = response.json()
            candidate = payload["candidates"][0]
            parts = candidate.get("content", {}).get("parts", [])
            text = "".join(p.get("text", "") for p in parts if not p.get("thought"))
            if not text:
                raise _Retryable(f"empty response (finishReason={candidate.get('finishReason')})")
            return schema.model_validate(json.loads(text))
        except (KeyError, IndexError, json.JSONDecodeError, ValidationError) as exc:
            raise _Retryable(f"invalid structured output: {exc}") from exc

    def close(self) -> None:
        self._client.close()


class _Retryable(Exception):
    pass


class _ModelUnavailable(Exception):
    pass


def _error_message(response: httpx.Response) -> str:
    try:
        return response.json()["error"]["message"]
    except (ValueError, KeyError, TypeError):
        return response.text[:200]
