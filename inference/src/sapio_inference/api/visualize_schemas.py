"""Wire shapes of /v1/visualize (camelCase JSON, like the rest of the API)."""

from pydantic import Field

from sapio_inference.api.schemas import ApiModel
from sapio_inference.study.learner import LearnerIn
from sapio_inference.visualize.models import VisualKind


class BeatIo(ApiModel):
    shows: str
    says: str


class StoryboardRequest(ApiModel):
    #: What to visualize: a formula (LaTeX or plain) or a question / idea in words
    request: str = Field(min_length=1, max_length=2000)
    language: str = "en"
    #: Optional surrounding material (e.g. the section the formula comes from)
    context: str | None = Field(default=None, max_length=8000)
    learner: LearnerIn | None = None


class StoryboardResponse(ApiModel):
    feasible: bool
    reason: str
    title: str
    kind: VisualKind
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
