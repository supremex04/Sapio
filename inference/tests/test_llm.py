import json

import httpx
import pytest
from pydantic import BaseModel

from sapio_inference.config import EmbeddingConfig, LlmConfig, ModelRef, ProviderConfig, Route
from sapio_inference.errors import ConfigurationError, EngineError
from sapio_inference.llm.base import EmbedItem, ImagePart
from sapio_inference.llm.json_schema import response_schema
from sapio_inference.llm.providers.gemini import GeminiProvider
from sapio_inference.llm.providers.openai_compatible import OpenAICompatibleProvider
from sapio_inference.llm.router import RoutedEmbedder, RoutedLlm
from sapio_inference.study.models import Outline


class Answer(BaseModel):
    title: str
    value: int


@pytest.fixture(autouse=True)
def _fresh_key_pools(monkeypatch):
    monkeypatch.setattr("sapio_inference.llm.key_pool._pools", {})


@pytest.fixture(autouse=True)
def _no_sleep(monkeypatch):
    sleeps: list[float] = []
    monkeypatch.setattr("sapio_inference.llm.retry.time.sleep", sleeps.append)
    return sleeps


def test_schema_is_self_contained_and_keeps_fields_named_title():
    schema = response_schema(Outline)
    assert "$ref" not in json.dumps(schema)
    topic = schema["properties"]["topics"]["items"]
    assert "title" in topic["properties"] and topic["additionalProperties"] is False
    assert set(topic["required"]) == set(topic["properties"])


# ------------------------------------------------------------------ Gemini


def gemini(handler, monkeypatch, keys="k", **cfg) -> tuple[GeminiProvider, list[httpx.Request]]:
    monkeypatch.setenv("TEST_GEMINI_KEY", keys)
    seen: list[httpx.Request] = []

    def record(request):
        seen.append(request)
        return handler(request)

    config = ProviderConfig(type="gemini", api_key_env="TEST_GEMINI_KEY", max_retries=2, **cfg)
    return GeminiProvider("gemini", config, transport=httpx.MockTransport(record)), seen


def gemini_ok(payload):
    return httpx.Response(200, json={"candidates": [{"content": {"parts": [{"text": json.dumps(payload)}]}}]})


def test_gemini_sends_schema_options_and_images(monkeypatch):
    p, seen = gemini(lambda _r: gemini_ok({"title": "t", "value": 3}), monkeypatch)
    ref = ModelRef(provider="gemini", model="g-1", options={"thinking_level": "low"})
    out = p.generate(
        ref, system="s", prompt="p", schema=Answer, temperature=0.1, images=[ImagePart(b"png", "image/png")]
    )
    body = json.loads(seen[0].content)
    assert out == Answer(title="t", value=3)
    assert (
        seen[0].url.path.endswith("/models/g-1:generateContent") and seen[0].headers["x-goog-api-key"] == "k"
    )
    assert body["generationConfig"]["thinkingConfig"] == {"thinkingLevel": "low"}
    assert body["generationConfig"]["responseJsonSchema"]["required"] == ["title", "value"]
    assert "inlineData" in body["contents"][0]["parts"][0]


def test_gemini_rate_limit_waits_for_server_hint_then_succeeds(monkeypatch, _no_sleep):
    limited = httpx.Response(429, json={"error": {"details": [{"retryDelay": "31s"}]}})
    calls = iter([limited, gemini_ok({"title": "t", "value": 1})])
    p, _ = gemini(lambda _r: next(calls), monkeypatch)
    assert (
        p.generate(
            ModelRef(provider="gemini", model="g"),
            system="s",
            prompt="p",
            schema=Answer,
            temperature=0,
            images=[],
        ).value
        == 1
    )
    assert _no_sleep == [pytest.approx(31.0, abs=0.5)]


def test_gemini_embeds_text_and_images(monkeypatch):
    def handler(request):
        body = json.loads(request.content)
        return httpx.Response(200, json={"embeddings": [{"values": [0.1, 0.2]} for _ in body["requests"]]})

    p, seen = gemini(handler, monkeypatch)
    vectors = p.embed(
        "e-2",
        2,
        [EmbedItem(text="a"), EmbedItem(text="cap", image=ImagePart(b"x", "image/png"))],
        "RETRIEVAL_DOCUMENT",
    )
    req = json.loads(seen[0].content)["requests"][1]
    assert vectors == [[0.1, 0.2]] * 2
    assert req["outputDimensionality"] == 2 and "inlineData" in req["content"]["parts"][0]


def ask(p):
    return p.generate(
        ModelRef(provider="gemini", model="g"),
        system="s",
        prompt="p",
        schema=Answer,
        temperature=0,
        images=[],
    )


def test_gemini_moves_to_the_next_key_when_one_reaches_its_limit(monkeypatch, _no_sleep):
    daily = httpx.Response(
        429,
        json={
            "error": {"details": [{"violations": [{"quotaId": "GenerateRequestsPerDayPerProjectPerModel"}]}]}
        },
    )

    def handler(request):
        return (
            daily if request.headers["x-goog-api-key"] == "key-one" else gemini_ok({"title": "t", "value": 2})
        )

    p, seen = gemini(handler, monkeypatch, keys="key-one, key-two\nkey-three")
    assert ask(p).value == 2
    assert ask(p).value == 2
    # No waiting: the exhausted key is skipped at once, and later requests start with the key that works.
    assert [r.headers["x-goog-api-key"] for r in seen] == ["key-one", "key-two", "key-two"]
    assert _no_sleep == []


def test_gemini_sets_aside_a_refused_key(monkeypatch):
    refused = httpx.Response(403, json={"error": {"message": "Your project has been denied access."}})

    def handler(request):
        return (
            refused
            if request.headers["x-goog-api-key"] == "blocked"
            else gemini_ok({"title": "t", "value": 3})
        )

    p, seen = gemini(handler, monkeypatch, keys="blocked,good")
    assert ask(p).value == 3 and ask(p).value == 3
    assert [r.headers["x-goog-api-key"] for r in seen] == ["blocked", "good", "good"]


def test_gemini_reports_when_every_key_is_refused(monkeypatch):
    invalid = httpx.Response(
        400, json={"error": {"message": "API key not valid.", "details": [{"reason": "API_KEY_INVALID"}]}}
    )
    p, _ = gemini(lambda _r: invalid, monkeypatch, keys="a,b")
    with pytest.raises(ConfigurationError, match="all 2 API keys"):
        ask(p)


def test_gemini_waits_for_the_first_key_back_when_all_are_at_their_limit(monkeypatch, _no_sleep):
    def limited(seconds):
        return httpx.Response(429, json={"error": {"details": [{"retryDelay": f"{seconds}s"}]}})

    answers = iter([limited(20), limited(5), gemini_ok({"title": "t", "value": 4})])
    p, seen = gemini(lambda _r: next(answers), monkeypatch, keys="a,b")
    assert ask(p).value == 4
    assert [r.headers["x-goog-api-key"] for r in seen] == ["a", "b", "b"]
    assert _no_sleep == [pytest.approx(5.0, abs=0.5)]


def test_a_bad_request_is_not_blamed_on_the_key(monkeypatch):
    bad = httpx.Response(400, json={"error": {"message": "Request contains an invalid argument."}})
    p, seen = gemini(lambda _r: bad, monkeypatch, keys="a,b")
    with pytest.raises(ConfigurationError, match="HTTP 400"):
        ask(p)
    assert len(seen) == 1


def test_gemini_requires_its_key():
    with pytest.raises(ConfigurationError, match="MISSING_KEY"):
        GeminiProvider("gemini", ProviderConfig(type="gemini", api_key_env="MISSING_KEY"))


# ------------------------------------------------------------------ OpenAI-compatible


def openai(handler, **cfg) -> tuple[OpenAICompatibleProvider, list[httpx.Request]]:
    seen: list[httpx.Request] = []

    def record(request):
        seen.append(request)
        return handler(request)

    config = ProviderConfig(type="openai", base_url="http://llm.test/v1", max_retries=1, **cfg)
    return OpenAICompatibleProvider("local", config, transport=httpx.MockTransport(record)), seen


def chat_ok(text):
    return httpx.Response(200, json={"choices": [{"message": {"content": text}, "finish_reason": "stop"}]})


def test_openai_strict_schema_vision_and_options():
    p, seen = openai(lambda _r: chat_ok('{"title": "t", "value": 2}'))
    ref = ModelRef(provider="local", model="qwen", options={"max_tokens": 500})
    out = p.generate(
        ref, system="sys", prompt="p", schema=Answer, temperature=0.3, images=[ImagePart(b"png", "image/png")]
    )
    body = json.loads(seen[0].content)
    assert out.value == 2 and seen[0].url.path == "/v1/chat/completions"
    assert (
        body["response_format"]["type"] == "json_schema"
        and body["response_format"]["json_schema"]["strict"] is True
    )
    assert body["messages"][0] == {"role": "system", "content": "sys"}
    assert body["messages"][1]["content"][0]["image_url"]["url"].startswith("data:image/png;base64,")
    assert body["temperature"] == 0.3 and body["max_tokens"] == 500


def test_openai_object_mode_puts_schema_in_prompt_and_strips_fences():
    p, seen = openai(lambda _r: chat_ok('```json\n{"title": "t", "value": 5}\n```'), json_mode="object")
    out = p.generate(
        ModelRef(provider="local", model="m"), system="s", prompt="p", schema=Answer, temperature=0, images=[]
    )
    body = json.loads(seen[0].content)
    assert out.value == 5 and body["response_format"] == {"type": "json_object"}
    assert "JSON Schema" in body["messages"][1]["content"][-1]["text"]


def test_openai_reasoning_models_omit_temperature():
    p, seen = openai(lambda _r: chat_ok('{"title": "t", "value": 1}'))
    ref = ModelRef(provider="local", model="o", options={"reasoning_effort": "low"})
    p.generate(ref, system="s", prompt="p", schema=Answer, temperature=0.5, images=[])
    body = json.loads(seen[0].content)
    assert "temperature" not in body and body["reasoning_effort"] == "low"


def test_openai_embeddings_request_dimensions():
    def handler(_r):
        return httpx.Response(
            200, json={"data": [{"index": 1, "embedding": [2.0]}, {"index": 0, "embedding": [1.0]}]}
        )

    p, seen = openai(handler)
    assert p.embed(
        "text-embedding-3-small", 1, [EmbedItem(text="a"), EmbedItem(text="b")], "RETRIEVAL_QUERY"
    ) == [[1.0], [2.0]]
    assert json.loads(seen[0].content)["dimensions"] == 1


# ------------------------------------------------------------------ routing


class _Flaky(GeminiProvider):
    """Provider double: fails for models listed in ``down``."""

    def __init__(self, name, down, value, retry_after=None):
        self.name, self.down, self.value, self.retry_after = name, down, value, retry_after
        self.supports_image_embeddings = False

    def generate(self, model, **_kw):
        from sapio_inference.llm.retry import ModelUnavailable

        if model.model in self.down:
            raise ModelUnavailable("quota", retry_after=self.retry_after)
        return Answer(title=f"{self.name}:{model.model}", value=self.value)

    def close(self):
        pass


def test_router_uses_task_routes_and_falls_back_across_providers():
    providers = {"a": _Flaky("a", {"big"}, 1), "b": _Flaky("b", set(), 2)}
    config = LlmConfig(
        default=Route(
            primary=ModelRef(provider="a", model="big"), fallbacks=[ModelRef(provider="b", model="small")]
        ),
        tasks={"summaries": Route(primary=ModelRef(provider="a", model="lite"))},
    )
    llm = RoutedLlm(providers, config)
    assert llm.generate(system="s", prompt="p", schema=Answer).title == "b:small"
    assert llm.model_name == "b:small"
    assert llm.generate(system="s", prompt="p", schema=Answer, task="summaries").title == "a:lite"


def test_served_model_is_tracked_per_request_context():
    import contextvars

    providers = {"a": _Flaky("a", {"big"}, 1), "b": _Flaky("b", set(), 2)}
    config = LlmConfig(
        default=Route(
            primary=ModelRef(provider="a", model="big"), fallbacks=[ModelRef(provider="b", model="small")]
        )
    )
    llm = RoutedLlm(providers, config)

    def request():
        llm.generate(system="s", prompt="p", schema=Answer)
        return llm.model_name

    assert contextvars.copy_context().run(request) == "b:small"
    # Another request (context) doesn't see that fallback.
    assert contextvars.Context().run(lambda: llm.model_name) == "a:big"


def test_router_reports_when_every_model_is_unavailable():
    providers = {"a": _Flaky("a", {"m"}, 1)}
    llm = RoutedLlm(providers, LlmConfig(default=Route(primary=ModelRef(provider="a", model="m"))))
    with pytest.raises(EngineError, match="busy or out of quota") as failure:
        llm.generate(system="s", prompt="p", schema=Answer)
    assert failure.value.retryable and failure.value.retry_after is None


def test_router_reports_the_earliest_quota_reset_only_when_every_model_gave_one():
    def fail(providers):
        route = Route(
            primary=ModelRef(provider="a", model="m"), fallbacks=[ModelRef(provider="b", model="m")]
        )
        with pytest.raises(EngineError) as failure:
            RoutedLlm(providers, LlmConfig(default=route)).generate(system="s", prompt="p", schema=Answer)
        return failure.value.retry_after

    assert fail({"a": _Flaky("a", {"m"}, 1, 3600.0), "b": _Flaky("b", {"m"}, 2, 600.0)}) == 600.0
    assert fail({"a": _Flaky("a", {"m"}, 1, 3600.0), "b": _Flaky("b", {"m"}, 2)}) is None


def test_embedder_drops_images_for_text_only_models_and_checks_dimensions():
    class TextOnly(_Flaky):
        def embed(self, model, dimensions, items, task, options=None):
            assert all(i.image is None for i in items)
            return [[0.0] * dimensions for _ in items]

    embedder = RoutedEmbedder(
        TextOnly("t", set(), 0), EmbeddingConfig(provider="t", model="e", dimensions=3, multimodal=True)
    )
    assert embedder.embed(
        [EmbedItem(text="x", image=ImagePart(b"i", "image/png"))], "RETRIEVAL_DOCUMENT"
    ) == [[0.0] * 3]

    class Wrong(_Flaky):
        def embed(self, model, dimensions, items, task, options=None):
            return [[0.0] * 5 for _ in items]

    with pytest.raises(ConfigurationError, match="5 dimensions"):
        RoutedEmbedder(Wrong("w", set(), 0), EmbeddingConfig(provider="w", model="e", dimensions=3)).embed(
            [EmbedItem(text="x")], "RETRIEVAL_QUERY"
        )


def test_a_daily_quota_hint_gives_up_at_once_and_keeps_the_reset_time():
    from sapio_inference.llm.retry import ModelUnavailable, Retryable, with_retries

    def call():
        raise Retryable("HTTP 429", delay=45_000.0)

    with pytest.raises(ModelUnavailable) as failure:
        with_retries(call, label="g:m", max_retries=3, max_backoff=30)
    assert failure.value.retry_after == 45_000.0
