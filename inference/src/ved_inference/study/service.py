"""Study use-cases on structured context: learning path, lessons, questions, grading, answers, notes.

The LLM writes; this module owns the rules: it only ever sees retrieved sections and figures (never
the whole document), and it validates everything the model returns (references, figure ids,
citations, answer keys) and fixes known biases such as answer-position bias.
"""

import random
import re
from collections.abc import Iterable

from ved_inference.api import study_schemas as api
from ved_inference.errors import EngineError
from ved_inference.llm.base import LlmClient
from ved_inference.study import prompts
from ved_inference.study.models import (
    AssessmentFeedback,
    ChoiceCheck,
    Grade,
    Lesson,
    LongAnswer,
    Notes,
    Outline,
    QuestionSet,
)

MAX_TOPICS = 12
OUTLINE_SUMMARY_CHARS = 400

# Every markdown image the model writes; the target is validated against the figures we provided.
_IMAGE = re.compile(r"!\[([^\]]*)\]\(\s*<?([^)\s>]*)>?(?:\s+\"[^\"]*\")?\s*\)")
_CITATION = re.compile(r"\[§([^\]\s]+)\]")


class StudyService:
    def __init__(self, llm: LlmClient, rng: random.Random | None = None) -> None:
        self._llm = llm
        self._rng = rng or random.Random()

    # --- learning path from the document tree ----------------------------------------------------

    def build_material(self, request: api.MaterialRequest) -> api.MaterialResponse:
        nodes = request.nodes
        outline = "\n".join(_outline_line(n) for n in nodes)
        min_topics = 1 if len(nodes) <= 2 else 3
        max_topics = max(min_topics, min(MAX_TOPICS, len(nodes)))
        result = self._llm.generate(
            system=prompts.OUTLINE_SYSTEM,
            prompt=prompts.outline_prompt(request.filename, outline, request.sample, min_topics, max_topics),
            schema=Outline,
            temperature=0.2,
        )
        valid = {n.key for n in nodes}
        order = {n.key: i for i, n in enumerate(nodes)}
        topics: list[api.TopicOut] = []
        for t in result.topics[:MAX_TOPICS]:
            keys = sorted({k for k in t.node_keys if k in valid}, key=order.__getitem__)
            if not keys or not t.title.strip():
                continue
            topics.append(
                api.TopicOut(
                    position=len(topics) + 1,
                    title=t.title.strip(),
                    summary=t.summary.strip(),
                    key_concepts=[k.strip() for k in t.key_concepts if k.strip()][:6],
                    difficulty=t.difficulty,
                    node_keys=keys,
                )
            )
        if not topics:
            raise EngineError("Could not organise the document into topics. Please retry.")
        return api.MaterialResponse(
            title=result.title.strip() or request.filename,
            summary=result.summary.strip(),
            language=(result.language or "en").strip().lower()[:8],
            kind=result.kind,
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
                context=prompts.render_context(request.sections, request.figures),
            ),
            schema=Lesson,
            temperature=0.5,
        )
        allowed = {f.id for f in request.figures}
        sections_ids = {s.id for s in request.sections}
        used: list[str] = []
        sections = []
        for s in lesson.sections:
            if not s.body.strip():
                continue
            body = clean_markdown(s.body, allowed, sections_ids, used)
            sections.append(api.SectionOut(kind=s.kind, heading=s.heading.strip(), body=body))
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
            sections=sections,
            key_takeaways=[k.strip() for k in lesson.key_takeaways if k.strip()][:5],
            checks=checks,
            figure_ids=used,
            model=self._llm.model_name,
        )

    # --- test ------------------------------------------------------------------------------------

    def questions(self, request: api.QuestionsRequest) -> api.QuestionsResponse:
        topics_block = "\n\n".join(
            f"### Topic key: {t.key}\nTitle: {t.title}\nSummary: {t.summary}\nKey concepts: {', '.join(t.key_concepts)}\n"
            f"{prompts.render_context(t.sections, [])}"
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
        sections_by_topic = {t.key: {s.id for s in t.sections} for t in request.topics}
        out: list[api.QuestionOut] = []
        for q in result.questions:
            if q.topic_key not in sections_by_topic or not q.prompt.strip():
                continue
            cited = [s for s in dict.fromkeys(q.source_section_ids) if s in sections_by_topic[q.topic_key]]
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
                        source_section_ids=cited,
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
                        source_section_ids=cited,
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
                sources=prompts.render_context(request.sections, [])
                if request.sections
                else "(not provided)",
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

    # --- long form: answers and notes ------------------------------------------------------------

    def answer(self, request: api.AnswerRequest) -> api.AnswerResponse:
        history = "\n".join(
            f"{'Learner' if t.role == 'user' else 'Tutor'}: {t.text}" for t in request.history[-10:]
        )
        result = self._llm.generate(
            system=prompts.system(request.language),
            prompt=prompts.answer_prompt(
                material_title=request.material_title,
                question=request.question,
                history=history,
                context=prompts.render_context(request.sections, request.figures),
                style=request.style,
            ),
            schema=LongAnswer,
            temperature=0.3,
        )
        used: list[str] = []
        sections_ids = {s.id for s in request.sections}
        text = clean_markdown(result.answer, {f.id for f in request.figures}, sections_ids, used)
        return api.AnswerResponse(
            answer=text,
            grounded=result.grounded,
            cited_section_ids=cited_ids(text, sections_ids),
            figure_ids=used,
            model=self._llm.model_name,
        )

    def notes(self, request: api.NotesRequest) -> api.NotesResponse:
        result = self._llm.generate(
            system=prompts.system(request.language),
            prompt=prompts.notes_prompt(
                material_title=request.material_title,
                kind=request.kind,
                title=request.title,
                path=request.path,
                scope=request.scope,
                context=prompts.render_context(request.sections, request.figures),
            ),
            schema=Notes,
            temperature=0.3,
        )
        used: list[str] = []
        sections_ids = {s.id for s in request.sections}
        text = clean_markdown(result.markdown, {f.id for f in request.figures}, sections_ids, used)
        if not text.strip():
            raise EngineError("The notes came back empty. Please retry.")
        return api.NotesResponse(
            markdown=text,
            figure_ids=used,
            cited_section_ids=cited_ids(text, sections_ids),
            model=self._llm.model_name,
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


def clean_markdown(text: str, allowed_figures: set[str], section_ids: set[str], used: list[str]) -> str:
    """Normalises figure embeds to ![caption](figure:<id>) and removes anything else.

    Models sometimes drop the "figure:" prefix, add slashes, or invent URLs; every image is untrusted:
    known figure ids (in any of those forms) are kept once, all other images (including external URLs)
    are removed. Citations to unknown sections are removed too.
    """

    def image(m: re.Match[str]) -> str:
        target = m.group(2).strip()
        fid = re.sub(r"^figure:/{0,2}", "", target)
        if fid not in allowed_figures or fid in used:
            return ""
        used.append(fid)
        return f"![{m.group(1).strip()}](figure:{fid})"

    def citation(m: re.Match[str]) -> str:
        return m.group(0) if m.group(1) in section_ids or f"§{m.group(1)}" in section_ids else ""

    out = _IMAGE.sub(image, text)
    out = _CITATION.sub(citation, out)
    return re.sub(r"\n{3,}", "\n\n", out).strip()


def cited_ids(text: str, section_ids: Iterable[str]) -> list[str]:
    ids = set(section_ids)
    found = []
    for m in _CITATION.finditer(text):
        sid = m.group(1) if m.group(1) in ids else f"§{m.group(1)}"
        if sid in ids and sid not in found:
            found.append(sid)
    return found


def _outline_line(n: api.OutlineNodeIn) -> str:
    indent = "  " * max(n.level - 1, 0)
    summary = (
        n.summary if len(n.summary) <= OUTLINE_SUMMARY_CHARS else n.summary[:OUTLINE_SUMMARY_CHARS] + "…"
    )
    terms = f" | terms: {', '.join(n.key_terms[:8])}" if n.key_terms else ""
    pages = f" (p. {n.pages})" if n.pages else ""
    return f"{indent}[{n.key}] {n.label} {n.title}{pages}: {summary}{terms}"


def _clamp_to_verdict(verdict: str, score: float) -> float:
    low, high = {"CORRECT": (0.8, 1.0), "PARTIAL": (0.3, 0.7), "INCORRECT": (0.0, 0.2)}[verdict]
    return round(min(max(score, low), high), 3)
