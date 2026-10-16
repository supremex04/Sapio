from fastapi.testclient import TestClient

from sapio_inference.errors import ConfigurationError, EngineError
from sapio_inference.main import create_app
from tests.conftest import textbook_pdf
from tests.fakes import FakeEmbedder, FakeLlm, FakeSpeech

KEY = {"X-Internal-Api-Key": "test-key"}


def client(llm=None) -> TestClient:
    return TestClient(
        create_app(
            engine_factory=lambda _s: None,
            llm_factory=lambda _s: llm or FakeLlm(),
            embedder_factory=lambda _s: FakeEmbedder(),
            speech_factory=lambda _s: FakeSpeech(),
        ),
        raise_server_exceptions=False,
    )


GRADE = {"question": {"prompt": "Q", "referenceAnswer": "R"}, "answer": "A"}


def failing_llm(error: Exception) -> FakeLlm:
    class Failing(FakeLlm):
        def generate(self, **_kw):
            raise error

    return Failing()


def test_health_reports_engines():
    with client() as c:
        assert c.get("/health").json() == {
            "status": "ok",
            "contractVersion": "1.5",
            "ocrEngine": "rapidocr-layout",
            "llmModel": "fake-llm",
            "embeddingModel": "fake-embed",
            "embeddingDimensions": 4,
            "speechModel": "fake-stt",
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
        response = c.post("/v1/index/embed", json={})
    assert response.status_code == 401
    assert response.json()["code"] == "UNAUTHORIZED" and response.json()["retryable"] is False


def test_unreadable_document_is_422():
    with client() as c:
        response = c.post(
            "/v1/extract", headers=KEY, files={"file": ("doc.pdf", b"%PDF-garbage", "application/pdf")}
        )
    assert response.status_code == 422
    body = response.json()
    assert (body["code"], body["retryable"]) == ("INPUT_REJECTED", False)
    assert body["message"] == "Unreadable or corrupt file"


def test_index_embed_round_trip():
    with client() as c:
        response = c.post(
            "/v1/index/embed", headers=KEY, json={"task": "RETRIEVAL_QUERY", "items": [{"text": "abc"}]}
        )
    assert response.json() == {"vectors": [[3.0, 0.0, 0.0, 1.0]], "model": "fake-embed", "dimensions": 4}


def test_unavailable_models_are_retryable_503_with_a_hint():
    with client(failing_llm(EngineError("busy", retry_after=41.6))) as c:
        response = c.post("/v1/study/grade", headers=KEY, json=GRADE)
    assert response.status_code == 503
    assert response.headers["Retry-After"] == "42"
    body = response.json()
    assert (body["code"], body["message"], body["retryable"]) == ("MODEL_UNAVAILABLE", "busy", True)


def test_misconfiguration_is_a_non_retryable_500():
    with client(failing_llm(ConfigurationError("set the API key in $X"))) as c:
        response = c.post("/v1/study/grade", headers=KEY, json=GRADE)
    assert response.status_code == 500
    assert (response.json()["code"], response.json()["retryable"]) == ("MISCONFIGURED", False)


def test_unexpected_errors_keep_the_envelope_and_hide_details():
    with client(failing_llm(RuntimeError("secret internals"))) as c:
        response = c.post("/v1/study/grade", headers=KEY, json=GRADE)
    assert response.status_code == 500
    body = response.json()
    assert body["code"] == "INTERNAL_ERROR" and "secret" not in body["message"]


def test_body_not_matching_the_contract_is_a_contract_violation():
    with client() as c:
        response = c.post("/v1/study/grade", headers=KEY, json={"answer": "A"})
    assert response.status_code == 400
    body = response.json()
    assert body["code"] == "CONTRACT_VIOLATION" and "question" in body["message"]


def test_request_id_is_echoed_and_included_in_errors():
    with client(failing_llm(EngineError("busy"))) as c:
        response = c.post("/v1/study/grade", headers={**KEY, "X-Request-Id": "req-123"}, json=GRADE)
        generated = c.get("/health")
    assert response.headers["X-Request-Id"] == "req-123"
    assert response.json()["requestId"] == "req-123"
    assert len(generated.headers["X-Request-Id"]) == 32
