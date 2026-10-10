"""LLM outputs for indexing (internal; snake_case)."""

from typing import Literal

from pydantic import BaseModel, Field

FigureKind = Literal["DIAGRAM", "CHART", "PHOTO", "TABLE", "FORMULA", "MAP", "ILLUSTRATION", "DECORATIVE"]


class FigureDescription(BaseModel):
    index: int = Field(description="0-based position of the image in the request")
    kind: FigureKind
    educational: bool = Field(description="false for logos, decorations, icons, stock backgrounds")
    description: str = Field(
        description="2-4 sentences: what it shows, labels, and what a student should notice"
    )


class FigureDescriptions(BaseModel):
    figures: list[FigureDescription]


class NodeSummary(BaseModel):
    key: str
    summary: str = Field(description="2-4 sentences: what this section teaches")
    key_terms: list[str] = Field(
        description="3-8 important terms, names or formulas exactly as written in the text"
    )


class NodeSummaries(BaseModel):
    summaries: list[NodeSummary]
