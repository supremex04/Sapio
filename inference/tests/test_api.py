from fastapi.testclient import TestClient

from tests.conftest import make_pdf
from ved_inference.engines.base import EngineOutput, OcrEngine
from ved_inference.main import create_app


class FakeEngine(OcrEngine):
    name = "fake"

    def extract(self, pages):
        return EngineOutput(markdown="\n\n".join(f"page {p.number}" for p in pages))


def client() -> TestClient:
    return TestClient(create_app(engine_factory=lambda _settings: FakeEngine()))


def test_health_reports_engine():
    with client() as c:
        assert c.get("/health").json() == {"status": "ok", "engine": "fake"}


def test_ocr_returns_camel_case_contract():
    with client() as c:
        response = c.post(
            "/v1/ocr",
            headers={"X-Internal-Api-Key": "test-key"},
            files={"file": ("doc.pdf", make_pdf(["a", "b"]), "application/pdf")},
        )
    assert response.status_code == 200
    assert response.json() == {"markdown": "page 1\n\npage 2", "engine": "fake", "pageCount": 2}


def test_ocr_requires_internal_api_key():
    with client() as c:
        response = c.post("/v1/ocr", files={"file": ("doc.pdf", make_pdf(["a"]), "application/pdf")})
    assert response.status_code == 401


def test_unreadable_document_is_422_with_detail():
    with client() as c:
        response = c.post(
            "/v1/ocr",
            headers={"X-Internal-Api-Key": "test-key"},
            files={"file": ("doc.pdf", b"%PDF-garbage", "application/pdf")},
        )
    assert response.status_code == 422
    assert "corrupt" in response.json()["detail"]
