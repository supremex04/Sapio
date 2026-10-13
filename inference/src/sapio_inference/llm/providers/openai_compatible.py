"""Any OpenAI-compatible API: OpenAI, Azure/OpenRouter-style gateways, Groq, Together, vLLM, LM Studio, Ollama.

Chat completions with structured output (``response_format: json_schema``, or ``json_object`` + the schema
in the prompt for servers without schema support), ``image_url`` data URIs for vision, ``/embeddings`` for
text embeddings (``dimensions`` is sent so models like text-embedding-3-* match the database column).
"""

import base64
import json

import httpx
from pydantic import ValidationError

from sapio_inference.config import ModelRef, ProviderConfig
from sapio_inference.errors import ConfigurationError
from sapio_inference.llm.base import EmbedItem, EmbedTask, ImagePart, T
from sapio_inference.llm.json_schema import response_schema
from sapio_inference.llm.providers.base import Provider
from sapio_inference.llm.retry import RETRYABLE_STATUS, ModelUnavailable, Retryable, retry_delay, with_retries

DEFAULT_BASE_URL = "https://api.openai.com/v1"


class OpenAICompatibleProvider(Provider):
    supports_image_embeddings = False

    def __init__(
        self, name: str, config: ProviderConfig, transport: httpx.BaseTransport | None = None
    ) -> None:
        headers = dict(config.headers)
        if key := config.api_key():
            headers["Authorization"] = f"Bearer {key}"
        self._name = name
        self._config = config
        self._client = httpx.Client(
            base_url=(config.base_url or DEFAULT_BASE_URL).rstrip("/"),
            headers=headers,
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
        json_schema = response_schema(schema)
        if self._config.json_mode == "schema":
            response_format: dict = {
                "type": "json_schema",
                "json_schema": {"name": schema.__name__, "schema": json_schema, "strict": True},
            }
            user_text = prompt
        else:
            response_format = {"type": "json_object"}
            user_text = f"{prompt}\n\nRespond with a single JSON object matching this JSON Schema:\n{json.dumps(json_schema)}"
        content: list[dict] = [
            {
                "type": "image_url",
                "image_url": {"url": f"data:{i.mime};base64,{base64.b64encode(i.data).decode('ascii')}"},
            }
            for i in images
        ]
        content.append({"type": "text", "text": user_text})
        body: dict = {
            "model": model.model,
            "messages": [{"role": "system", "content": system}, {"role": "user", "content": content}],
            "response_format": response_format,
            **model.options,  # e.g. reasoning_effort, max_tokens, top_p
        }
        if "reasoning_effort" not in model.options:
            body["temperature"] = temperature  # reasoning models reject temperature

        def call() -> T:
            response = self._post("/chat/completions", body)
            try:
                choice = response.json()["choices"][0]
                text = choice["message"].get("content") or ""
                if not text:
                    raise Retryable(f"empty response (finish_reason={choice.get('finish_reason')})")
                return schema.model_validate(json.loads(_strip_fences(text)))
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
        texts = [(i.text or " ")[:8000] for i in items]  # images are not supported by /embeddings

        def call() -> list[list[float]]:
            response = self._post("/embeddings", {"model": model, "input": texts, "dimensions": dimensions})
            try:
                data = sorted(response.json()["data"], key=lambda d: d["index"])
                vectors = [d["embedding"] for d in data]
            except (KeyError, ValueError, TypeError) as exc:
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
        if response.status_code == 429 and _error_code(response) == "insufficient_quota":
            raise ModelUnavailable(f"{self._name}: quota exhausted")
        if response.status_code in RETRYABLE_STATUS:
            raise Retryable(f"HTTP {response.status_code}", retry_delay(response))
        if response.status_code == 404:
            raise ModelUnavailable(f"{self._name}: model not found")
        if response.status_code >= 400:
            raise ConfigurationError(
                f"AI request rejected (HTTP {response.status_code}): {response.text[:200]}"
            )
        return response

    def close(self) -> None:
        self._client.close()


def _error_code(response: httpx.Response) -> str | None:
    try:
        return response.json().get("error", {}).get("code")
    except (ValueError, AttributeError):
        return None


def _strip_fences(text: str) -> str:
    """Some servers wrap JSON in ```json fences even in JSON mode."""
    t = text.strip()
    if t.startswith("```"):
        t = t.split("\n", 1)[1] if "\n" in t else ""
        t = t.rsplit("```", 1)[0]
    return t.strip()
