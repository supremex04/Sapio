"""Structured outputs requested from the LLM (internal; snake_case, no optionals)."""

from typing import Literal

from pydantic import BaseModel, Field

Difficulty = Literal["INTRO", "CORE", "ADVANCED"]
MaterialKind = Literal["SYLLABUS", "TEXTBOOK", "LECTURE_NOTES", "HANDOUT", "OTHER"]
ChoiceType = Literal["MULTIPLE_CHOICE", "TRUE_FALSE"]
QuestionType = Literal["MULTIPLE_CHOICE", "TRUE_FALSE", "SHORT_ANSWER"]
SectionKind = Literal["CONCEPT", "EXAMPLE", "ANALOGY", "SUMMARY"]
Verdict = Literal["CORRECT", "PARTIAL", "INCORRECT"]


class OutlineTopic(BaseModel):
    title: str = Field(description="Short topic title, at most 8 words")
    summary: str = Field(description="1-2 sentences: what the learner will understand")
    key_concepts: list[str] = Field(description="2-6 short key terms or ideas")
    difficulty: Difficulty
    chunk_positions: list[int] = Field(description="Positions [C#] of all chunks covering this topic")


class Outline(BaseModel):
    title: str = Field(description="Concise title of the material")
    summary: str = Field(description="2-4 sentence overview in Markdown")
    language: str = Field(description="ISO 639-1 code of the material's main language")
    kind: MaterialKind
    topics: list[OutlineTopic]


class ChoiceCheck(BaseModel):
    type: ChoiceType
    prompt: str
    options: list[str] = Field(
        description="4 options for MULTIPLE_CHOICE; exactly 2 (true, false) for TRUE_FALSE"
    )
    correct_index: int
    explanation: str = Field(description="Why the answer is right, citing the source")


class PlacedCheck(ChoiceCheck):
    after_section: int = Field(description="0-based index of the section this check follows")


class LessonSection(BaseModel):
    kind: SectionKind
    heading: str
    body: str = Field(description="Markdown, 80-250 words")


class ExplainBack(BaseModel):
    prompt: str = Field(description="Ask the learner to explain the core idea in their own words")
    reference_answer: str = Field(description="A model answer (2-4 sentences)")


class Lesson(BaseModel):
    warmup: ChoiceCheck = Field(
        description="Pre-question asked BEFORE teaching, answerable by reasoning/guessing"
    )
    sections: list[LessonSection]
    checks: list[PlacedCheck]
    explain_back: ExplainBack
    key_takeaways: list[str]


class GeneratedQuestion(BaseModel):
    topic_key: str
    type: QuestionType
    prompt: str
    options: list[str] = Field(
        description="4 for MULTIPLE_CHOICE, 2 (true, false) for TRUE_FALSE, empty for SHORT_ANSWER"
    )
    correct_index: int = Field(description="Index of the correct option; -1 for SHORT_ANSWER")
    reference_answer: str = Field(
        description="Model answer for SHORT_ANSWER; the correct option text otherwise"
    )
    explanation: str
    source_chunk_positions: list[int]


class QuestionSet(BaseModel):
    questions: list[GeneratedQuestion]


class Grade(BaseModel):
    verdict: Verdict
    score: float = Field(description="0.0-1.0")
    feedback: str = Field(description="2-4 sentences of specific, encouraging feedback")


class WeakArea(BaseModel):
    topic_title: str
    advice: str


class AssessmentFeedback(BaseModel):
    summary: str
    strengths: list[str]
    weak_areas: list[WeakArea]
    next_steps: list[str]


class TutorAnswer(BaseModel):
    answer: str
    grounded: bool = Field(description="false if the source does not cover the question")
