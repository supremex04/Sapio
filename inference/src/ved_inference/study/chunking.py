"""Splits extracted page text into source chunks: the unit lessons and questions cite."""

import re
from dataclasses import dataclass

TARGET_CHARS = 3500
MAX_CHARS = 6000
_HEADING = re.compile(r"^#{1,4}\s+(.+?)\s*#*$")
_SENTENCE_END = re.compile(r"(?<=[.!?])\s+")


@dataclass(frozen=True)
class PageText:
    page_from: int
    page_to: int
    text: str


@dataclass(frozen=True)
class Chunk:
    position: int  # 1-based
    heading: str
    content: str
    page_from: int
    page_to: int


@dataclass
class _Block:
    text: str
    heading: str | None
    page_from: int
    page_to: int


def chunk_pages(pages: list[PageText]) -> list[Chunk]:
    blocks = list(_blocks(pages))
    chunks: list[Chunk] = []
    current: list[_Block] = []
    size = 0
    last_heading = ""

    def flush() -> None:
        nonlocal current, size
        if not current:
            return
        content = "\n\n".join(b.text for b in current).strip()
        heading = next((b.heading for b in current if b.heading), None) or last_heading
        chunks.append(
            Chunk(
                position=len(chunks) + 1,
                heading=heading[:200],
                content=content,
                page_from=current[0].page_from,
                page_to=current[-1].page_to,
            )
        )
        current, size = [], 0

    for block in blocks:
        # A heading starts a new chunk once the current one has meaningful content.
        if block.heading and size >= TARGET_CHARS * 0.4:
            flush()
        if size + len(block.text) > TARGET_CHARS and current:
            flush()
        current.append(block)
        size += len(block.text)
        if block.heading:
            last_heading = block.heading
    flush()
    return chunks


def _blocks(pages: list[PageText]):
    """Yields paragraphs (split further if a single paragraph is too long)."""
    for page in pages:
        for paragraph in re.split(r"\n\s*\n", page.text):
            paragraph = paragraph.strip()
            if not paragraph:
                continue
            first_line = paragraph.splitlines()[0]
            match = _HEADING.match(first_line)
            heading = match.group(1).strip("*_ ") if match else None
            for piece in _split_long(paragraph):
                yield _Block(text=piece, heading=heading, page_from=page.page_from, page_to=page.page_to)
                heading = None


def _split_long(paragraph: str) -> list[str]:
    if len(paragraph) <= MAX_CHARS:
        return [paragraph]
    pieces, current = [], ""
    for sentence in _SENTENCE_END.split(paragraph):
        if len(current) + len(sentence) > TARGET_CHARS and current:
            pieces.append(current.strip())
            current = ""
        # Pathological "sentences" (tables, no punctuation) are hard-wrapped.
        while len(sentence) > MAX_CHARS:
            pieces.append(sentence[:TARGET_CHARS])
            sentence = sentence[TARGET_CHARS:]
        current += sentence + " "
    if current.strip():
        pieces.append(current.strip())
    return pieces
