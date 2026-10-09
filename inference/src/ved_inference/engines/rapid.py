"""Lightweight CPU OCR (RapidOCR / PaddleOCR models on ONNX Runtime).

Produces plain text grouped into lines and paragraphs; it does not recover tables, formulas or
headings. Its purpose is to make the platform work on any machine; use Unlimited-OCR for quality.
"""

from dataclasses import dataclass
from statistics import median

import numpy as np

from ved_inference.documents.models import PageImage
from ved_inference.engines.base import EngineOutput, OcrEngine
from ved_inference.engines.markdown import join_pages
from ved_inference.errors import EngineError


@dataclass(frozen=True)
class _Box:
    x: float
    y_center: float
    height: float
    text: str


class RapidOcrEngine(OcrEngine):
    name = "rapidocr"

    def __init__(self) -> None:
        try:
            from rapidocr_onnxruntime import RapidOCR
        except ImportError as exc:  # pragma: no cover - depends on installed extras
            raise EngineError("RapidOCR not installed: pip install -e '.[rapid]'") from exc
        self._ocr = RapidOCR()

    def extract(self, pages: list[PageImage]) -> EngineOutput:
        return EngineOutput(markdown=join_pages([self._page_to_text(p) for p in pages]))

    def _page_to_text(self, page: PageImage) -> str:
        result, _ = self._ocr(np.asarray(page.image))
        boxes = [_to_box(points, text) for points, text, _score in (result or []) if text.strip()]
        return layout_text(boxes)


def _to_box(points: list[list[float]], text: str) -> _Box:
    ys = [p[1] for p in points]
    return _Box(x=min(p[0] for p in points), y_center=sum(ys) / len(ys), height=max(ys) - min(ys), text=text)


def layout_text(boxes: list[_Box]) -> str:
    """Groups detected text boxes into reading-order lines, and lines into paragraphs."""
    if not boxes:
        return ""
    typical_height = median(b.height for b in boxes) or 1.0
    lines: list[list[_Box]] = []
    for box in sorted(boxes, key=lambda b: b.y_center):
        if lines and abs(box.y_center - _line_center(lines[-1])) < typical_height * 0.5:
            lines[-1].append(box)
        else:
            lines.append([box])

    out: list[str] = []
    previous_center: float | None = None
    for line in lines:
        center = _line_center(line)
        if previous_center is not None and center - previous_center > typical_height * 1.8:
            out.append("")  # large vertical gap -> new paragraph
        out.append(" ".join(b.text.strip() for b in sorted(line, key=lambda b: b.x)))
        previous_center = center
    return "\n".join(out)


def _line_center(line: list[_Box]) -> float:
    return sum(b.y_center for b in line) / len(line)
