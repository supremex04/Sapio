import random

import pytest

from tests.fakes import FakeLlm
from ved_inference.api import study_schemas as s
from ved_inference.errors import DocumentError, EngineError
from ved_inference.study.service import StudyService

TEXT = ("Demand falls as price rises. " * 40 + "\n\n") * 2


def material_request() -> s.MaterialRequest:
    return s.MaterialRequest(filename="econ.pdf", pages=[s.PageTextIn(page_from=1, page_to=1, text=TEXT)])


def outline(**overrides):
    base = {
        "title": "Econ",
        "summary": "About demand.",
        "language": "EN",
        "kind": "LECTURE_NOTES",
        "topics": [
            {
                "title": "Demand",
                "summary": "s",
                "key_concepts": ["law of demand", " "],
                "difficulty": "INTRO",
                "chunk_positions": [1, 99],
            },
            {
                "title": "Ghost",
                "summary": "s",
                "key_concepts": [],
                "difficulty": "CORE",
                "chunk_positions": [42],
            },
        ],
    }
    return base | overrides


def test_material_validates_chunk_references_and_drops_unanchored_topics():
    result = StudyService(FakeLlm(outline())).build_material(material_request())
    assert result.language == "en"
    assert [(t.position, t.title, t.chunk_positions, t.key_concepts) for t in result.topics] == [
        (1, "Demand", [1], ["law of demand"])
    ]
    assert result.chunks[0].position == 1


def test_material_with_no_valid_topics_is_an_engine_error():
    llm = FakeLlm(outline(topics=[outline()["topics"][1]]))
    with pytest.raises(EngineError):
        StudyService(llm).build_material(material_request())


def test_nearly_empty_document_is_rejected_before_calling_the_llm():
    llm = FakeLlm()
    with pytest.raises(DocumentError):
        StudyService(llm).build_material(
            s.MaterialRequest(filename="x.pdf", pages=[s.PageTextIn(page_from=1, page_to=1, text="hi")])
        )
    assert llm.prompts == []


def choice(**kw):
    return {
        "type": "MULTIPLE_CHOICE",
        "prompt": "Q?",
        "options": ["right", "w1", "w2", "w3"],
        "correct_index": 0,
        "explanation": "because",
    } | kw


def lesson_request() -> s.LessonRequest:
    return s.LessonRequest(
        material_title="Econ",
        topic=s.TopicIn(title="Demand", summary="s", key_concepts=["x"]),
        sources=[s.SourceChunk(position=1, content="Demand falls as price rises.")],
    )


def test_lesson_places_warmup_first_explain_back_last_and_never_checks_after_summary():
    llm = FakeLlm(
        {
            "warmup": choice(prompt="Guess?"),
            "sections": [
                {"kind": "CONCEPT", "heading": "A", "body": "a"},
                {"kind": "EXAMPLE", "heading": "B", "body": "b"},
                {"kind": "SUMMARY", "heading": "C", "body": "c"},
            ],
            "checks": [
                choice(after_section=0),
                choice(after_section=7, prompt="Late?"),
                choice(after_section=1, correct_index=9),
            ],  # invalid index -> dropped
            "explain_back": {"prompt": "Explain demand", "reference_answer": "Price up, quantity down."},
            "key_takeaways": ["t1", ""],
        }
    )
    lesson = StudyService(llm, random.Random(1)).lesson(lesson_request())

    assert [(c.after_section, c.type) for c in lesson.checks] == [
        (-1, "MULTIPLE_CHOICE"),
        (0, "MULTIPLE_CHOICE"),
        (1, "MULTIPLE_CHOICE"),
        (2, "SHORT_ANSWER"),
    ]
    late = lesson.checks[2]
    assert late.prompt == "Late?" and late.options[late.correct_option_index] == "right"
    assert lesson.checks[-1].reference_answer == "Price up, quantity down."
    assert lesson.key_takeaways == ["t1"]


def test_multiple_choice_options_are_shuffled_but_answer_tracked():
    positions = set()
    for seed in range(20):
        llm = FakeLlm(
            {
                "questions": [
                    {
                        "topic_key": "t",
                        "type": "MULTIPLE_CHOICE",
                        "prompt": "Q",
                        "options": ["right", "a", "b", "c"],
                        "correct_index": 0,
                        "reference_answer": "right",
                        "explanation": "e",
                        "source_chunk_positions": [1, 5],
                    }
                ]
            }
        )
        q = (
            StudyService(llm, random.Random(seed))
            .questions(
                s.QuestionsRequest(
                    material_title="Econ",
                    count=1,
                    topics=[
                        s.QuestionTopicIn(
                            key="t",
                            title="T",
                            summary="s",
                            key_concepts=[],
                            sources=[s.SourceChunk(position=1, content="c")],
                        )
                    ],
                )
            )
            .questions[0]
        )
        assert q.options[q.correct_option_index] == "right"
        assert q.source_chunk_positions == [1]  # chunk 5 isn't this topic's source
        positions.add(q.correct_option_index)
    assert len(positions) > 1


def test_questions_for_unknown_topics_are_dropped():
    llm = FakeLlm(
        {
            "questions": [
                {
                    "topic_key": "nope",
                    "type": "TRUE_FALSE",
                    "prompt": "Q",
                    "options": ["True", "False"],
                    "correct_index": 0,
                    "reference_answer": "True",
                    "explanation": "e",
                    "source_chunk_positions": [],
                }
            ]
        }
    )
    with pytest.raises(EngineError):
        StudyService(llm).questions(
            s.QuestionsRequest(
                material_title="m",
                count=1,
                topics=[
                    s.QuestionTopicIn(
                        key="t",
                        title="T",
                        summary="s",
                        key_concepts=[],
                        sources=[s.SourceChunk(position=1, content="c")],
                    )
                ],
            )
        )


def test_grade_clamps_score_into_verdict_band_and_skips_llm_for_empty_answers():
    request = s.GradeRequest(question=s.QuestionToGrade(prompt="Q", reference_answer="R"), answer="something")
    graded = StudyService(FakeLlm({"verdict": "PARTIAL", "score": 0.95, "feedback": "ok"})).grade(request)
    assert (graded.verdict, graded.score) == ("PARTIAL", 0.7)

    llm = FakeLlm()
    empty = StudyService(llm).grade(request.model_copy(update={"answer": "  "}))
    assert empty.verdict == "INCORRECT" and llm.prompts == []


def test_true_false_options_are_capitalised():
    llm = FakeLlm(
        {
            "questions": [
                {
                    "topic_key": "t",
                    "type": "TRUE_FALSE",
                    "prompt": "Q",
                    "options": ["true", "false"],
                    "correct_index": 1,
                    "reference_answer": "false",
                    "explanation": "e",
                    "source_chunk_positions": [],
                }
            ]
        }
    )
    q = (
        StudyService(llm)
        .questions(
            s.QuestionsRequest(
                material_title="m",
                count=1,
                topics=[
                    s.QuestionTopicIn(
                        key="t",
                        title="T",
                        summary="s",
                        key_concepts=[],
                        sources=[s.SourceChunk(position=1, content="c")],
                    )
                ],
            )
        )
        .questions[0]
    )
    assert q.options == ["True", "False"] and q.correct_option_index == 1 and q.reference_answer == "False"
