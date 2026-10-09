import pytest

from tests.conftest import make_text_image
from ved_inference.documents.loader import DocumentLoader
from ved_inference.engines.rapid import _Box, layout_text


def test_layout_groups_boxes_into_lines_and_paragraphs():
    boxes = [
        _Box(x=200, y_center=10, height=10, text="world"),
        _Box(x=10, y_center=11, height=10, text="Hello"),
        _Box(x=10, y_center=25, height=10, text="next line"),
        _Box(x=10, y_center=80, height=10, text="new paragraph"),
    ]
    assert layout_text(boxes) == "Hello world\nnext line\n\nnew paragraph"


@pytest.mark.slow
def test_recognises_rendered_text_end_to_end():
    pytest.importorskip("rapidocr_onnxruntime")
    from ved_inference.engines.rapid import RapidOcrEngine

    doc = DocumentLoader(max_pages=1, pdf_dpi=72).load(
        make_text_image(["Photosynthesis", "uses sunlight"]), "a.png"
    )
    markdown = RapidOcrEngine().extract(doc.pages).segments[0].markdown
    assert "Photosynthesis" in markdown
    assert "sunlight" in markdown
