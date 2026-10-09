"""Post-processing shared by engines that emit Unlimited-OCR / DeepSeek-OCR style output."""

import re

# <|det|>category [bbox]<|/det|>content  (from the Unlimited-OCR model card)
_DET_RE = re.compile(r"<\|det\|>([^<\s]+)(?:\s*\[[^\]]*\])?\s*<\|/det\|>(.*)", re.DOTALL)
_SPECIAL_TOKEN_RE = re.compile(r"<\|[a-z_/]+\|>|<｜[^｜]+｜>")

PAGE_SEPARATOR = "\n\n---\n\n"


def remove_detection_markup(raw: str) -> str:
    """Strip layout-detection markers, keeping one Markdown block per detected region.

    Lines of the same region are joined with a newline, different regions with a blank line.
    Markers of category ``image`` carry no text and are dropped.
    """
    blocks: list[list[str]] = []
    current: list[str] | None = None
    for line in raw.splitlines():
        line = line.rstrip()
        if not line:
            continue
        match = _DET_RE.match(line)
        if match:
            category, content = match.group(1).strip(), match.group(2).strip()
            if category == "image":
                continue
            if current is not None:
                blocks.append(current)
            current = [content] if content else []
            continue
        if current is None:
            current = []
        current.append(line)
    if current is not None:
        blocks.append(current)
    text = "\n\n".join("\n".join(b) for b in blocks if b).strip()
    return _SPECIAL_TOKEN_RE.sub("", text).strip()


def join_pages(page_markdowns: list[str]) -> str:
    return PAGE_SEPARATOR.join(md.strip() for md in page_markdowns if md.strip())
