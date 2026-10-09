import json

import httpx
import pytest
from pydantic import BaseModel

from ved_inference.config import Settings
from ved_inference.errors import EngineError
from ved_inference.llm.gemini import GeminiClient
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
