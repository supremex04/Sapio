import json

import httpx
import pytest
from pydantic import BaseModel

from ved_inference.config import Settings
from ved_inference.errors import EngineError
from ved_inference.llm.base import EmbedItem, ImagePart
from ved_inference.llm.gemini import GeminiClient, GeminiEmbedder
from ved_inference.llm.json_schema import response_schema
from ved_inference.study.models import Outline


class Answer(BaseModel):
    title: str
    value: int


def test_schema_is_self_contained_and_keeps_fields_named_title():
    schema = response_schema(Outline)
    text = json.dumps(schema)
    assert "$ref" not in text and "$defs" not in text
    assert "title" in schema["properties"]
    topic = schema["properties"]["topics"]["items"]
    assert "title" in topic["properties"] and topic["additionalProperties"] is False
    assert set(topic["required"]) == set(topic["properties"])


def client(handler, **settings) -> tuple[GeminiClient, list[str]]:
    urls: list[str] = []

    def record(request: httpx.Request) -> httpx.Response:
        urls.append(request.url.path)
        return handler(request)

    s = Settings(gemini_api_key="k", llm_max_retries=1, gemini_fallback_models=["backup"], **settings)
    return GeminiClient(s, transport=httpx.MockTransport(record)), urls


def ok(payload: dict) -> httpx.Response:
    return httpx.Response(200, json={"candidates": [{"content": {"parts": [{"text": json.dumps(payload)}]}}]})


def test_parses_structured_output_and_sends_schema(monkeypatch):
    seen = {}

    def handler(request):
        seen["body"] = json.loads(request.content)
        seen["key"] = request.headers["x-goog-api-key"]
        return ok({"title": "t", "value": 3})

    c, _ = client(handler)
    assert c.generate(system="s", prompt="p", schema=Answer) == Answer(title="t", value=3)
    config = seen["body"]["generationConfig"]
    assert config["responseMimeType"] == "application/json"
    assert config["responseJsonSchema"]["required"] == ["title", "value"]
    assert seen["key"] == "k"


def test_retries_then_falls_back_to_next_model(monkeypatch):
    monkeypatch.setattr("ved_inference.llm.gemini.time.sleep", lambda _s: None)
    calls = iter([httpx.Response(503), httpx.Response(503), ok({"title": "b", "value": 1})])
    c, urls = client(lambda _r: next(calls))
    assert c.generate(system="s", prompt="p", schema=Answer).title == "b"
    assert urls[-1].endswith("/models/backup:generateContent")
    assert c.model_name == "backup"


def test_invalid_output_is_retried_and_finally_raises(monkeypatch):
    monkeypatch.setattr("ved_inference.llm.gemini.time.sleep", lambda _s: None)
    c, urls = client(lambda _r: ok({"unexpected": True}))
    with pytest.raises(EngineError):
        c.generate(system="s", prompt="p", schema=Answer)
    assert len(urls) == 4  # 2 attempts x 2 models


def test_client_errors_are_not_retried():
    c, urls = client(lambda _r: httpx.Response(400, json={"error": {"message": "bad request"}}))
    with pytest.raises(EngineError, match="bad request"):
        c.generate(system="s", prompt="p", schema=Answer)
    assert len(urls) == 1


def test_missing_key_fails_fast():
    with pytest.raises(EngineError):
        GeminiClient(Settings(gemini_api_key=""))


def test_generate_sends_images_before_the_prompt():
    seen = {}

    def handler(request):
        seen["parts"] = json.loads(request.content)["contents"][0]["parts"]
        return ok({"title": "t", "value": 1})

    c, _ = client(handler)
    c.generate(
        system="s", prompt="describe", schema=Answer, images=[ImagePart(data=b"png", mime="image/png")]
    )
    assert "inlineData" in seen["parts"][0] and seen["parts"][1] == {"text": "describe"}


def test_embedder_batches_multimodal_items():
    bodies = []

    def handler(request):
        body = json.loads(request.content)
        bodies.append(body)
        return httpx.Response(200, json={"embeddings": [{"values": [0.1, 0.2]} for _ in body["requests"]]})

    s = Settings(gemini_api_key="k", embedding_dimensions=2)
    embedder = GeminiEmbedder(s, transport=httpx.MockTransport(handler))
    embedder.BATCH = 2
    vectors = embedder.embed(
        [EmbedItem(text="a"), EmbedItem(image=ImagePart(b"x", "image/png"), text="cap"), EmbedItem(text="b")],
        "RETRIEVAL_DOCUMENT",
    )
    assert vectors == [[0.1, 0.2]] * 3
    assert len(bodies) == 2
    first = bodies[0]["requests"][1]
    assert first["taskType"] == "RETRIEVAL_DOCUMENT" and first["outputDimensionality"] == 2
    assert "inlineData" in first["content"]["parts"][0] and first["content"]["parts"][1] == {"text": "cap"}


def test_rate_limits_wait_for_the_servers_retry_delay(monkeypatch):
    sleeps = []
    monkeypatch.setattr("ved_inference.llm.gemini.time.sleep", sleeps.append)
    limited = httpx.Response(
        429,
        json={
            "error": {
                "code": 429,
                "details": [{"@type": "type.googleapis.com/google.rpc.RetryInfo", "retryDelay": "31s"}],
            }
        },
    )
    calls = iter(
        [limited, httpx.Response(503, headers={"retry-after": "120"}), ok({"title": "t", "value": 1})]
    )
    s = Settings(gemini_api_key="k", llm_max_retries=3, gemini_fallback_models=[])
    c = GeminiClient(s, transport=httpx.MockTransport(lambda _r: next(calls)))
    assert c.generate(system="s", prompt="p", schema=Answer).title == "t"
    assert sleeps == [31.0, 45.0]  # server hint, then capped
