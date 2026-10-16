from sapio_inference.api import visualize_schemas as v
from sapio_inference.visualize.service import VisualizeService, strip_fences
from tests.fakes import FakeLlm

BOARD = {
    "feasible": True,
    "reason": "",
    "title": "Why a^2 + b^2 = c^2",
    "kind": "geometry",
    "beats": [
        {"shows": "A right triangle with sides a, b, c", "says": "Start with a right triangle."},
        {"shows": "Squares on each side, coloured", "says": "Build a square on each side."},
        {"shows": "", "says": "dropped: shows nothing"},
    ],
}


def test_storyboard_keeps_real_beats_and_reports_the_model():
    out = VisualizeService(FakeLlm(BOARD)).storyboard(v.StoryboardRequest(request="Pythagoras"))
    assert out.feasible and out.title.startswith("Why")
    assert [b.says for b in out.beats] == ["Start with a right triangle.", "Build a square on each side."]


def test_an_infeasible_request_says_why():
    board = {
        **BOARD,
        "feasible": False,
        "reason": "That is an opinion, not something to animate.",
        "beats": [],
    }
    out = VisualizeService(FakeLlm(board)).storyboard(v.StoryboardRequest(request="Is maths beautiful?"))
    assert not out.feasible and "opinion" in out.reason


def test_code_and_repairs_use_the_storyboard_and_the_error():
    llm = FakeLlm({"code": "```python\nfrom manim import *\nclass Visualization(Scene):\n    pass\n```"})
    request = v.SceneCodeRequest(
        title="T",
        beats=[v.BeatIo(shows="a dot", says="A dot.")],
        previous_code="old code",
        error="NameError: name 'ShowCreation' is not defined",
    )
    out = VisualizeService(llm).code(request)
    assert out.code.startswith("from manim import *") and "```" not in out.code
    assert "ShowCreation" in llm.prompts[-1] and "old code" in llm.prompts[-1]


def test_fences_are_stripped():
    assert strip_fences("```\nx = 1\n```") == "x = 1\n"
    assert strip_fences("x = 1") == "x = 1\n"


def test_scenes_are_asked_to_narrate_every_beat_and_captions_to_be_speakable():
    from sapio_inference.visualize import prompts

    code = prompts.code_prompt("Pythagoras", [("A triangle", "Start with a right triangle.")])
    assert "from sapio_narration import narrate" in code
    assert "Block: with narrate(self, 1) as seconds:" in code
    assert "read aloud" in prompts.storyboard_prompt("a^2 + b^2 = c^2", None)
