from fastapi.testclient import TestClient

from tests.conftest import textbook_pdf
from tests.fakes import FakeEmbedder, FakeLlm
from ved_inference.main import create_app

KEY = {"X-Internal-Api-Key": "test-key"}


def client(llm=None) -> TestClient:
    return TestClient(
        create_app(
            engine_factory=lambda _s: None,
            llm_factory=lambda _s: llm or FakeLlm(),
            embedder_factory=lambda _s: FakeEmbedder(),
        )
    )


def test_health_reports_engines():
    with client() as c:
        assert c.get("/health").json() == {
            "status": "ok",
            "ocrEngine": "rapidocr-layout",
            "llmModel": "fake-llm",
            "embeddingModel": "fake-embed",
            "embeddingDimensions": 4,
        }


def test_extract_returns_pages_and_tree_with_figures():
    with client() as c:
        response = c.post(
            "/v1/extract", headers=KEY, files={"file": ("bio.pdf", textbook_pdf(), "application/pdf")}
        )
    body = response.json()
    assert response.status_code == 200
    assert (body["pageCount"], body["textPages"], body["ocrPages"], body["language"]) == (3, 3, 0, "en")
    tree = body["tree"]
    assert [n["path"] for n in tree["nodes"]] == ["1", "1.1", "2", "2.1"]
    assert tree["nodes"][1]["parentKey"] == tree["nodes"][0]["key"]
    figure = tree["figures"][0]
    assert (
        figure["caption"].startswith("Figure 1.1")
        and figure["imageBase64"]
        and figure["mime"].startswith("image/")
    )
    assert "*[Figure]*" in body["markdown"] and "base64" not in body["markdown"]


def test_endpoints_require_internal_api_key():
    with client() as c:
        assert (
            c.post("/v1/extract", files={"file": ("d.pdf", b"%PDF-", "application/pdf")}).status_code == 401
        )
        assert c.post("/v1/study/grade", json={}).status_code == 401
        assert c.post("/v1/index/embed", json={}).status_code == 401


def test_unreadable_document_is_422():
    with client() as c:
        response = c.post(
            "/v1/extract", headers=KEY, files={"file": ("doc.pdf", b"%PDF-garbage", "application/pdf")}
        )
    assert response.status_code == 422


def test_index_embed_round_trip():
    with client() as c:
        response = c.post(
            "/v1/index/embed", headers=KEY, json={"task": "RETRIEVAL_QUERY", "items": [{"text": "abc"}]}
        )
    assert response.json() == {"vectors": [[3.0, 0.0, 0.0, 1.0]], "model": "fake-embed", "dimensions": 4}


def test_llm_failures_map_to_503():
    class Failing(FakeLlm):
        def generate(self, **_kw):
            from ved_inference.errors import EngineError

            raise EngineError("busy")

    with client(Failing()) as c:
        response = c.post(
            "/v1/study/grade",
            headers=KEY,
            json={"question": {"prompt": "Q", "referenceAnswer": "R"}, "answer": "A"},
        )
    assert response.status_code == 503 and response.json()["detail"] == "busy"
