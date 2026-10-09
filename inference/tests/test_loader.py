import pytest

from tests.conftest import make_pdf, make_text_image
from ved_inference.documents.loader import DocumentLoader
from ved_inference.errors import DocumentError

loader = DocumentLoader(max_pages=3, pdf_dpi=72)


def test_pdf_pages_are_rasterised():
    doc = loader.load(make_pdf(["one", "two"]), "notes.pdf")
    assert [p.number for p in doc.pages] == [1, 2]
    assert doc.pages[0].image.mode == "RGB"


def test_image_is_single_page():
    doc = loader.load(make_text_image(["hello"]), "scan.png")
    assert len(doc.pages) == 1


def test_page_limit_is_enforced():
    with pytest.raises(DocumentError, match="limit is 3"):
        loader.load(make_pdf(["1", "2", "3", "4"]), "big.pdf")


@pytest.mark.parametrize("filename", ["broken.pdf", "broken.png"])
def test_corrupt_input_is_a_document_error(filename):
    with pytest.raises(DocumentError):
        loader.load(b"definitely not a real file", filename)
