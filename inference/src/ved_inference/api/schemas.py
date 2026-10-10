from typing import Literal

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class ApiModel(BaseModel):
    """JSON contracts use camelCase, matching the Java backend."""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class SegmentOut(ApiModel):
    page_from: int
    page_to: int
    method: Literal["text", "ocr"]
    markdown: str


class NodeOut(ApiModel):
    key: str
    parent_key: str | None
    level: int
    path: str
    label: str
    title: str
    ordinal: int
    page_from: int
    page_to: int


class ChunkOut(ApiModel):
    key: str
    node_key: str
    ordinal: int
    text: str
    page_from: int
    page_to: int


class FigureOut(ApiModel):
    key: str
    node_key: str
    chunk_key: str | None
    ordinal: int
    page: int
    bbox: list[float] | None
    caption: str
    mime: str
    width: int
    height: int
    image_base64: str


class TreeOut(ApiModel):
    nodes: list[NodeOut]
    chunks: list[ChunkOut]
    figures: list[FigureOut]


class ExtractResponse(ApiModel):
    markdown: str
    engine: str
    page_count: int
    text_pages: int
    ocr_pages: int
    segments: list[SegmentOut]
    tree: TreeOut


class HealthResponse(ApiModel):
    status: str
    ocr_engine: str
    llm_model: str
    embedding_model: str
