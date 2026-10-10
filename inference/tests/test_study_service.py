import random

import pytest

from tests.fakes import FakeLlm
from ved_inference.api import study_schemas as s
from ved_inference.errors import EngineError
from ved_inference.study.service import StudyService, clean_markdown

SECTION = s.SectionIn(
    id="§1.1",
    path="Chapter 1 › Cell theory",
    pages="1",
    summary="Cells.",
    passages=["All living things are made of cells."],
)
FIGURE = s.FigureRef(
    id="fig-1", section_id="§1.1", caption="Figure 1.1: A plant cell", description="A diagram."
)


def material_request() -> s.MaterialRequest:
    return s.MaterialRequest(
        filename="bio.pdf",
        sample="All living things are made of cells.",
        nodes=[
            s.OutlineNodeIn(
                key="N1", label="Chapter 1", title="The Cell", level=1, summary="Cells", key_terms=["cell"]
            ),
            s.OutlineNodeIn(key="N2", label="1.1", title="Cell theory", level=2, summary="Theory"),
            s.OutlineNodeIn(key="N3", label="Chapter 2", title="Energy", level=1, summary="ATP"),
        ],
    )


def outline(**overrides):
    base = {
        "title": "Biology",
        "summary": "Cells and energy.",
        "language": "EN",
        "kind": "TEXTBOOK",
        "topics": [
            {
                "title": "Cells",
                "summary": "s",
                "key_concepts": ["cell", " "],
                "difficulty": "INTRO",
                "node_keys": ["N2", "N1", "N9"],
            },
            {
                "title": "Ghost",
                "summary": "s",
                "key_concepts": [],
                "difficulty": "CORE",
                "node_keys": ["N42"],
            },
        ],
    }
    return base | overrides


def test_material_topics_reference_valid_nodes_in_document_order():
    llm = FakeLlm(outline())
    result = StudyService(llm).build_material(material_request())
    assert result.language == "en"
    assert [(t.title, t.node_keys, t.key_concepts) for t in result.topics] == [
        ("Cells", ["N1", "N2"], ["cell"])
    ]
    assert "[N2] 1.1 Cell theory" in llm.prompts[0]  # the outline, not raw text, goes to the model


def test_material_without_valid_topics_is_an_engine_error():
    with pytest.raises(EngineError):
        StudyService(FakeLlm(outline(topics=[outline()["topics"][1]]))).build_material(material_request())


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
        material_title="Bio",
        topic=s.TopicIn(title="Cells", summary="s", key_concepts=["x"]),
        sections=[SECTION],
        figures=[FIGURE],
    )


def test_lesson_structure_and_figures_are_validated():
    llm = FakeLlm(
        {
            "warmup": choice(prompt="Guess?"),
            "sections": [
                {
                    "kind": "CONCEPT",
                    "heading": "A",
                    "body": "Cells [§1.1] [§9.9].\n\n![Plant cell](figure:fig-1)\n\n![x](figure:made-up)",
                },
                {"kind": "EXAMPLE", "heading": "B", "body": "again ![dup](figure:fig-1)"},
                {"kind": "SUMMARY", "heading": "C", "body": "c"},
            ],
            "checks": [
                choice(after_section=0),
                choice(after_section=7, prompt="Late?"),
                choice(after_section=1, correct_index=9),
            ],
            "explain_back": {"prompt": "Explain cells", "reference_answer": "Cells are units of life."},
            "key_takeaways": ["t1", ""],
        }
    )
    lesson = StudyService(llm, random.Random(1)).lesson(lesson_request())

    body = lesson.sections[0].body
    assert "[§1.1]" in body and "§9.9" not in body
    assert "figure:fig-1" in body and "made-up" not in body
    assert "figure:" not in lesson.sections[1].body  # each figure at most once
    assert lesson.figure_ids == ["fig-1"]
    assert [(c.after_section, c.type) for c in lesson.checks] == [
        (-1, "MULTIPLE_CHOICE"),
        (0, "MULTIPLE_CHOICE"),
        (1, "MULTIPLE_CHOICE"),
        (2, "SHORT_ANSWER"),
    ]
    assert "FIGURES:" in llm.prompts[0] and 'id: "fig-1"' in llm.prompts[0]


def test_questions_cite_only_their_topics_sections_and_shuffle_options():
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
                        "source_section_ids": ["§1.1", "§5"],
                    }
                ]
            }
        )
        q = (
            StudyService(llm, random.Random(seed))
            .questions(
                s.QuestionsRequest(
                    material_title="Bio",
                    count=1,
                    topics=[
                        s.QuestionTopicIn(
                            key="t", title="T", summary="s", key_concepts=[], sections=[SECTION]
                        )
                    ],
                )
            )
            .questions[0]
        )
        assert q.options[q.correct_option_index] == "right"
        assert q.source_section_ids == ["§1.1"]
        positions.add(q.correct_option_index)
    assert len(positions) > 1


def test_answer_keeps_valid_citations_and_figures():
    llm = FakeLlm({"answer": "Cells are units [§1.1][§7].\n\n![Plant cell](figure:fig-1)", "grounded": True})
    result = StudyService(llm).answer(
        s.AnswerRequest(
            material_title="Bio", question="What is a cell?", sections=[SECTION], figures=[FIGURE]
        )
    )
    assert result.cited_section_ids == ["§1.1"] and result.figure_ids == ["fig-1"]
    assert "[§7]" not in result.answer


def test_notes_overview_scope_and_empty_notes_rejected():
    llm = FakeLlm({"markdown": "## Big ideas\n\nCells [§1.1]."}, {"markdown": "  "})
    service = StudyService(llm)
    notes = service.notes(
        s.NotesRequest(
            material_title="Bio", title="Chapter 1", path="Chapter 1", scope="overview", sections=[SECTION]
        )
    )
    assert notes.cited_section_ids == ["§1.1"]
    assert "OVERVIEW" in llm.prompts[0]
    with pytest.raises(EngineError):
        service.notes(
            s.NotesRequest(material_title="Bio", title="1.1", path="1.1", scope="section", sections=[SECTION])
        )


def test_clean_markdown_strips_unknown_ids():
    used: list[str] = []
    out = clean_markdown("a [§2] b [§1.1]\n\n\n\n![c](figure:zz)", {"fig-1"}, {"§1.1"}, used)
    assert out == "a  b [§1.1]" and used == []


def test_grade_clamps_score_and_skips_llm_for_empty_answers():
    request = s.GradeRequest(
        question=s.QuestionToGrade(prompt="Q", reference_answer="R"), answer="something", sections=[SECTION]
    )
    graded = StudyService(FakeLlm({"verdict": "PARTIAL", "score": 0.95, "feedback": "ok"})).grade(request)
    assert (graded.verdict, graded.score) == ("PARTIAL", 0.7)
    llm = FakeLlm()
    assert (
        StudyService(llm).grade(request.model_copy(update={"answer": "  "})).verdict == "INCORRECT"
        and llm.prompts == []
    )


def test_figure_embeds_are_normalised_and_external_images_removed():
    used: list[str] = []
    text = "a ![A](fig-1) b ![B](figure://fig-2) c ![C](https://evil.example/x.png) d ![D](figure:fig-1)"
    out = clean_markdown(text, {"fig-1", "fig-2"}, set(), used)
    assert out == "a ![A](figure:fig-1) b ![B](figure:fig-2) c  d"
    assert used == ["fig-1", "fig-2"]
