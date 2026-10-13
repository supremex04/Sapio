import pytest

from sapio_inference.config import ParsingConfig
from sapio_inference.errors import DocumentError
from sapio_inference.parsing.parser import DocumentParser
from tests.conftest import make_text_image, scanned, textbook_pdf
from tests.fakes import FakeOcrEngine


def parser(engine=None, max_pages=20) -> DocumentParser:
    return DocumentParser(engine, ParsingConfig(max_pages=max_pages, dpi=110, native_min_chars=20))


def test_native_pdf_becomes_a_tree_with_linked_figures():
    result = parser().parse(textbook_pdf(), "bio.pdf")
    tree = result.tree

    assert [(n.path, n.label, n.title, n.level) for n in tree.nodes] == [
        ("1", "Chapter 1", "The Cell", 1),
        ("1.1", "1.1", "Cell theory", 2),
        ("2", "Chapter 2", "Energy", 1),
        ("2.1", "2.1", "Respiration", 2),
    ]
    by_key = {n.key: n for n in tree.nodes}
    assert by_key["n2"].parent_key == "n1" and by_key["n4"].parent_key == "n3"
    assert tree.nodes[2].page_to == 3  # a parent spans its children

    # Raster figure + vector diagram kept with captions; the logo repeated on every page is dropped.
    captions = [(by_key[f.node_key].path, f.caption) for f in tree.figures]
    assert captions == [("1.1", "Figure 1.1: A typical plant cell"), ("2", "Figure 2.1: Energy flow")]
    chunk_keys = {c.key for c in tree.chunks}
    assert all(f.chunk_key in chunk_keys for f in tree.figures)
    assert all(f.image.data and f.image.width >= 100 for f in tree.figures)
    assert (result.text_pages, result.ocr_pages, result.engine) == (3, 0, "pymupdf")


def test_scanned_pdf_recovers_structure_and_figures_with_layout_ocr():
    result = parser().parse(scanned(textbook_pdf()), "scan.pdf")
    assert result.ocr_pages == 3
    assert {n.label for n in result.tree.nodes} >= {"Chapter 1", "1.1"}
    assert any("1.1" in f.caption for f in result.tree.figures)


def test_model_engine_handles_scanned_pages_from_markdown():
    engine = FakeOcrEngine()
    result = parser(engine).parse(make_text_image(["hello"]), "photo.png")
    assert engine.calls == [[1]]
    assert result.tree.nodes[0].title == "Scanned 1"
    assert result.tree.chunks[0].text == "ocr page 1"


def test_limits_and_corrupt_files():
    with pytest.raises(DocumentError, match="limit is 2"):
        parser(max_pages=2).parse(textbook_pdf(), "x.pdf")
    with pytest.raises(DocumentError):
        parser().parse(b"%PDF-garbage", "x.pdf")
    with pytest.raises(DocumentError):
        parser().parse(b"", "x.pdf")
