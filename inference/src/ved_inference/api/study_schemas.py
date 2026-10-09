"""Wire contracts for /v1/study/* (see docs/specs/inference-api.md)."""

from typing import Literal

from pydantic import Field

from ved_inference.api.schemas import ApiModel
from ved_inference.study.models import Difficulty, MaterialKind, QuestionType, SectionKind, Verdict

AssessmentDifficulty = Literal["EASY", "MIXED", "HARD"]


class SourceChunk(ApiModel):
    position: int
    heading: str = ""
    content: str


class PageTextIn(ApiModel):
    page_from: int
    page_to: int
    text: str


class MaterialRequest(ApiModel):
    filename: str
    pages: list[PageTextIn] = Field(min_length=1)


class ChunkOut(ApiModel):
    position: int
    heading: str
    content: str
    page_from: int
    page_to: int


class TopicOut(ApiModel):
    position: int
    title: str
    summary: str
    key_concepts: list[str]
    difficulty: Difficulty
    chunk_positions: list[int]


class MaterialResponse(ApiModel):
    title: str
    summary: str
    language: str
    kind: MaterialKind
    chunks: list[ChunkOut]
    topics: list[TopicOut]
    model: str


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
    sources: list[SourceChunk] = Field(min_length=1)


class SectionOut(ApiModel):
    kind: SectionKind
    heading: str
    body: str


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
    model: str


class QuestionTopicIn(TopicIn):
    key: str
    sources: list[SourceChunk] = Field(min_length=1)


class QuestionsRequest(ApiModel):
    material_title: str
    language: str = "en"
    kind: MaterialKind = "OTHER"
    count: int = Field(ge=1, le=30)
    difficulty: AssessmentDifficulty = "MIXED"
    topics: list[QuestionTopicIn] = Field(min_length=1)
    avoid_prompts: list[str] = Field(default_factory=list)


class QuestionOut(ApiModel):
    topic_key: str
    type: QuestionType
    prompt: str
    options: list[str]
    correct_option_index: int | None
    reference_answer: str | None
    explanation: str
    source_chunk_positions: list[int]


class QuestionsResponse(ApiModel):
    questions: list[QuestionOut]
    model: str


class QuestionToGrade(ApiModel):
    prompt: str
    reference_answer: str
    explanation: str = ""


class GradeRequest(ApiModel):
    language: str = "en"
    question: QuestionToGrade
    sources: list[SourceChunk] = Field(default_factory=list)
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


class WeakAreaOut(ApiModel):
    topic_title: str
    advice: str


class FeedbackResponse(ApiModel):
    summary: str
    strengths: list[str]
    weak_areas: list[WeakAreaOut]
    next_steps: list[str]
    model: str


class ChatTurn(ApiModel):
    role: Literal["user", "tutor"]
    text: str = Field(max_length=4000)


class AskRequest(ApiModel):
    material_title: str
    language: str = "en"
    topic_title: str
    sources: list[SourceChunk] = Field(min_length=1)
    question: str = Field(min_length=1, max_length=2000)
    history: list[ChatTurn] = Field(default=[], max_length=20)


class AskResponse(ApiModel):
    answer: str
    grounded: bool
    model: str
