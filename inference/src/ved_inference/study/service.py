"""Study use-cases: material outline, lessons, questions, grading, report feedback, tutor.

The LLM does the writing; this module owns the rules: grounding (only the given chunks),
validation of everything the model returns, and fairness fixes such as shuffling options.
"""

import random
from collections.abc import Sequence

from ved_inference.api import study_schemas as api
from ved_inference.errors import DocumentError, EngineError
from ved_inference.llm.base import LlmClient
from ved_inference.study import prompts
from ved_inference.study.chunking import PageText, chunk_pages
from ved_inference.study.models import (
    AssessmentFeedback,
    ChoiceCheck,
    Grade,
    Lesson,
    Outline,
    QuestionSet,
    TutorAnswer,
)

OUTLINE_CHAR_BUDGET = 300_000
LESSON_CHAR_BUDGET = 40_000
QUESTION_TOPIC_CHAR_BUDGET = 12_000
MAX_TOPICS = 12


class StudyService:
    def __init__(self, llm: LlmClient, rng: random.Random | None = None) -> None:
        self._llm = llm
        self._rng = rng or random.Random()

    # --- material --------------------------------------------------------------------------------

    def build_material(self, request: api.MaterialRequest) -> api.MaterialResponse:
        chunks = chunk_pages([PageText(p.page_from, p.page_to, p.text) for p in request.pages])
        if not chunks or sum(len(c.content) for c in chunks) < 80:
            raise DocumentError("The document contains too little readable text to study from")

        listing = _fit_listing([(c.position, c.page_from, c.page_to, c.heading, c.content) for c in chunks])
        min_topics = 1 if len(chunks) <= 2 else 3
        max_topics = max(min_topics, min(MAX_TOPICS, len(chunks) + 1))
        outline = self._llm.generate(
            system=prompts.OUTLINE_SYSTEM,
            prompt=prompts.outline_prompt(request.filename, listing, min_topics, max_topics),
            schema=Outline,
            temperature=0.2,
        )

        valid = {c.position for c in chunks}
        topics: list[api.TopicOut] = []
        for t in outline.topics[:MAX_TOPICS]:
            positions = sorted({p for p in t.chunk_positions if p in valid})
            if not positions or not t.title.strip():
                continue
            topics.append(
                api.TopicOut(
                    position=len(topics) + 1,
                    title=t.title.strip(),
                    summary=t.summary.strip(),
                    key_concepts=[k.strip() for k in t.key_concepts if k.strip()][:6],
                    difficulty=t.difficulty,
                    chunk_positions=positions,
                )
            )
        if not topics:
            raise EngineError("Could not organise the document into topics. Please retry.")

        return api.MaterialResponse(
            title=outline.title.strip() or request.filename,
            summary=outline.summary.strip(),
            language=(outline.language or "en").strip().lower()[:8],
            kind=outline.kind,
            chunks=[
                api.ChunkOut(
                    position=c.position,
                    heading=c.heading,
                    content=c.content,
                    page_from=c.page_from,
                    page_to=c.page_to,
                )
                for c in chunks
            ],
            topics=topics,
            model=self._llm.model_name,
        )

    # --- learn -----------------------------------------------------------------------------------

    def lesson(self, request: api.LessonRequest) -> api.LessonResponse:
        lesson = self._llm.generate(
            system=prompts.system(request.language),
            prompt=prompts.lesson_prompt(
                material_title=request.material_title,
                kind=request.kind,
                topic_title=request.topic.title,
                topic_summary=request.topic.summary,
                key_concepts=request.topic.key_concepts,
                difficulty=request.topic.difficulty,
                previous_topics=request.previous_topics,
                next_topics=request.next_topics,
                sources=_sources_text(request.sources, LESSON_CHAR_BUDGET),
            ),
            schema=Lesson,
            temperature=0.5,
        )
        sections = [s for s in lesson.sections if s.body.strip()]
        if not sections:
            raise EngineError("The lesson came back empty. Please retry.")
        last = len(sections) - 1
        # Checks may not follow the closing summary; clamp to the last teaching section.
        last_teaching = last - 1 if sections[last].kind == "SUMMARY" and last > 0 else last

        checks: list[api.CheckOut] = []
        warmup = self._choice(lesson.warmup, after_section=-1)
        if warmup:
            checks.append(warmup)
        for c in lesson.checks[:3]:
            placed = self._choice(c, after_section=min(max(c.after_section, 0), last_teaching))
            if placed:
                checks.append(placed)
        if lesson.explain_back.prompt.strip():
            checks.append(
                api.CheckOut(
                    after_section=last,
                    type="SHORT_ANSWER",
                    prompt=lesson.explain_back.prompt.strip(),
                    options=[],
                    correct_option_index=None,
                    reference_answer=lesson.explain_back.reference_answer.strip(),
                    explanation=lesson.explain_back.reference_answer.strip(),
                )
            )
        checks.sort(key=lambda c: c.after_section)
        return api.LessonResponse(
            sections=[
                api.SectionOut(kind=s.kind, heading=s.heading.strip(), body=s.body.strip()) for s in sections
            ],
            key_takeaways=[k.strip() for k in lesson.key_takeaways if k.strip()][:5],
            checks=checks,
            model=self._llm.model_name,
        )

    # --- test ------------------------------------------------------------------------------------

    def questions(self, request: api.QuestionsRequest) -> api.QuestionsResponse:
        topics_block = "\n\n".join(
            f"### Topic key: {t.key}\nTitle: {t.title}\nSummary: {t.summary}\nKey concepts: {', '.join(t.key_concepts)}\n"
            f"SOURCE:\n{_sources_text(t.sources, QUESTION_TOPIC_CHAR_BUDGET)}"
            for t in request.topics
        )
        result = self._llm.generate(
            system=prompts.system(request.language),
            prompt=prompts.questions_prompt(
                material_title=request.material_title,
                kind=request.kind,
                count=request.count,
                difficulty=request.difficulty,
                topics_block=topics_block,
                avoid=request.avoid_prompts,
            ),
            schema=QuestionSet,
            temperature=0.6,
        )
        sources_by_topic = {t.key: {s.position for s in t.sources} for t in request.topics}
        out: list[api.QuestionOut] = []
        for q in result.questions:
            if q.topic_key not in sources_by_topic or not q.prompt.strip():
                continue
            cited = sorted({p for p in q.source_chunk_positions if p in sources_by_topic[q.topic_key]})
            if q.type == "SHORT_ANSWER":
                if not q.reference_answer.strip():
                    continue
                out.append(
                    api.QuestionOut(
                        topic_key=q.topic_key,
                        type="SHORT_ANSWER",
                        prompt=q.prompt.strip(),
                        options=[],
                        correct_option_index=None,
                        reference_answer=q.reference_answer.strip(),
                        explanation=q.explanation.strip(),
                        source_chunk_positions=cited,
                    )
                )
                continue
            choice = self._choice(
                ChoiceCheck(
                    type=q.type,
                    prompt=q.prompt,
                    options=q.options,
                    correct_index=q.correct_index,
                    explanation=q.explanation,
                ),
                after_section=0,
            )
            if choice:
                out.append(
                    api.QuestionOut(
                        topic_key=q.topic_key,
                        type=choice.type,
                        prompt=choice.prompt,
                        options=choice.options,
                        correct_option_index=choice.correct_option_index,
                        reference_answer=choice.reference_answer,
                        explanation=choice.explanation,
                        source_chunk_positions=cited,
                    )
                )
        if not out:
            raise EngineError("Could not generate questions. Please retry.")
        return api.QuestionsResponse(questions=out[: request.count], model=self._llm.model_name)

    def grade(self, request: api.GradeRequest) -> api.GradeResponse:
        if not request.answer.strip():
            return api.GradeResponse(
                verdict="INCORRECT",
                score=0.0,
                feedback=f"No answer given. A good answer would be: {request.question.reference_answer}",
                model="rule",
            )
        grade = self._llm.generate(
            system=prompts.system(request.language),
            prompt=prompts.grade_prompt(
                prompt=request.question.prompt,
                reference=request.question.reference_answer,
                explanation=request.question.explanation,
                sources=_sources_text(request.sources, 8_000) or "(not provided)",
                answer=request.answer[:4000],
            ),
            schema=Grade,
            temperature=0.0,
        )
        score = _clamp_to_verdict(grade.verdict, grade.score)
        return api.GradeResponse(
            verdict=grade.verdict, score=score, feedback=grade.feedback.strip(), model=self._llm.model_name
        )

    def assessment_feedback(self, request: api.FeedbackRequest) -> api.FeedbackResponse:
        topics_block = "\n".join(
            f"- {t.title}: {round(t.score * 100)}% over {t.question_count} question(s)"
            for t in request.topics
        )
        missed_block = "\n".join(
            f"- [{m.topic_title}] Q: {m.prompt} | learner: {m.learner_answer or '(skipped)'} | correct: {m.correct_answer}"
            for m in request.missed[:20]
        )
        fb = self._llm.generate(
            system=prompts.system(request.language),
            prompt=prompts.feedback_prompt(
                material_title=request.material_title,
                score=request.score,
                topics_block=topics_block,
                missed_block=missed_block,
            ),
            schema=AssessmentFeedback,
            temperature=0.4,
        )
        return api.FeedbackResponse(
            summary=fb.summary.strip(),
            strengths=[s.strip() for s in fb.strengths if s.strip()][:4],
            weak_areas=[api.WeakAreaOut(topic_title=w.topic_title, advice=w.advice) for w in fb.weak_areas][
                :6
            ],
            next_steps=[s.strip() for s in fb.next_steps if s.strip()][:4],
            model=self._llm.model_name,
        )

    def ask(self, request: api.AskRequest) -> api.AskResponse:
        history = "\n".join(
            f"{'Learner' if t.role == 'user' else 'Tutor'}: {t.text}" for t in request.history[-10:]
        )
        answer = self._llm.generate(
            system=prompts.system(request.language),
            prompt=prompts.ask_prompt(
                material_title=request.material_title,
                topic_title=request.topic_title,
                sources=_sources_text(request.sources, LESSON_CHAR_BUDGET),
                history=history,
                question=request.question,
            ),
            schema=TutorAnswer,
            temperature=0.4,
        )
        return api.AskResponse(
            answer=answer.answer.strip(), grounded=answer.grounded, model=self._llm.model_name
        )

    # --- helpers ---------------------------------------------------------------------------------

    def _choice(self, check: ChoiceCheck, *, after_section: int) -> api.CheckOut | None:
        """Validates a choice question and shuffles multiple-choice options (LLMs favour certain positions)."""
        options = [o.strip() for o in check.options if o.strip()]
        if not check.prompt.strip() or not 0 <= check.correct_index < len(options):
            return None
        correct_text = options[check.correct_index]
        if check.type == "TRUE_FALSE":
            if len(options) != 2:
                return None
            options = [o[:1].upper() + o[1:] for o in options]  # "true" -> "True" (any language)
            correct_text = options[check.correct_index]
            correct = check.correct_index  # keep true/false order stable
        else:
            if len(options) < 3 or len(set(options)) != len(options):
                return None
            self._rng.shuffle(options)
            correct = options.index(correct_text)
        return api.CheckOut(
            after_section=after_section,
            type=check.type,
            prompt=check.prompt.strip(),
            options=options,
            correct_option_index=correct,
            reference_answer=correct_text,
            explanation=check.explanation.strip(),
        )


def _sources_text(sources: Sequence[api.SourceChunk], budget: int) -> str:
    parts, used = [], 0
    for s in sources:
        remaining = budget - used
        if remaining <= 200:
            break
        content = s.content if len(s.content) <= remaining else s.content[:remaining] + "…"
        parts.append((s.position, s.heading, content))
        used += len(content)
    return prompts.format_sources(parts)


def _fit_listing(chunks: list[tuple[int, int, int, str, str]]) -> str:
    """Lists every chunk; truncates each evenly if the whole document exceeds the outline budget."""
    total = sum(len(c[4]) for c in chunks)
    per_chunk = None if total <= OUTLINE_CHAR_BUDGET else max(300, OUTLINE_CHAR_BUDGET // len(chunks))
    lines = []
    for pos, p_from, p_to, heading, content in chunks:
        pages = f"p. {p_from}" if p_from == p_to else f"p. {p_from}-{p_to}"
        body = content if per_chunk is None or len(content) <= per_chunk else content[:per_chunk] + "…"
        lines.append(f"[C{pos}] ({pages}) {heading}\n{body}")
    return "\n\n".join(lines)


def _clamp_to_verdict(verdict: str, score: float) -> float:
    low, high = {"CORRECT": (0.8, 1.0), "PARTIAL": (0.3, 0.7), "INCORRECT": (0.0, 0.2)}[verdict]
    return round(min(max(score, low), high), 3)
