"""Turns uploaded bytes (PDF or image) into page images that every OCR engine understands."""

import io
from pathlib import PurePath

import pymupdf
from PIL import Image, ImageSequence, UnidentifiedImageError

from ved_inference.documents.models import LoadedDocument, PageImage
from ved_inference.errors import DocumentError

IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp", ".tif", ".tiff", ".bmp"}


class DocumentLoader:
    def __init__(self, max_pages: int, pdf_dpi: int) -> None:
        self._max_pages = max_pages
        self._pdf_dpi = pdf_dpi

    def load(self, data: bytes, filename: str) -> LoadedDocument:
        if not data:
            raise DocumentError("Empty file")
        ext = PurePath(filename).suffix.lower()
        if ext == ".pdf" or data.startswith(b"%PDF-"):
            pages = self._load_pdf(data)
        elif ext in IMAGE_EXTENSIONS or not ext:
            pages = self._load_image(data)
        else:
            raise DocumentError(f"Unsupported file type: {ext}")
        if not pages:
            raise DocumentError("Document has no pages")
        return LoadedDocument(filename=filename, pages=pages)

    def _load_pdf(self, data: bytes) -> list[PageImage]:
        try:
            doc = pymupdf.open(stream=data, filetype="pdf")
        except (pymupdf.FileDataError, RuntimeError) as exc:
            raise DocumentError("Unreadable or corrupt PDF") from exc
        with doc:
            if doc.needs_pass:
                raise DocumentError("Password-protected PDFs are not supported")
            self._check_page_count(doc.page_count)
            zoom = self._pdf_dpi / 72
            matrix = pymupdf.Matrix(zoom, zoom)
            pages = []
            for index, page in enumerate(doc):
                pix = page.get_pixmap(matrix=matrix, alpha=False)
                image = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
                pages.append(PageImage(number=index + 1, image=image))
            return pages

    def _load_image(self, data: bytes) -> list[PageImage]:
        try:
            with Image.open(io.BytesIO(data)) as img:
                frames = [frame.convert("RGB") for frame in ImageSequence.Iterator(img)]
        except (UnidentifiedImageError, OSError) as exc:
            raise DocumentError("Unreadable or corrupt image") from exc
        self._check_page_count(len(frames))  # multi-frame TIFFs are multi-page documents
        return [PageImage(number=i + 1, image=frame) for i, frame in enumerate(frames)]

    def _check_page_count(self, count: int) -> None:
        if count > self._max_pages:
            raise DocumentError(f"Document has {count} pages; the limit is {self._max_pages}")
