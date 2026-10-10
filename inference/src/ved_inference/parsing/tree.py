"""Builds the document tree (nodes → leaf chunks, with linked figures) from the block stream."""

import re
from dataclasses import dataclass, field

from ved_inference.config import ParsingConfig
from ved_inference.parsing.models import Block, DocumentTree, TreeChunk, TreeFigure, TreeNode

DEFAULT = ParsingConfig()

_NUMBERED = re.compile(r"^(\d+(?:\.\d+){0,3})[.)]?\s+\S")
_UNIT = re.compile(
    r"^(chapter|unit|module|lecture|part|section|lesson|week|topic)\s+(\d+|[ivxlc]+\b)", re.IGNORECASE
)
_CAPTION = re.compile(r"^(fig(ure)?|table|diagram|chart|image)\.?\s*\d", re.IGNORECASE)


@dataclass
class _Heading:
    level: int
    title: str
    label: str | None
    page: int


@dataclass
class _Draft:
    heading: _Heading
    content: list[Block] = field(default_factory=list)


def build_tree(blocks: list[Block], page_count: int, config: ParsingConfig = DEFAULT) -> DocumentTree:
    blocks = _demote_noise_headings(blocks, config.max_heading_chars)
    headings = [b for b in blocks if b.kind == "heading"]
    if not headings:
        return _page_group_tree(blocks, page_count, config)

    levels = _normalised_levels(headings, config.max_depth)
    drafts: list[_Draft] = []
    current: _Draft | None = None
    for b in blocks:
        if b.kind == "heading":
            current = _Draft(_Heading(levels[id(b)], b.text, _explicit_label(b.text), b.page))
            drafts.append(current)
        else:
            if current is None:  # content before the first heading
                current = _Draft(_Heading(1, "Introduction", None, b.page))
                drafts.append(current)
            current.content.append(b)
    return _assemble(drafts, config)


# --------------------------------------------------------------------------------- levels


def _demote_noise_headings(blocks: list[Block], max_heading_chars: int) -> list[Block]:
    """Very long 'headings' and captions mis-detected as headings become text."""
    out = []
    for b in blocks:
        if b.kind == "heading" and (len(b.text) > max_heading_chars or _CAPTION.match(b.text)):
            out.append(
                Block(
                    page=b.page,
                    kind="caption" if _CAPTION.match(b.text) else "text",
                    text=b.text,
                    bbox=b.bbox,
                )
            )
        else:
            out.append(b)
    return out


def _normalised_levels(headings: list[Block], max_depth: int) -> dict[int, int]:
    """Maps layout heading levels to 1..max_depth, refined by explicit numbering (2.3 → level 2)."""
    distinct = sorted({h.level for h in headings})
    rank = {lvl: i + 1 for i, lvl in enumerate(distinct)}
    numbered = [h for h in headings if _NUMBERED.match(h.text) or _UNIT.match(h.text)]
    use_numbering = len(numbered) >= max(2, len(headings) // 2)
    has_units = any(_UNIT.match(h.text) for h in headings)
    # "Chapter 2 → 2.1": sections are children of the unit (2.1 is level 2). But with bare-number
    # headings under units ("Unit 1 → 1 Basics → 1.1 Cells") every numbered level sits one deeper.
    has_bare = any(
        (m := _NUMBERED.match(h.text)) and "." not in m.group(1) for h in headings if not _UNIT.match(h.text)
    )
    shift = 1 if has_units and has_bare else 0
    levels: dict[int, int] = {}
    for h in headings:
        level = rank[h.level]
        if use_numbering:
            m = _NUMBERED.match(h.text)
            if _UNIT.match(h.text):
                level = 1  # "Chapter 2", "Unit 4"
            elif m:
                level = m.group(1).count(".") + 1 + shift
        levels[id(h)] = min(max(level, 1), max_depth)
    return levels


def _explicit_label(title: str) -> str | None:
    m = _UNIT.match(title)
    if m:
        return f"{m.group(1).capitalize()} {m.group(2)}"
    m = _NUMBERED.match(title)
    return m.group(1) if m else None


# --------------------------------------------------------------------------------- assembly


def _assemble(drafts: list[_Draft], config: ParsingConfig) -> DocumentTree:
    tree = DocumentTree()
    stack: list[tuple[int, TreeNode]] = []  # (level, node)
    counters: dict[str | None, int] = {}
    chunk_no = 0
    figure_no = 0

    for draft in drafts:
        h = draft.heading
        while stack and stack[-1][0] >= h.level:
            stack.pop()
        # A jump of more than one level (1 → 3) attaches to the nearest ancestor.
        parent = stack[-1][1] if stack else None
        parent_key = parent.key if parent else None
        counters[parent_key] = counters.get(parent_key, 0) + 1
        path = f"{parent.path}.{counters[parent_key]}" if parent else str(counters[parent_key])
        level = (parent.level + 1) if parent else 1
        pages = [b.page for b in draft.content] or [h.page]
        node = TreeNode(
            key=f"n{len(tree.nodes) + 1}",
            parent_key=parent_key,
            level=level,
            path=path,
            label=h.label or path,
            title=_clean_title(h.title, h.label),
            ordinal=len(tree.nodes) + 1,
            page_from=min(min(pages), h.page),
            page_to=max(pages),
        )
        tree.nodes.append(node)
        stack.append((h.level, node))

        chunks, figures = _pack(node, draft.content, chunk_no, figure_no, config)
        chunk_no += len(chunks)
        figure_no += len(figures)
        tree.chunks.extend(chunks)
        tree.figures.extend(figures)

    _propagate_pages(tree)
    return tree


def _pack(
    node: TreeNode, content: list[Block], chunk_no: int, figure_no: int, config: ParsingConfig
) -> tuple[list[TreeChunk], list[TreeFigure]]:
    chunks: list[TreeChunk] = []
    figures: list[TreeFigure] = []
    buffer: list[str] = []
    pages: list[int] = []

    def flush() -> None:
        nonlocal buffer, pages
        text = "\n\n".join(buffer).strip()
        if text:
            chunks.append(
                TreeChunk(
                    key=f"c{chunk_no + len(chunks) + 1}",
                    node_key=node.key,
                    ordinal=chunk_no + len(chunks) + 1,
                    text=text,
                    page_from=min(pages),
                    page_to=max(pages),
                )
            )
        buffer, pages = [], []

    for i, b in enumerate(content):
        if b.kind == "figure" and b.image is not None:
            caption = _caption_near(content, i)
            figures.append(
                TreeFigure(
                    key=f"f{figure_no + len(figures) + 1}",
                    node_key=node.key,
                    chunk_key=None,  # resolved below to the chunk around the figure
                    ordinal=figure_no + len(figures) + 1,
                    page=b.page,
                    bbox=b.bbox,
                    caption=caption,
                    image=b.image,
                )
            )
            # anchor: the chunk being built now, or the previous one
            figures[-1].chunk_key = (
                f"c{chunk_no + len(chunks) + 1}" if buffer else (chunks[-1].key if chunks else None)
            )
            continue
        text = b.text.strip()
        if not text:
            continue
        atomic = b.kind in ("table", "formula")
        size = sum(len(t) for t in buffer)
        # A caption always stays in the chunk of the figure it describes.
        if (
            b.kind != "caption"
            and buffer
            and (
                size + len(text) > config.chunk_max_chars
                or (size >= config.chunk_target_chars and not atomic)
            )
        ):
            flush()
        buffer.append(text)
        pages.append(b.page)
        if atomic and len(text) > config.chunk_target_chars:
            flush()
    flush()

    # Figures that preceded any text in their node anchor to the node's first chunk.
    valid = {c.key for c in chunks}
    for f in figures:
        if f.chunk_key not in valid:
            f.chunk_key = chunks[0].key if chunks else None
    return chunks, figures


def _caption_near(content: list[Block], index: int) -> str:
    """The caption right after a figure (or right before it, as tables often have)."""
    for j in (index + 1, index + 2, index - 1):
        if 0 <= j < len(content):
            b = content[j]
            if b.kind == "caption" or (b.kind == "text" and _CAPTION.match(b.text) and len(b.text) < 300):
                return b.text.strip()
    return ""


def _clean_title(title: str, label: str | None) -> str:
    """Drops the numbering the label already carries: "2.3 Osmosis" → "Osmosis", "Chapter 2: Energy" → "Energy"."""
    t = re.sub(r"\s+", " ", title).strip(" #*")
    m = _UNIT.match(t) or _NUMBERED.match(t)
    if m:
        rest = t[m.end(2) if m.re is _UNIT else len(m.group(1)) :]
        rest = re.sub(r"^[\s.):\-\u2013\u2014]+", "", rest)
        if rest:
            t = rest
    return t.strip(" :-") or (label or "Untitled")


def _propagate_pages(tree: DocumentTree) -> None:
    """A parent spans at least its children's pages."""
    by_key = {n.key: n for n in tree.nodes}
    for node in reversed(tree.nodes):
        if node.parent_key:
            parent = by_key[node.parent_key]
            parent.page_from = min(parent.page_from, node.page_from)
            parent.page_to = max(parent.page_to, node.page_to)


def _page_group_tree(blocks: list[Block], page_count: int, config: ParsingConfig) -> DocumentTree:
    """No headings at all: one node per few pages so the document is still navigable."""
    drafts: list[_Draft] = []
    for start in range(1, max(page_count, 1) + 1, config.pages_per_fallback_node):
        end = min(start + config.pages_per_fallback_node - 1, max(page_count, 1))
        title = f"Page {start}" if start == end else f"Pages {start}–{end}"
        group = [b for b in blocks if start <= b.page <= end]
        if group:
            drafts.append(_Draft(_Heading(1, title, None, start), group))
    if not drafts:
        drafts.append(_Draft(_Heading(1, "Document", None, 1), blocks))
    return _assemble(drafts, config)
