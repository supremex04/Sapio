"""Wire contracts for /v1/study/* (see docs/specs/inference-api.md and knowledge.md).

Study endpoints never receive the whole document: the backend retrieves the relevant sections
(summary + passages) and figures (caption + description) from the knowledge store.
"""

from typing import Literal

from pydantic import Field

from ved_inference.api.schemas import ApiModel
from ved_inference.study.learner import LearnerIn
from ved_inference.study.models import Difficulty, MaterialKind, QuestionType, SectionKind, Verdict

AssessmentDifficulty = Literal["EASY", "MIXED", "HARD"]


class SectionIn(ApiModel):
    """One retrieved section. ``id`` is the citation id the model must use, e.g. "§2.3"."""

    id: str
    path: str = ""  # breadcrumb "Chapter 2 › 2.3 Osmosis"
    pages: str = ""
    summary: str = ""
    passages: list[str] = Field(default_factory=list)


class FigureRef(ApiModel):
    """A figure the model may embed as ![caption](figure:<id>)."""

    id: str
    section_id: str = ""
    caption: str = ""
    description: str = ""


# ---------------------------------------------------------------- material (learning path from the tree)


class OutlineNodeIn(ApiModel):
    key: str
    label: str
    title: str
    level: int
    pages: str = ""
    summary: str = ""
    key_terms: list[str] = Field(default_factory=list)


class MaterialRequest(ApiModel):
    filename: str
    sample: str = ""  # a few hundred characters of text, for language/kind detection
    nodes: list[OutlineNodeIn] = Field(min_length=1, max_length=600)


class TopicOut(ApiModel):
    position: int
    title: str
    summary: str
    key_concepts: list[str]
    difficulty: Difficulty
    node_keys: list[str]


class MaterialResponse(ApiModel):
    title: str
    summary: str
    language: str
    kind: MaterialKind
    topics: list[TopicOut]
    model: str


# ---------------------------------------------------------------- lesson


class TopicIn(ApiModel):
    title: str
    summary: str
    key_concepts: list[str]
    difficulty: Difficulty = "CORE"


class LessonRequest(ApiModel):
    material_title: str
    language: str = "en"
    kind: MaterialKind = "OTHER"
    topic: TopicIn
    previous_topics: list[str] = Field(default_factory=list)
    next_topics: list[str] = Field(default_factory=list)
    sections: list[SectionIn] = Field(min_length=1)
    figures: list[FigureRef] = Field(default_factory=list)
    #: Who the content is for (depth, vocabulary, examples); None = a general audience
    learner: LearnerIn | None = None


class SectionOut(ApiModel):
    kind: SectionKind
    heading: str
    body: str  # markdown; may embed ![caption](figure:<id>)


class CheckOut(ApiModel):
    after_section: int
    type: QuestionType
    prompt: str
    options: list[str]
    correct_option_index: int | None
    reference_answer: str | None
    explanation: str


class LessonResponse(ApiModel):
    sections: list[SectionOut]
    key_takeaways: list[str]
    checks: list[CheckOut]
    figure_ids: list[str]  # figures actually embedded
    model: str


# ---------------------------------------------------------------- questions


class QuestionTopicIn(TopicIn):
    key: str
    sections: list[SectionIn] = Field(min_length=1)


class QuestionsRequest(ApiModel):
    material_title: str
    language: str = "en"
    kind: MaterialKind = "OTHER"
    count: int = Field(ge=1, le=30)
    difficulty: AssessmentDifficulty = "MIXED"
    topics: list[QuestionTopicIn] = Field(min_length=1)
    avoid_prompts: list[str] = Field(default_factory=list)
    #: Who the content is for (depth, vocabulary, examples); None = a general audience
    learner: LearnerIn | None = None


class QuestionOut(ApiModel):
    topic_key: str
    type: QuestionType
    prompt: str
    options: list[str]
    correct_option_index: int | None
    reference_answer: str | None
    explanation: str
    source_section_ids: list[str]


class QuestionsResponse(ApiModel):
    questions: list[QuestionOut]
    model: str


# ---------------------------------------------------------------- grading & report


class QuestionToGrade(ApiModel):
    prompt: str
    reference_answer: str
    explanation: str = ""


class GradeRequest(ApiModel):
    language: str = "en"
    question: QuestionToGrade
    sections: list[SectionIn] = Field(default_factory=list)
    answer: str


class GradeResponse(ApiModel):
    verdict: Verdict
    score: float
    feedback: str
    model: str


class TopicResult(ApiModel):
    title: str
    question_count: int
    score: float


class MissedQuestion(ApiModel):
    topic_title: str
    prompt: str
    learner_answer: str
    correct_answer: str


class FeedbackRequest(ApiModel):
    material_title: str
    language: str = "en"
    score: float
    topics: list[TopicResult]
    missed: list[MissedQuestion] = Field(default_factory=list)
    #: Who the content is for (depth, vocabulary, examples); None = a general audience
    learner: LearnerIn | None = None


class WeakAreaOut(ApiModel):
    topic_title: str
    advice: str


class FeedbackResponse(ApiModel):
    summary: str
    strengths: list[str]
    weak_areas: list[WeakAreaOut]
    next_steps: list[str]
    model: str


# ---------------------------------------------------------------- answers & notes (long form)


class ChatTurn(ApiModel):
    role: Literal["user", "tutor"]
    text: str = Field(max_length=4000)


class AnswerRequest(ApiModel):
    material_title: str
    language: str = "en"
    question: str = Field(min_length=1, max_length=2000)
    history: list[ChatTurn] = Field(default_factory=list, max_length=20)
    sections: list[SectionIn] = Field(min_length=1)
    figures: list[FigureRef] = Field(default_factory=list)
    style: Literal["tutor", "answer"] = "answer"  # tutor = short conversational; answer = long-form
    # What the learner is looking at (a lesson section, or a check question with their answer), so short
    # follow-ups like "why?" are understood. Empty when unknown.
    focus: str = Field(default="", max_length=8000)
    #: Who the content is for (depth, vocabulary, examples); None = a general audience
    learner: LearnerIn | None = None


class AnswerResponse(ApiModel):
    answer: str
    grounded: bool
    cited_section_ids: list[str]
    figure_ids: list[str]
    model: str


class NotesRequest(ApiModel):
    material_title: str
    language: str = "en"
    kind: MaterialKind = "OTHER"
    title: str  # the node the notes are for
    path: str
    scope: Literal["section", "overview"]  # overview = chapter notes from child notes (reduce step)
    # expand = in-depth explanation (elaboration); revision = condensed revision notes (quick recall)
    style: Literal["expand", "revision"] = "expand"
    sections: list[SectionIn] = Field(min_length=1)
    figures: list[FigureRef] = Field(default_factory=list)
    #: Who the content is for (depth, vocabulary, examples); None = a general audience
    learner: LearnerIn | None = None


class NotesResponse(ApiModel):
    markdown: str
    figure_ids: list[str]
    cited_section_ids: list[str]
    model: str
