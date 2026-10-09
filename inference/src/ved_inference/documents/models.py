from dataclasses import dataclass

from PIL import Image


@dataclass(frozen=True)
class PageImage:
    """One page of a document, rasterised. ``number`` is 1-based."""

    number: int
    image: Image.Image


@dataclass(frozen=True)
class LoadedDocument:
    filename: str
    pages: list[PageImage]
