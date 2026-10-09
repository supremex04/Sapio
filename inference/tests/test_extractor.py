import pytest

from tests.conftest import make_mixed_pdf, make_pdf, make_text_image
from tests.fakes import FakeOcrEngine
from ved_inference.documents.loader import DocumentLoader
from ved_inference.errors import DocumentError
from ved_inference.extraction.extractor import DocumentExtractor


def extractor(engine=None, max_pages=10):
    return DocumentExtractor(
        engine or FakeOcrEngine(),
        DocumentLoader(max_pages=max_pages, pdf_dpi=72),
        max_pages=max_pages,
        pdf_dpi=72,
        native_min_chars=20,
        max_concurrency=1,
    )


def test_text_layer_pages_are_read_natively_and_scans_are_ocrd():
    engine = FakeOcrEngine()
    result = extractor(engine).extract(make_mixed_pdf(), "bio.pdf")

    assert [(s.page_from, s.method) for s in result.segments] == [(1, "text"), (2, "ocr")]
    assert "Photosynthesis" in result.segments[0].markdown
    assert result.segments[1].markdown == "ocr page 2"
    assert engine.calls == [[2]]
    assert (result.text_pages, result.ocr_pages, result.page_count) == (1, 1, 2)
    assert result.engine == "pymupdf+fake-ocr"


def test_fully_digital_pdf_never_calls_ocr():
    engine = FakeOcrEngine()
    result = extractor(engine).extract(
        make_pdf(["A page with plenty of real text on it", "Second page, also with plenty of real text"]),
        "a.pdf",
    )
    assert engine.calls == []
    assert result.engine == "pymupdf"
    assert result.ocr_pages == 0


def test_images_go_through_ocr():
    result = extractor().extract(make_text_image(["hi"]), "photo.png")
    assert [s.method for s in result.segments] == ["ocr"]


def test_page_limit_and_corrupt_files():
    with pytest.raises(DocumentError, match="limit is 1"):
        extractor(max_pages=1).extract(make_pdf(["a", "b"]), "x.pdf")
    with pytest.raises(DocumentError):
        extractor().extract(b"%PDF-garbage", "x.pdf")
