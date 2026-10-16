"""Wire shapes of /v1/visualize (camelCase JSON, like the rest of the API)."""

from typing import Literal

from pydantic import Field

from sapio_inference.api.schemas import ApiModel
from sapio_inference.study.learner import LearnerIn
from sapio_inference.visualize.models import Engine, VisualKind


class BeatIo(ApiModel):
    shows: str
    says: str
    #: What to point at, per engine: SMARTS (molecule), A:58-63 or ligand:HEM (protein), 3-5 (code lines)
    focus: str = ""


class MoleculeIo(ApiModel):
    name: str
    smiles: str


class ProteinIo(ApiModel):
    pdb_id: str
    name: str


class StoryboardRequest(ApiModel):
    #: What to visualize: a formula (LaTeX or plain), code, a molecule, or a question / idea in words
    request: str = Field(min_length=1, max_length=4000)
    language: str = "en"
    #: Optional surrounding material (e.g. the section the formula comes from)
    context: str | None = Field(default=None, max_length=8000)
    learner: LearnerIn | None = None
    #: auto: the storyboard picks the engine; anything else: use that engine (the learner chose it, or rules did)
    engine: Literal["auto", "animation", "molecule", "protein", "code"] = "auto"


class StoryboardResponse(ApiModel):
    feasible: bool
    reason: str
    title: str
    kind: VisualKind
    engine: Engine
    molecule: MoleculeIo | None = None
    protein: ProteinIo | None = None
    #: engine code: the Python program to trace
    program: str = ""
    beats: list[BeatIo]
    model: str


class SceneCodeRequest(ApiModel):
    title: str = Field(max_length=300)
    beats: list[BeatIo] = Field(min_length=1, max_length=8)
    #: A repair: the code that failed and what the renderer said
    previous_code: str | None = Field(default=None, max_length=30000)
    error: str | None = Field(default=None, max_length=6000)


class SceneCodeResponse(ApiModel):
    code: str
    model: str
