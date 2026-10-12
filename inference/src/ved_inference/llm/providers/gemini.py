"""Google Gemini (Generative Language API): generateContent with JSON schema, batchEmbedContents (multimodal)."""

import base64
import json

import httpx
from pydantic import ValidationError

from ved_inference.config import ModelRef, ProviderConfig
from ved_inference.errors import ConfigurationError
from ved_inference.llm.base import EmbedItem, EmbedTask, ImagePart, T
from ved_inference.llm.json_schema import response_schema
from ved_inference.llm.providers.base import Provider
from ved_inference.llm.retry import RETRYABLE_STATUS, ModelUnavailable, Retryable, retry_delay, with_retries

DEFAULT_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"


class GeminiProvider(Provider):
    supports_image_embeddings = True

    def __init__(
        self, name: str, config: ProviderConfig, transport: httpx.BaseTransport | None = None
    ) -> None:
        key = config.api_key()
        if not key:
            raise ConfigurationError(f"Provider '{name}': set the API key in ${config.api_key_env}")
        self._name = name
        self._config = config
        self._client = httpx.Client(
            base_url=(config.base_url or DEFAULT_BASE_URL).rstrip("/"),
            headers={"x-goog-api-key": key, **config.headers},
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
            response = self._post(f"/models/{model.model}:generateContent", body)
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
            response = self._post(f"/models/{model}:batchEmbedContents", {"requests": requests})
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

    def _post(self, path: str, body: dict) -> httpx.Response:
        try:
            response = self._client.post(path, json=body)
        except httpx.HTTPError as exc:
            raise Retryable(str(exc)) from exc
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
