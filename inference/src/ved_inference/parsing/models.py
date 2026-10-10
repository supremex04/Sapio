"""Types of the structural parse: a typed block stream in, a document tree out."""

from dataclasses import dataclass, field
from typing import Literal

BlockKind = Literal["heading", "text", "table", "formula", "caption", "figure"]
BBox = tuple[float, float, float, float]


@dataclass
class FigureImage:
    data: bytes
    mime: str
    width: int
    height: int


@dataclass
class Block:
    """One layout element in reading order."""

    page: int  # 1-based
    kind: BlockKind
    text: str = ""
    level: int = 0  # headings only (1 = top level, from layout/outline)
    bbox: BBox | None = None
    image: FigureImage | None = None


@dataclass
class TreeNode:
    key: str
    parent_key: str | None
    level: int
    path: str  # ordinal path, e.g. "2.3.1"
    label: str  # the document's own numbering when present ("Unit 4", "2.3"), else the path
    title: str
    ordinal: int  # pre-order position in the whole document
    page_from: int
    page_to: int


@dataclass
class TreeChunk:
    key: str
    node_key: str
    ordinal: int
    text: str
    page_from: int
    page_to: int


@dataclass
class TreeFigure:
    key: str
    node_key: str
    chunk_key: str | None
    ordinal: int
    page: int
    bbox: BBox | None
    caption: str
    image: FigureImage


@dataclass
class DocumentTree:
    nodes: list[TreeNode] = field(default_factory=list)
    chunks: list[TreeChunk] = field(default_factory=list)
    figures: list[TreeFigure] = field(default_factory=list)
