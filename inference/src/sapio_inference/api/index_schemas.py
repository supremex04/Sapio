"""Wire contracts for /v1/index/* (see docs/specs/knowledge.md)."""

import base64
from typing import Literal

from pydantic import Field

from sapio_inference.api.schemas import ApiModel


class FigureIn(ApiModel):
    key: str
    image_base64: str
    mime: str = "image/png"
    caption: str = ""
    context: str = ""  # breadcrumb of the section the figure is in

    def image_bytes(self) -> bytes:
        return base64.b64decode(self.image_base64)


class DescribeFiguresRequest(ApiModel):
    language: str = "en"
    figures: list[FigureIn] = Field(min_length=1, max_length=60)


class FigureDescriptionOut(ApiModel):
    key: str
    kind: str
    role: Literal["VISUAL", "TEXTUAL", "DECORATIVE"]
    description: str
    transcript: str = ""  # TEXTUAL figures: their important text, replacing the image


class DescribeFiguresResponse(ApiModel):
    figures: list[FigureDescriptionOut]
    model: str


class SummarizeNode(ApiModel):
    key: str
    path: str  # breadcrumb, e.g. "Chapter 2 › 2.3 Osmosis"
    text: str = ""
    child_summaries: list[str] = Field(default_factory=list)


class SummarizeRequest(ApiModel):
    language: str = "en"
    nodes: list[SummarizeNode] = Field(min_length=1, max_length=120)


class NodeSummaryOut(ApiModel):
    key: str
    summary: str
    key_terms: list[str]


class SummarizeResponse(ApiModel):
    summaries: list[NodeSummaryOut]
    model: str


class EmbedItemIn(ApiModel):
    text: str | None = None
    image_base64: str | None = None
    mime: str | None = None

    def image_bytes(self) -> bytes:
        return base64.b64decode(self.image_base64 or "")


class EmbedRequest(ApiModel):
    task: Literal["RETRIEVAL_DOCUMENT", "RETRIEVAL_QUERY"]
    items: list[EmbedItemIn] = Field(min_length=1, max_length=256)


class EmbedResponse(ApiModel):
    vectors: list[list[float]]
    model: str
    dimensions: int
