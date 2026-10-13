from abc import ABC, abstractmethod
from dataclasses import dataclass

from sapio_inference.documents.models import PageImage


@dataclass(frozen=True)
class OcrSegment:
    """Text recognised for a run of pages (a single page for per-page engines)."""

    page_from: int
    page_to: int
    markdown: str


@dataclass(frozen=True)
class EngineOutput:
    segments: list[OcrSegment]


class OcrEngine(ABC):
    """Converts page images to Markdown.

    Implementations must be safe to call from worker threads; callers serialise calls up to
    ``max_concurrency``. Add a new engine by subclassing this and registering it in ``registry.py``.
    """

    name: str

    @abstractmethod
    def extract(self, pages: list[PageImage]) -> EngineOutput: ...

    def close(self) -> None:
        """Release resources (HTTP clients, GPU memory)."""
