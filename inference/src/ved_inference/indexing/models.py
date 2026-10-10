"""LLM outputs for indexing (internal; snake_case)."""

from typing import Literal

from pydantic import BaseModel, Field

FigureKind = Literal[
    "DIAGRAM", "CHART", "PHOTO", "SCREENSHOT", "TABLE", "FORMULA", "MAP", "ILLUSTRATION", "DECORATIVE"
]
# VISUAL: the meaning is in the picture (layout, arrows, shapes, data); shown to students.
# TEXTUAL: the meaning is its text (terminal output, code, text-only slide); kept as an annotated transcript.
# DECORATIVE: logos, ornaments, stock imagery; dropped.
FigureRole = Literal["VISUAL", "TEXTUAL", "DECORATIVE"]


class FigureDescription(BaseModel):
    index: int = Field(description="0-based position of the image in the request")
    kind: FigureKind
    role: FigureRole = Field(
        description="VISUAL if a student learns from seeing it; TEXTUAL if its text alone carries the "
        "meaning; DECORATIVE for logos, ornaments, stock images. When unsure between VISUAL and TEXTUAL: VISUAL"
    )
    description: str = Field(
        description="2-4 sentences: what it shows, labels, and what a student should notice"
    )
    transcript: str = Field(
        description="TEXTUAL only: the important text verbatim (commands, key output lines, code), "
        "at most ~25 lines; empty otherwise"
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
