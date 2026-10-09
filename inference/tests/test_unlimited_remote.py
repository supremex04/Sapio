import json

import httpx
import pytest
from PIL import Image

from ved_inference.config import Settings
from ved_inference.documents.models import PageImage
from ved_inference.engines.unlimited_remote import UnlimitedOcrRemoteEngine
from ved_inference.errors import EngineError


def pages(n: int) -> list[PageImage]:
    return [PageImage(number=i + 1, image=Image.new("RGB", (10, 10), "white")) for i in range(n)]


def engine_with(handler, **overrides) -> tuple[UnlimitedOcrRemoteEngine, list[dict]]:
    requests: list[dict] = []

    def record(request: httpx.Request) -> httpx.Response:
        requests.append(json.loads(request.content))
        return handler(request)

    settings = Settings(remote_base_url="http://ocr.test/v1", **overrides)
    return UnlimitedOcrRemoteEngine(settings, transport=httpx.MockTransport(record)), requests


def reply(text: str):
    return lambda _request: httpx.Response(200, json={"choices": [{"message": {"content": text}}]})


def test_single_page_uses_gundam_mode_and_strips_markup():
    engine, requests = engine_with(reply("<|det|>text [1, 2, 3, 4]<|/det|>Hello"))
    assert engine.extract(pages(1)).segments[0].markdown == "Hello"
    body = requests[0]
    assert body["messages"][0]["content"][0] == {"type": "text", "text": "document parsing."}
    assert body["images_config"] == {"image_mode": "gundam"}
    assert body["temperature"] == 0


def test_long_documents_are_batched_in_multi_page_mode():
    engine, requests = engine_with(reply("part"), remote_pages_per_request=2)
    result = engine.extract(pages(5))
    assert len(requests) == 3
    assert requests[0]["messages"][0]["content"][0]["text"] == "Multi page parsing."
    assert requests[0]["images_config"] == {"image_mode": "base"}
    assert len(requests[0]["messages"][0]["content"]) == 3  # prompt + 2 images
    assert [(s.page_from, s.page_to, s.markdown) for s in result.segments] == [
        (1, 2, "part"),
        (3, 4, "part"),
        (5, 5, "part"),
    ]


def test_vllm_flavor_omits_sglang_fields_and_merges_extra_body():
    engine, requests = engine_with(reply("x"), remote_flavor="vllm", remote_extra_body={"max_tokens": 100})
    engine.extract(pages(1))
    assert "images_config" not in requests[0]
    assert requests[0]["max_tokens"] == 100


def test_server_errors_become_engine_errors():
    engine, _ = engine_with(lambda _r: httpx.Response(500, text="boom"))
    with pytest.raises(EngineError, match="HTTP 500"):
        engine.extract(pages(1))
