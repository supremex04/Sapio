"""Word documents (.docx) → the same typed block stream the PDF path produces.

Structure comes from the document itself, not from layout guessing: heading styles (``Title``, ``Heading 1..9``) and
outline levels become headings, list paragraphs become Markdown bullets, tables become Markdown tables, and embedded
pictures become figures. A .docx has no fixed pages, so pages are counted from the page breaks Word recorded when the
file was last saved (and explicit page breaks); citations then point at the page a reader sees in Word.
"""

import io
import re

from docx import Document as open_docx
from docx.oxml.ns import qn
from docx.table import Table
from docx.text.paragraph import Paragraph

from sapio_inference.config import FigureFilterConfig
from sapio_inference.errors import DocumentError
from sapio_inference.parsing.figures import decode, encode
from sapio_inference.parsing.models import Block

_HEADING_STYLE = re.compile(r"^heading\s*(\d)$", re.IGNORECASE)
_LIST_STYLE = re.compile(r"list", re.IGNORECASE)
_BLIP = qn("a:blip")
_EMBED = qn("r:embed")


def docx_blocks(data: bytes, figures: FigureFilterConfig) -> tuple[list[Block], int]:
    """Returns (blocks in reading order, page count)."""
    try:
        document = open_docx(io.BytesIO(data))
    except Exception as exc:  # python-docx raises several types for broken packages
        raise DocumentError("Unreadable or corrupt Word document") from exc

    blocks: list[Block] = []
    page = 1
    for element in document.element.body.iterchildren():
        if element.tag == qn("w:p"):
            paragraph = Paragraph(element, document)
            page += _page_breaks_before(paragraph)
            blocks.extend(_paragraph_blocks(paragraph, page, document, figures))
        elif element.tag == qn("w:tbl"):
            table = _table_markdown(Table(element, document))
            if table:
                blocks.append(Block(page=page, kind="table", text=table))
    return blocks, page


def _page_breaks_before(paragraph: Paragraph) -> int:
    rendered = len(paragraph.rendered_page_breaks)
    explicit = sum(1 for br in paragraph._p.iter(qn("w:br")) if br.get(qn("w:type")) == "page")
    return rendered + explicit


def _paragraph_blocks(paragraph: Paragraph, page: int, document, figures: FigureFilterConfig) -> list[Block]:
    out: list[Block] = [
        Block(page=page, kind="figure", text="", image=image)
        for image in _images(paragraph, document, figures)
    ]
    text = _inline_markdown(paragraph).strip()
    if not text:
        return out
    level = _heading_level(paragraph)
    if level:
        out.append(Block(page=page, kind="heading", text=text, level=level))
    elif _is_list_item(paragraph):
        out.append(Block(page=page, kind="text", text=f"- {text}"))
    else:
        out.append(Block(page=page, kind="text", text=text))
    return out


def _heading_level(paragraph: Paragraph) -> int:
    name = (paragraph.style.name if paragraph.style is not None else "") or ""
    if name.lower() == "title":
        return 1
    match = _HEADING_STYLE.match(name)
    if match:
        return int(match.group(1))
    outline = paragraph._p.find(f"{qn('w:pPr')}/{qn('w:outlineLvl')}")
    if outline is not None:
        value = int(outline.get(qn("w:val"), "9"))
        return value + 1 if value < 9 else 0
    return 0


def _is_list_item(paragraph: Paragraph) -> bool:
    numbered = paragraph._p.find(f"{qn('w:pPr')}/{qn('w:numPr')}") is not None
    name = (paragraph.style.name if paragraph.style is not None else "") or ""
    return numbered or bool(_LIST_STYLE.search(name))


def _inline_markdown(paragraph: Paragraph) -> str:
    parts: list[str] = []
    for run in paragraph.runs:
        text = run.text
        if not text.strip():
            parts.append(text)
            continue
        lead = text[: len(text) - len(text.lstrip())]
        trail = text[len(text.rstrip()) :]
        core = text.strip()
        if run.bold and run.italic:
            core = f"***{core}***"
        elif run.bold:
            core = f"**{core}**"
        elif run.italic:
            core = f"*{core}*"
        parts.append(f"{lead}{core}{trail}")
    # Adjacent runs with the same emphasis produce "**a****b**": merge them.
    return re.sub(r"\*\*\*\*", "", "".join(parts))


def _images(paragraph: Paragraph, document, figures: FigureFilterConfig) -> list:
    images = []
    for blip in paragraph._p.iter(_BLIP):
        rel = blip.get(_EMBED)
        part = document.part.related_parts.get(rel) if rel else None
        if part is None:
            continue
        image = decode(part.blob)
        if image is None or min(image.size) < figures.min_side_px:
            continue  # icons, bullets, spacer images
        images.append(encode(image, figures.max_side_px))
    return images


def _table_markdown(table: Table) -> str:
    rows: list[list[str]] = []
    for row in table.rows:
        cells = []
        seen: set[int] = set()
        for cell in row.cells:
            if id(cell._tc) in seen:  # merged cells repeat the same element
                continue
            seen.add(id(cell._tc))
            text = " ".join(p.text.strip() for p in cell.paragraphs if p.text.strip())
            cells.append(text.replace("|", "\\|"))
        if any(cells):
            rows.append(cells)
    if not rows:
        return ""
    width = max(len(r) for r in rows)
    rows = [r + [""] * (width - len(r)) for r in rows]
    lines = ["|" + "|".join(rows[0]) + "|", "|" + "|".join(["---"] * width) + "|"]
    lines += ["|" + "|".join(r) + "|" for r in rows[1:]]
    return "\n".join(lines)
