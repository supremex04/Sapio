from sapio_inference.engines.markdown import join_pages, remove_detection_markup


def test_strips_detection_markers_and_groups_blocks():
    raw = (
        "<|det|>title [10, 10, 200, 40]<|/det|># Chapter 1\n"
        "<|det|>text [10, 50, 400, 90]<|/det|>First line\n"
        "continued line\n"
        "<|det|>image [0, 0, 5, 5]<|/det|>\n"
        "<|det|>text [10, 100, 400, 140]<|/det|>Second paragraph\n"
    )
    assert remove_detection_markup(raw) == "# Chapter 1\n\nFirst line\ncontinued line\n\nSecond paragraph"


def test_plain_text_passes_through():
    assert remove_detection_markup("just text\nmore") == "just text\nmore"


def test_join_pages_skips_empty_pages():
    assert join_pages(["a", "  ", "b"]) == "a\n\n---\n\nb"
