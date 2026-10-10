from ved_inference.parsing.models import Block, FigureImage
from ved_inference.parsing.tree import CHUNK_MAX, build_tree

IMG = FigureImage(data=b"x", mime="image/png", width=300, height=200)


def h(text, level=1, page=1):
    return Block(page=page, kind="heading", text=text, level=level)


def t(text, page=1):
    return Block(page=page, kind="text", text=text)


def test_explicit_numbering_beats_font_levels_and_labels_are_kept():
    tree = build_tree(
        [
            h("Unit 1: Biology", 2),
            h("1 Basics", 2),
            t("a"),
            h("1.1 Cells", 1),
            t("b"),
            h("Unit 2 Chemistry", 1),
            t("c"),
        ],
        3,
    )
    assert [(n.path, n.label, n.title, n.level) for n in tree.nodes] == [
        ("1", "Unit 1", "Biology", 1),
        ("1.1", "1", "Basics", 2),
        ("1.1.1", "1.1", "Cells", 3),
        ("2", "Unit 2", "Chemistry", 1),
    ]


def test_content_before_first_heading_gets_an_introduction_node():
    tree = build_tree([t("preface"), h("Chapter 1 Start"), t("x")], 1)
    assert [n.title for n in tree.nodes] == ["Introduction", "Start"]


def test_no_headings_falls_back_to_page_groups():
    blocks = [t(f"page {p}", page=p) for p in range(1, 12)]
    tree = build_tree(blocks, 11)
    assert [n.title for n in tree.nodes] == ["Pages 1–5", "Pages 6–10", "Page 11"]


def test_long_headings_and_captions_are_not_sections():
    tree = build_tree([h("Chapter 1 Real"), h("Figure 3: something"), h("x" * 200), t("body")], 1)
    assert [n.title for n in tree.nodes] == ["Real"]


def test_chunks_respect_size_and_figures_anchor_to_their_chunk():
    para = "Sentence of medium length about cells. " * 12  # ~470 chars
    blocks = [
        h("Chapter 1 A"),
        t(para),
        t(para),
        Block(page=1, kind="figure", image=IMG),
        Block(page=1, kind="caption", text="Figure 1: Cell"),
        t(para),
        t(para),
        t(para),
    ]
    tree = build_tree(blocks, 1)
    assert all(len(c.text) <= CHUNK_MAX for c in tree.chunks)
    assert len(tree.chunks) >= 2
    fig = tree.figures[0]
    assert fig.caption == "Figure 1: Cell"
    assert fig.chunk_key in {c.key for c in tree.chunks}
    anchored = next(c for c in tree.chunks if c.key == fig.chunk_key)
    assert "Figure 1: Cell" in anchored.text


def test_language_detection():
    from ved_inference.parsing.language import detect_language

    assert (
        detect_language(
            "The cell is the basic unit of life and it is made of parts that work with each other. " * 3
        )
        == "en"
    )
    assert (
        detect_language(
            "La célula es la unidad básica de la vida y de los seres vivos que se encuentran en el mundo. "
            * 3
        )
        == "es"
    )
    assert detect_language("short") == "und"
