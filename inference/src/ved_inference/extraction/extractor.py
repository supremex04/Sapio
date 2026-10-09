"""Hybrid text extraction: native PDF text where a page has a text layer, OCR everywhere else.

Native text is far faster and exact, so OCR is only spent on scanned pages and images.
Consecutive OCR pages are sent to the engine together, so multi-page engines (Unlimited-OCR)
keep their cross-page context.
"""

import logging
import re
import threading
from dataclasses import dataclass
from pathlib import PurePath
from typing import Literal

import pymupdf

from ved_inference.documents.loader import DocumentLoader
from ved_inference.documents.models import PageImage
from ved_inference.engines.base import OcrEngine
from ved_inference.errors import DocumentError

log = logging.getLogger(__name__)

Method = Literal["text", "ocr"]
_ALNUM = re.compile(r"\w", re.UNICODE)


@dataclass(frozen=True)
class ExtractedSegment:
    page_from: int
    page_to: int
    method: Method
    markdown: str


@dataclass(frozen=True)
class ExtractionResult:
    segments: list[ExtractedSegment]
    page_count: int
    engine: str

    @property
    def text_pages(self) -> int:
        return sum(s.page_to - s.page_from + 1 for s in self.segments if s.method == "text")

    @property
    def ocr_pages(self) -> int:
        return sum(s.page_to - s.page_from + 1 for s in self.segments if s.method == "ocr")


class DocumentExtractor:
    """Use-case: document bytes in, per-page Markdown out."""

    def __init__(
        self,
        ocr_engine: OcrEngine,
        loader: DocumentLoader,
        *,
        max_pages: int,
        pdf_dpi: int,
        native_min_chars: int,
        max_concurrency: int,
    ) -> None:
        self._ocr = ocr_engine
        self._loader = loader
        self._max_pages = max_pages
        self._pdf_dpi = pdf_dpi
        self._native_min_chars = native_min_chars
        self._ocr_slots = threading.BoundedSemaphore(max_concurrency)

    @property
    def ocr_engine_name(self) -> str:
        return self._ocr.name

    def extract(self, data: bytes, filename: str) -> ExtractionResult:
        if not data:
            raise DocumentError("Empty file")
        if PurePath(filename).suffix.lower() == ".pdf" or data.startswith(b"%PDF-"):
            return self._extract_pdf(data)
        document = self._loader.load(data, filename)
        segments = self._run_ocr(document.pages)
        return ExtractionResult(segments=segments, page_count=len(document.pages), engine=self._ocr.name)

    # --- PDF -----------------------------------------------------------------------------------

    def _extract_pdf(self, data: bytes) -> ExtractionResult:
        try:
            doc = pymupdf.open(stream=data, filetype="pdf")
        except (pymupdf.FileDataError, RuntimeError) as exc:
            raise DocumentError("Unreadable or corrupt PDF") from exc
        with doc:
            if doc.needs_pass:
                raise DocumentError("Password-protected PDFs are not supported")
            if doc.page_count == 0:
                raise DocumentError("Document has no pages")
            page_count = doc.page_count
            if page_count > self._max_pages:
                raise DocumentError(f"Document has {doc.page_count} pages; the limit is {self._max_pages}")

            native = [i for i in range(page_count) if self._has_text_layer(doc[i])]
            native_set = set(native)
            scanned = [i for i in range(page_count) if i not in native_set]
            segments = self._native_markdown(doc, native)
            for run in _contiguous_runs(scanned):
                segments += self._run_ocr([self._render(doc, i) for i in run])

        segments.sort(key=lambda s: s.page_from)
        engines = (["pymupdf"] if native else []) + ([self._ocr.name] if scanned else [])
        return ExtractionResult(segments=segments, page_count=page_count, engine="+".join(engines))

    def _has_text_layer(self, page: pymupdf.Page) -> bool:
        return len(_ALNUM.findall(page.get_text("text"))) >= self._native_min_chars

    def _native_markdown(self, doc: pymupdf.Document, indices: list[int]) -> list[ExtractedSegment]:
        if not indices:
            return []
        try:
            import pymupdf4llm

            chunks = pymupdf4llm.to_markdown(
                doc, pages=indices, page_chunks=True, use_ocr=False, show_progress=False
            )
            texts = [c["text"] for c in chunks]
            if len(texts) != len(indices):
                raise ValueError("unexpected page count from pymupdf4llm")
        except Exception:  # layout analysis is best-effort; plain text is always available
            log.warning("Markdown conversion failed, falling back to plain text", exc_info=True)
            texts = [doc[i].get_text("text") for i in indices]
        return [
            ExtractedSegment(page_from=i + 1, page_to=i + 1, method="text", markdown=_tidy(text))
            for i, text in zip(indices, texts, strict=True)
        ]

    def _render(self, doc: pymupdf.Document, index: int) -> PageImage:
        from PIL import Image

        zoom = self._pdf_dpi / 72
        pix = doc[index].get_pixmap(matrix=pymupdf.Matrix(zoom, zoom), alpha=False)
        return PageImage(number=index + 1, image=Image.frombytes("RGB", (pix.width, pix.height), pix.samples))

    # --- OCR -----------------------------------------------------------------------------------

    def _run_ocr(self, pages: list[PageImage]) -> list[ExtractedSegment]:
        with self._ocr_slots:
            output = self._ocr.extract(pages)
        return [
            ExtractedSegment(
                page_from=s.page_from, page_to=s.page_to, method="ocr", markdown=_tidy(s.markdown)
            )
            for s in output.segments
        ]

    def close(self) -> None:
        self._ocr.close()


def _contiguous_runs(indices: list[int]) -> list[list[int]]:
    runs: list[list[int]] = []
    for i in indices:
        if runs and runs[-1][-1] == i - 1:
            runs[-1].append(i)
        else:
            runs.append([i])
    return runs


def _tidy(text: str) -> str:
    lines = [line.rstrip() for line in text.splitlines()]
    return re.sub(r"\n{3,}", "\n\n", "\n".join(lines)).strip()
