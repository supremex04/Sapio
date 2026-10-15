"""Google Gemini (Generative Language API): generateContent with JSON schema, batchEmbedContents (multimodal).

Several API keys may be configured (one setting, comma separated): one is used until it reaches its limit or is
rejected, then the next (see ``key_pool``).
"""

import base64
import json

import httpx
from pydantic import ValidationError

from sapio_inference.config import ModelRef, ProviderConfig
from sapio_inference.errors import ConfigurationError
from sapio_inference.llm.base import EmbedItem, EmbedTask, ImagePart, T
from sapio_inference.llm.json_schema import response_schema
from sapio_inference.llm.key_pool import (
    DAILY_QUOTA_REST_SECONDS,
    DEFAULT_LIMIT_REST_SECONDS,
    parse_keys,
    shared_pool,
)
from sapio_inference.llm.providers.base import Provider
from sapio_inference.llm.retry import RETRYABLE_STATUS, ModelUnavailable, Retryable, retry_delay, with_retries

DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"


class GeminiProvider(Provider):
    supports_image_embeddings = True

    def __init__(
        self, name: str, config: ProviderConfig, transport: httpx.BaseTransport | None = None
    ) -> None:
        keys = parse_keys(config.api_key())
        if not keys:
            raise ConfigurationError(f"Provider '{name}': set the API key in ${config.api_key_env}")
        self._name = name
        self._config = config
        self._keys = shared_pool(name, keys)
        self._client = httpx.Client(
            base_url=(config.base_url or DEFAULT_BASE_URL).rstrip("/"),
            headers=dict(config.headers),
            timeout=httpx.Timeout(config.timeout_seconds, connect=10.0),
            transport=transport,
        )

    def generate(
        self,
        model: ModelRef,
        *,
        system: str,
        prompt: str,
        schema: type[T],
        temperature: float,
        images: list[ImagePart],
    ) -> T:
        generation: dict = {
            "temperature": temperature,
            "responseMimeType": "application/json",
            "responseJsonSchema": response_schema(schema),
        }
        if level := model.options.get("thinking_level"):
            generation["thinkingConfig"] = {"thinkingLevel": level}
        body = {
            "systemInstruction": {"parts": [{"text": system}]},
            "contents": [{"role": "user", "parts": [*[_inline(i) for i in images], {"text": prompt}]}],
            "generationConfig": generation,
        }

        def call() -> T:
            response = self._post(f"/models/{model.model}:generateContent", body, model.model)
            try:
                candidate = response.json()["candidates"][0]
                parts = candidate.get("content", {}).get("parts", [])
                text = "".join(p.get("text", "") for p in parts if not p.get("thought"))
                if not text:
                    raise Retryable(f"empty response (finishReason={candidate.get('finishReason')})")
                return schema.model_validate(json.loads(text))
            except (KeyError, IndexError, json.JSONDecodeError, ValidationError) as exc:
                raise Retryable(f"invalid structured output: {exc}") from exc

        return with_retries(
            call,
            label=f"{self._name}:{model.model}",
            max_retries=self._config.max_retries,
            max_backoff=self._config.max_backoff_seconds,
        )

    def embed(
        self,
        model: str,
        dimensions: int,
        items: list[EmbedItem],
        task: EmbedTask,
        options: dict | None = None,
    ) -> list[list[float]]:
        requests = []
        for item in items:
            parts: list[dict] = [_inline(item.image)] if item.image is not None else []
            if item.text:
                parts.append({"text": item.text[:8000]})
            requests.append(
                {
                    "model": f"models/{model}",
                    "content": {"parts": parts or [{"text": " "}]},
                    "taskType": task,
                    "outputDimensionality": dimensions,
                }
            )

        def call() -> list[list[float]]:
            response = self._post(f"/models/{model}:batchEmbedContents", {"requests": requests}, model)
            try:
                vectors = [e["values"] for e in response.json()["embeddings"]]
            except (KeyError, ValueError) as exc:
                raise Retryable(f"invalid embedding response: {exc}") from exc
            if len(vectors) != len(items):
                raise Retryable("embedding count mismatch")
            return vectors

        return with_retries(
            call,
            label=f"{self._name}:{model}",
            max_retries=self._config.max_retries,
            max_backoff=self._config.max_backoff_seconds,
        )

    def _post(self, path: str, body: dict, model: str) -> httpx.Response:
        """Posts with the current key; a key at its limit or rejected hands the request to the next one."""
        while True:
            picked = self._keys.pick(model)
            if picked is None:
                if self._keys.all_rejected():
                    raise ConfigurationError(
                        f"AI request rejected: all {len(self._keys)} API keys were refused"
                    )
                # Every key is resting: wait for the first to come back (with_retries waits, or gives up on long
                # waits so the router can fall back or report when to retry).
                raise Retryable(f"all {len(self._keys)} API keys at their limit", self._keys.soonest(model))
            index, key = picked
            try:
                response = self._client.post(path, json=body, headers={"x-goog-api-key": key})
            except httpx.HTTPError as exc:
                raise Retryable(str(exc)) from exc
            if response.status_code == 429:
                self._keys.limited(index, model, _rest_seconds(response))
                continue
            if _key_refused(response):
                self._keys.rejected(index, f"HTTP {response.status_code}: {_error_message(response)}")
                continue
            if response.status_code in RETRYABLE_STATUS:
                raise Retryable(f"HTTP {response.status_code}", retry_delay(response))
            if response.status_code == 404:
                raise ModelUnavailable(f"{self._name}: model not found ({path})")
            if response.status_code >= 400:
                raise ConfigurationError(
                    f"AI request rejected (HTTP {response.status_code}): {_error_message(response)}"
                )
            return response

    def close(self) -> None:
        self._client.close()


def _inline(image: ImagePart) -> dict:
    return {"inlineData": {"mimeType": image.mime, "data": base64.b64encode(image.data).decode("ascii")}}


def _error_message(response: httpx.Response) -> str:
    try:
        return response.json()["error"]["message"]
    except (ValueError, KeyError, TypeError):
        return response.text[:200]


def _rest_seconds(response: httpx.Response) -> float:
    """How long a key rests after a 429: an hour for a daily quota, else the server's hint (or a short default)."""
    try:
        for detail in response.json().get("error", {}).get("details", []):
            for violation in detail.get("violations", []) if isinstance(detail, dict) else []:
                if "PerDay" in str(violation.get("quotaId", "")):
                    return DAILY_QUOTA_REST_SECONDS
    except (ValueError, AttributeError):
        pass
    hint = retry_delay(response)
    return hint if hint is not None else DEFAULT_LIMIT_REST_SECONDS


def _key_refused(response: httpx.Response) -> bool:
    """The key itself was refused (invalid, expired, its project blocked), not the request."""
    if response.status_code in (401, 403):
        return True
    if response.status_code != 400:
        return False
    try:
        error = response.json().get("error", {})
        reasons = {d.get("reason") for d in error.get("details", []) if isinstance(d, dict)}
        return "API_KEY_INVALID" in reasons or "API key" in str(error.get("message", ""))
    except (ValueError, AttributeError):
        return False
