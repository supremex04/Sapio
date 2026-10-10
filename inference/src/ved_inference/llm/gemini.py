"""Google Gemini (Generative Language API, ``generateContent``) with JSON-schema output."""

import base64
import json
import logging
import time

import httpx
from pydantic import ValidationError

from ved_inference.config import Settings
from ved_inference.errors import EngineError
from ved_inference.llm.base import Embedder, EmbedItem, EmbedTask, ImagePart, LlmClient, T
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

    def generate(
        self,
        *,
        system: str,
        prompt: str,
        schema: type[T],
        temperature: float = 0.4,
        images: list[ImagePart] | None = None,
    ) -> T:
        parts: list[dict] = [_inline(img) for img in images or []]
        parts.append({"text": prompt})
        body = {
            "systemInstruction": {"parts": [{"text": system}]},
            "contents": [{"role": "user", "parts": parts}],
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
                        time.sleep(_backoff(attempt, exc))
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
            raise _Retryable(f"HTTP {response.status_code}", _retry_delay(response))
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


class GeminiEmbedder(Embedder):
    """``gemini-embedding-2`` (multimodal): text and images in one 768-d space, batched."""

    BATCH = 64

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
        self._model = settings.embedding_model
        self._dims = settings.embedding_dimensions
        self._max_retries = settings.llm_max_retries

    @property
    def model_name(self) -> str:
        return self._model

    @property
    def dimensions(self) -> int:
        return self._dims

    def embed(self, items: list[EmbedItem], task: EmbedTask) -> list[list[float]]:
        vectors: list[list[float]] = []
        for i in range(0, len(items), self.BATCH):
            vectors.extend(self._batch(items[i : i + self.BATCH], task))
        return vectors

    def _batch(self, items: list[EmbedItem], task: EmbedTask) -> list[list[float]]:
        requests = []
        for item in items:
            parts: list[dict] = []
            if item.image is not None:
                parts.append(_inline(item.image))
            if item.text:
                parts.append({"text": item.text[:8000]})
            if not parts:
                parts.append({"text": " "})
            requests.append(
                {
                    "model": f"models/{self._model}",
                    "content": {"parts": parts},
                    "taskType": task,
                    "outputDimensionality": self._dims,
                }
            )
        last: Exception | None = None
        for attempt in range(self._max_retries + 1):
            try:
                response = self._client.post(
                    f"/models/{self._model}:batchEmbedContents", json={"requests": requests}
                )
                if response.status_code in _RETRYABLE:
                    raise _Retryable(f"HTTP {response.status_code}", _retry_delay(response))
                if response.status_code >= 400:
                    raise EngineError(
                        f"Embedding request rejected (HTTP {response.status_code}): {_error_message(response)}"
                    )
                vectors = [e["values"] for e in response.json()["embeddings"]]
                if len(vectors) != len(items):
                    raise _Retryable("embedding count mismatch")
                return vectors
            except (_Retryable, httpx.HTTPError, KeyError, ValueError) as exc:
                last = exc
                log.warning("Embedding attempt %d failed: %s", attempt + 1, exc)
                if attempt < self._max_retries:
                    time.sleep(_backoff(attempt, exc))
        raise EngineError("The embedding service is busy right now. Please try again in a moment.") from last

    def close(self) -> None:
        self._client.close()


def _inline(image: ImagePart) -> dict:
    return {"inlineData": {"mimeType": image.mime, "data": base64.b64encode(image.data).decode("ascii")}}


class _Retryable(Exception):
    def __init__(self, message: str, delay: float | None = None) -> None:
        super().__init__(message)
        self.delay = delay


MAX_BACKOFF_SECONDS = 45.0


def _backoff(attempt: int, error: Exception) -> float:
    """Server hint (rate-limit window) if given, else exponential; always bounded."""
    hinted = getattr(error, "delay", None)
    return min(hinted if hinted is not None else 2.0 ** (attempt + 1), MAX_BACKOFF_SECONDS)


def _retry_delay(response: httpx.Response) -> float | None:
    """Reads google.rpc.RetryInfo ("retryDelay": "31s") or a Retry-After header."""
    header = response.headers.get("retry-after")
    if header and header.isdigit():
        return float(header)
    try:
        for detail in response.json().get("error", {}).get("details", []):
            delay = detail.get("retryDelay")
            if isinstance(delay, str) and delay.endswith("s"):
                return float(delay[:-1])
    except (ValueError, AttributeError):
        pass
    return None


class _ModelUnavailable(Exception):
    pass


def _error_message(response: httpx.Response) -> str:
    try:
        return response.json()["error"]["message"]
    except (ValueError, KeyError, TypeError):
        return response.text[:200]
