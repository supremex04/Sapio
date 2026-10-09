import threading
from dataclasses import dataclass

from ved_inference.documents.loader import DocumentLoader
from ved_inference.engines.base import OcrEngine


@dataclass(frozen=True)
class OcrResult:
    markdown: str
    engine: str
    page_count: int


class OcrService:
    """Use-case: document bytes in, Markdown out. Independent of HTTP and of the concrete engine."""

    def __init__(self, loader: DocumentLoader, engine: OcrEngine, max_concurrency: int) -> None:
        self._loader = loader
        self._engine = engine
        self._slots = threading.BoundedSemaphore(max_concurrency)

    @property
    def engine_name(self) -> str:
        return self._engine.name

    def convert(self, data: bytes, filename: str) -> OcrResult:
        document = self._loader.load(data, filename)
        with self._slots:
            output = self._engine.extract(document.pages)
        return OcrResult(markdown=output.markdown, engine=self._engine.name, page_count=len(document.pages))

    def close(self) -> None:
        self._engine.close()
