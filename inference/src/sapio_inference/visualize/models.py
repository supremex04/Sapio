"""What the LLM returns for visualizations (validated JSON)."""

from typing import Literal

from pydantic import BaseModel, Field

VisualKind = Literal[
    "formula", "graph", "geometry", "physics", "process", "chemistry", "biology", "code", "other"
]
#: How it is shown: an animation (Manim code, rendered to video), a molecule (RDKit, 3D viewer), a protein or other
#: macromolecule from the Protein Data Bank (3D viewer), or a program traced line by line.
Engine = Literal["animation", "molecule", "protein", "code"]


class Beat(BaseModel):
    shows: str = Field(
        description="What appears or moves on screen in this step (concrete: objects, colours, motion)"
    )
    says: str = Field(
        description="The narration of this step: one sentence in plain spoken words, read aloud and shown as a "
        "subtitle"
    )
    focus: str = Field(
        default="",
        description="What to point at, by engine. molecule: group:name or a SMARTS of the atoms to highlight; "
        "protein: a chain (A), a residue with its name (A:HIS87), a range (A:58-63) or a ligand (ligand:HEM); "
        "code: the exact text of the line(s) explained, first | last for a block; animation: empty",
    )


class Molecule(BaseModel):
    name: str = Field(description="Common name of the molecule")
    smiles: str = Field(description="Its SMILES string (valid, with stereochemistry where it matters)")


class Protein(BaseModel):
    pdb_id: str = Field(description="The 4-character Protein Data Bank ID of a well-known entry")
    name: str = Field(description="What it is, e.g. human haemoglobin")


class Storyboard(BaseModel):
    feasible: bool = Field(description="False when the request cannot be shown visually")
    reason: str = Field(description="Why not, when not feasible; otherwise empty")
    title: str = Field(description="A short title for the visualization")
    kind: VisualKind
    engine: Engine
    molecule: Molecule | None = Field(default=None, description="Only for engine molecule")
    protein: Protein | None = Field(default=None, description="Only for engine protein")
    program: str = Field(
        default="",
        description="Only for engine code: the short Python program to trace (the learner's own if they gave "
        "Python), with line numbers matching the focus of the beats",
    )
    beats: list[Beat] = Field(description="3-6 steps, in order")


class SceneCode(BaseModel):
    code: str = Field(
        description="The complete Python file: from manim import * and class Visualization(Scene)"
    )
