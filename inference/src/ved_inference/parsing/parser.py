"""Document → (per-page text, structured tree with chunks and figures). Runs once per document."""

import logging
import re
import threading
from dataclasses import dataclass
from pathlib import PurePath

import pymupdf

from ved_inference.engines.base import OcrEngine
from ved_inference.errors import DocumentError
from ved_inference.parsing.figures import detect_scanned_figures, filter_figures, insert_by_position
from ved_inference.parsing.language import detect_language
from ved_inference.parsing.layout import blocks_from_markdown, read_layout
from ved_inference.parsing.models import Block, DocumentTree
from ved_inference.parsing.tree import build_tree

log = logging.getLogger(__name__)

_ALNUM = re.compile(r"\w", re.UNICODE)
IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp", ".tif", ".tiff", ".bmp"}


@dataclass(frozen=True)
class PageText:
    page: int
    method: str  # "text" | "ocr"
    markdown: str


@dataclass(frozen=True)
class ParsedDocument:
    pages: list[PageText]
    tree: DocumentTree
    page_count: int
    engine: str
    language: str  # ISO 639-1 or "und"

    @property
    def text_pages(self) -> int:
        return sum(1 for p in self.pages if p.method == "text")

    @property
    def ocr_pages(self) -> int:
        return sum(1 for p in self.pages if p.method == "ocr")


class DocumentParser:
    """Native text where a PDF page has a text layer; OCR elsewhere.

    ``ocr_engine=None`` means the built-in layout OCR (RapidOCR inside the layout model), which also
    recovers headings and captions on scans. A model engine (Unlimited-OCR) returns markdown instead.
    """

    def __init__(
        self,
        ocr_engine: OcrEngine | None,
        *,
        max_pages: int,
        dpi: int,
        native_min_chars: int,
        max_concurrency: int,
    ) -> None:
        self._engine = ocr_engine
        self._max_pages = max_pages
        self._dpi = dpi
        self._native_min_chars = native_min_chars
        self._slots = threading.BoundedSemaphore(max_concurrency)

    @property
    def ocr_engine_name(self) -> str:
        return self._engine.name if self._engine else "rapidocr-layout"

    def parse(self, data: bytes, filename: str) -> ParsedDocument:
        doc = self._open(data, filename)
        with doc, self._slots:
            page_count = doc.page_count
            if page_count == 0:
                raise DocumentError("Document has no pages")
            if page_count > self._max_pages:
                raise DocumentError(f"Document has {page_count} pages; the limit is {self._max_pages}")

            native = [i for i in range(page_count) if self._has_text_layer(doc[i])]
            scanned = [i for i in range(page_count) if i not in set(native)]
            by_page = read_layout(doc, native, ocr=False, dpi=self._dpi)

            if scanned and self._engine is None:
                ocr_pages = read_layout(doc, scanned, ocr=True, dpi=self._dpi)
                for page_no, blocks in ocr_pages.items():
                    extra = detect_scanned_figures(doc[page_no - 1], blocks, dpi=self._dpi)
                    by_page[page_no] = insert_by_position(blocks, extra)
            elif scanned:
                by_page.update(self._engine_blocks(doc, scanned))

            page_areas = {i + 1: doc[i].rect.width * doc[i].rect.height for i in range(page_count)}
            ordered = [b for page in range(1, page_count + 1) for b in by_page.get(page, [])]
            blocks = filter_figures(ordered, page_areas, text_pages={i + 1 for i in native})
            if not any(b.kind != "figure" and b.text.strip() for b in blocks):
                raise DocumentError("No readable text was found in this document")

            tree = build_tree(blocks, page_count)
            pages = [
                PageText(
                    page=p,
                    method="text" if (p - 1) in native else "ocr",
                    markdown=_page_markdown(by_page.get(p, [])),
                )
                for p in range(1, page_count + 1)
            ]
            engines = (["pymupdf"] if native else []) + ([self.ocr_engine_name] if scanned else [])
            language = detect_language(" ".join(c.text for c in tree.chunks[:40]))
            return ParsedDocument(
                pages=pages, tree=tree, page_count=page_count, engine="+".join(engines), language=language
            )

    # ------------------------------------------------------------------------------------------

    def _open(self, data: bytes, filename: str) -> pymupdf.Document:
        if not data:
            raise DocumentError("Empty file")
        ext = PurePath(filename).suffix.lower()
        try:
            if ext == ".pdf" or data.startswith(b"%PDF-"):
                doc = pymupdf.open(stream=data, filetype="pdf")
                if doc.needs_pass:
                    raise DocumentError("Password-protected PDFs are not supported")
                return doc
            if ext in IMAGE_EXTENSIONS or not ext:
                # Images (incl. multi-frame TIFF) become a PDF so every input takes the same path.
                img = pymupdf.open(stream=data, filetype=ext.lstrip(".") or None)
                return pymupdf.open("pdf", img.convert_to_pdf())
        except DocumentError:
            raise
        except Exception as exc:
            raise DocumentError("Unreadable or corrupt file") from exc
        raise DocumentError(f"Unsupported file type: {ext}")

    def _has_text_layer(self, page: pymupdf.Page) -> bool:
        return len(_ALNUM.findall(page.get_text("text"))) >= self._native_min_chars

    def _engine_blocks(self, doc: pymupdf.Document, scanned: list[int]) -> dict[int, list[Block]]:
        from PIL import Image

        from ved_inference.documents.models import PageImage

        assert self._engine is not None
        zoom = self._dpi / 72
        images = []
        for i in scanned:
            pix = doc[i].get_pixmap(matrix=pymupdf.Matrix(zoom, zoom), alpha=False)
            images.append(
                PageImage(number=i + 1, image=Image.frombytes("RGB", (pix.width, pix.height), pix.samples))
            )
        out: dict[int, list[Block]] = {}
        for seg in self._engine.extract(images).segments:
            out.setdefault(seg.page_from, []).extend(
                blocks_from_markdown(seg.markdown, seg.page_from, seg.page_to)
            )
        return out

    def close(self) -> None:
        if self._engine:
            self._engine.close()


def _page_markdown(blocks: list[Block]) -> str:
    """Readable per-page text for the 'Extracted text' view (figures become placeholders)."""
    parts = []
    for b in blocks:
        if b.kind == "heading":
            parts.append(f"{'#' * min(max(b.level, 1), 6)} {b.text}")
        elif b.kind == "figure":
            parts.append("*[Figure]*")
        else:
            parts.append(b.text)
    return "\n\n".join(p for p in parts if p.strip())
