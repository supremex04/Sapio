"""Typed block stream from PyMuPDF's layout model (pymupdf4llm), with RapidOCR for scanned pages."""

import base64
import logging
import re

import pymupdf

from sapio_inference.parsing.figures import decode, encode
from sapio_inference.parsing.models import BBox, Block
from sapio_inference.parsing.tables import repair_tables

log = logging.getLogger(__name__)

_HEADING = re.compile(r"^(#{1,6})\s+(.*)$", re.DOTALL)
_DATA_URI = re.compile(r"!\[[^\]]*\]\(data:(image/[a-z+]+);base64,([A-Za-z0-9+/=]+)\)")
# The layout model marks highlighted / underlined / styled spans with inline HTML; keep the emphasis as Markdown.
_INLINE_HTML = [
    (re.compile(r"<(mark|b|strong)>(.*?)</\1>", re.IGNORECASE | re.DOTALL), r"**\2**"),
    (re.compile(r"<(i|em)>(.*?)</\1>", re.IGNORECASE | re.DOTALL), r"*\2*"),
    (re.compile(r"</?(u|span|font|sup|sub)\b[^>]*>", re.IGNORECASE), ""),
    (re.compile(r"<br\s*/?>", re.IGNORECASE), "\n"),
    # Enclosed-letter symbols are slide bullet glyphs, not text.
    (re.compile("[\U0001f130-\U0001f189]\ufe0f?\\s?"), ""),
]
_FENCE = re.compile(r"(```.*?```|`[^`\n]*`)", re.DOTALL)


def inline_html_to_markdown(text: str) -> str:
    """Converts simple inline HTML to Markdown, leaving code (fenced or inline) untouched."""
    # Tables first: their rows must stay single lines before <br> elsewhere becomes a line break.
    parts = _FENCE.split(repair_tables(text))
    for i in range(0, len(parts), 2):  # even indexes are outside code
        for pattern, repl in _INLINE_HTML:
            parts[i] = pattern.sub(repl, parts[i])
    return "".join(parts)


_SKIP_CLASSES = {"page-header", "page-footer", "footnote-marker"}
_HEADING_CLASSES = {"section-header", "title"}


def read_layout(doc: pymupdf.Document, pages: list[int], *, ocr: bool, dpi: int) -> dict[int, list[Block]]:
    """Returns {page_number (1-based): blocks in reading order} for the given 0-based page indices."""
    if not pages:
        return {}
    import pymupdf4llm

    chunks = pymupdf4llm.to_markdown(
        doc,
        pages=pages,
        page_chunks=True,
        use_ocr=ocr,
        embed_images=True,
        header=False,
        footer=False,
        dpi=dpi,
        show_progress=False,
    )
    out: dict[int, list[Block]] = {}
    for index, chunk in zip(pages, chunks, strict=True):
        out[index + 1] = _blocks_from_chunk(index + 1, chunk)
    return out


def _blocks_from_chunk(page: int, chunk: dict) -> list[Block]:
    text: str = chunk.get("text", "")
    boxes = chunk.get("page_boxes") or []
    blocks: list[Block] = []
    for box in boxes:
        cls = box.get("class", "text")
        if cls in _SKIP_CLASSES:
            continue
        start, end = box.get("pos", (0, 0))
        span = inline_html_to_markdown(text[start:end]).strip()
        bbox = _bbox(box.get("bbox"))
        if cls == "picture":
            match = _DATA_URI.search(span)
            if match:
                img = decode(base64.b64decode(match.group(2)))
                if img is not None:
                    blocks.append(Block(page=page, kind="figure", bbox=bbox, image=encode(img)))
            # pictures may also contain recognised text (labels); keep it searchable
            leftover = _DATA_URI.sub("", span).strip()
            if leftover:
                blocks.append(Block(page=page, kind="text", text=leftover, bbox=bbox))
            continue
        if not span:
            continue
        if cls in _HEADING_CLASSES:
            match = _HEADING.match(span)
            level = len(match.group(1)) if match else 1
            title = (match.group(2) if match else span).strip().strip("*").strip()
            blocks.append(Block(page=page, kind="heading", text=title, level=level, bbox=bbox))
        elif cls == "caption":
            blocks.append(Block(page=page, kind="caption", text=span, bbox=bbox))
        elif cls == "table":
            blocks.append(Block(page=page, kind="table", text=span, bbox=bbox))
        elif cls == "formula":
            blocks.append(Block(page=page, kind="formula", text=span, bbox=bbox))
        else:
            # Some layouts mark headings as text with markdown hashes (overlong ones are demoted by the tree
            # builder, see parsing.max_heading_chars).
            match = _HEADING.match(span)
            if match and "\n" not in span:
                blocks.append(
                    Block(
                        page=page,
                        kind="heading",
                        text=match.group(2).strip(),
                        level=len(match.group(1)),
                        bbox=bbox,
                    )
                )
            else:
                blocks.append(Block(page=page, kind="text", text=_DATA_URI.sub("", span).strip(), bbox=bbox))
    return [b for b in blocks if b.kind == "figure" or b.text]


def _bbox(raw: object) -> BBox | None:
    if isinstance(raw, (list, tuple)) and len(raw) == 4:
        x0, y0, x1, y1 = (float(v) for v in raw)
        return (x0, y0, x1, y1)
    return None


def blocks_from_markdown(markdown: str, page_from: int, page_to: int) -> list[Block]:
    """Blocks from engine markdown (Unlimited-OCR): headings and paragraphs, no bboxes."""
    blocks: list[Block] = []
    for para in re.split(r"\n\s*\n", markdown):
        para = para.strip()
        if not para or para == "---":
            continue
        match = _HEADING.match(para)
        if match and "\n" not in para:
            blocks.append(
                Block(page=page_from, kind="heading", text=match.group(2).strip(), level=len(match.group(1)))
            )
        elif para.startswith("|"):
            blocks.append(Block(page=page_from, kind="table", text=para))
        elif para.startswith("$$"):
            blocks.append(Block(page=page_from, kind="formula", text=para))
        else:
            blocks.append(Block(page=page_from, kind="text", text=para))
    if blocks and page_to != page_from:
        blocks[-1].page = page_to
    return blocks
