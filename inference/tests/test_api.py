from fastapi.testclient import TestClient

from tests.conftest import make_mixed_pdf
from tests.fakes import FakeLlm, FakeOcrEngine
from ved_inference.main import create_app

KEY = {"X-Internal-Api-Key": "test-key"}


def client(llm=None) -> TestClient:
    return TestClient(
        create_app(engine_factory=lambda _s: FakeOcrEngine(), llm_factory=lambda _s: llm or FakeLlm())
    )


def test_health_reports_engines():
    with client() as c:
        assert c.get("/health").json() == {"status": "ok", "ocrEngine": "fake-ocr", "llmModel": "fake-llm"}


def test_extract_returns_segments_with_methods():
    with client() as c:
        response = c.post(
            "/v1/extract", headers=KEY, files={"file": ("doc.pdf", make_mixed_pdf(), "application/pdf")}
        )
    body = response.json()
    assert response.status_code == 200
    assert (body["pageCount"], body["textPages"], body["ocrPages"]) == (2, 1, 1)
    assert [seg["method"] for seg in body["segments"]] == ["text", "ocr"]
    assert body["markdown"].endswith("---\n\nocr page 2")


def test_endpoints_require_internal_api_key():
    with client() as c:
        assert (
            c.post("/v1/extract", files={"file": ("d.pdf", b"%PDF-", "application/pdf")}).status_code == 401
        )
        assert c.post("/v1/study/grade", json={}).status_code == 401


def test_unreadable_document_is_422():
    with client() as c:
        response = c.post(
            "/v1/extract", headers=KEY, files={"file": ("doc.pdf", b"%PDF-garbage", "application/pdf")}
        )
    assert response.status_code == 422


def test_study_grade_round_trip_uses_camel_case():
    llm = FakeLlm({"verdict": "CORRECT", "score": 1.0, "feedback": "Nice"})
    with client(llm) as c:
        response = c.post(
            "/v1/study/grade",
            headers=KEY,
            json={"question": {"prompt": "Q", "referenceAnswer": "R"}, "answer": "A"},
        )
    assert response.json() == {"verdict": "CORRECT", "score": 1.0, "feedback": "Nice", "model": "fake-llm"}


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
