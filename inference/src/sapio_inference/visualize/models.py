"""What the LLM returns for visualizations (validated JSON)."""

from typing import Literal

from pydantic import BaseModel, Field

VisualKind = Literal["formula", "graph", "geometry", "physics", "process", "other"]


class Beat(BaseModel):
    shows: str = Field(
        description="What appears or moves on screen in this step (concrete: objects, colours, motion)"
    )
    says: str = Field(
        description="A short caption shown at the bottom during this step (one sentence, plain words)"
    )


class Storyboard(BaseModel):
    feasible: bool = Field(description="False when the request cannot be shown as a short 2D/3D animation")
    reason: str = Field(description="Why not, when not feasible; otherwise empty")
    title: str = Field(description="A short title for the visualization")
    kind: VisualKind
    beats: list[Beat] = Field(description="3-6 steps, in order")


class SceneCode(BaseModel):
    code: str = Field(
        description="The complete Python file: from manim import * and class Visualization(Scene)"
    )
