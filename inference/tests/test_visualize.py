from sapio_inference.api import visualize_schemas as v
from sapio_inference.visualize.service import VisualizeService, strip_fences
from tests.fakes import FakeLlm

BOARD = {
    "feasible": True,
    "reason": "",
    "title": "Why a^2 + b^2 = c^2",
    "kind": "geometry",
    "engine": "animation",
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


ASPIRIN = {
    **BOARD,
    "title": "Aspirin",
    "kind": "chemistry",
    "engine": "molecule",
    "molecule": {"name": "aspirin", "smiles": " CC(=O)Oc1ccccc1C(=O)O "},
    "beats": [{"shows": "The whole molecule", "says": "This is aspirin.", "focus": "C(=O)O"}],
}


def test_the_storyboard_routes_and_carries_the_engine_data_in_the_same_call():
    llm = FakeLlm(ASPIRIN)
    out = VisualizeService(llm).storyboard(v.StoryboardRequest(request="aspirin"))
    assert out.feasible and out.engine == "molecule"
    assert out.molecule.smiles == "CC(=O)Oc1ccccc1C(=O)O" and out.beats[0].focus == "C(=O)O"
    assert out.protein is None and out.program == ""
    assert len(llm.prompts) == 1 and "Choose the engine" in llm.prompts[0]


def test_missing_engine_data_falls_back_to_an_animation_unless_the_learner_chose_the_engine():
    board = {**ASPIRIN, "molecule": None}
    auto = VisualizeService(FakeLlm(board)).storyboard(v.StoryboardRequest(request="aspirin"))
    assert auto.feasible and auto.engine == "animation"

    llm = FakeLlm(board)
    chosen = VisualizeService(llm).storyboard(v.StoryboardRequest(request="aspirin", engine="molecule"))
    assert not chosen.feasible and "molecule" in chosen.reason
    assert "Use the engine molecule" in llm.prompts[0]


def test_code_storyboards_carry_the_program_and_protein_ids_are_normalised():
    code = {**BOARD, "engine": "code", "program": "```python\nx = 1\nprint(x)\n```"}
    out = VisualizeService(FakeLlm(code)).storyboard(v.StoryboardRequest(request="assignment"))
    assert out.engine == "code" and out.program == "x = 1\nprint(x)\n"

    protein = {**BOARD, "engine": "protein", "protein": {"pdb_id": "1hho", "name": "haemoglobin"}}
    out = VisualizeService(FakeLlm(protein)).storyboard(v.StoryboardRequest(request="haemoglobin"))
    assert out.engine == "protein" and out.protein.pdb_id == "1HHO"


def test_scenes_leave_the_subtitles_to_narrate():
    from sapio_inference.visualize import prompts

    code = prompts.code_prompt("Oscillator", [("A spring", "A mass on a spring.")])
    assert "SUBTITLES ARE AUTOMATIC" in code
    assert "to_edge(DOWN); replace the previous caption" not in code


def test_visualizations_explain_and_never_coach_for_exams():
    from sapio_inference.study.learner import LearnerIn
    from sapio_inference.visualize import prompts

    system = prompts.storyboard_system("en", LearnerIn(academic_level="UNDERGRADUATE", goal="EXAM"))
    assert "never gives exam or test advice" in system
    assert "typically examined" not in system  # the exam goal steers study material, not explanations
    assert "university undergraduate" in system  # the level still shapes how it is explained


def test_scenes_are_told_to_keep_attached_parts_attached():
    from sapio_inference.visualize import prompts

    code = prompts.code_prompt("Oscillator", [("A mass on a spring", "It oscillates.")])
    assert "def zigzag(start, end" in code and "always_redraw" in code
    assert "Never move one part of a connected system on its own" in code


RC = {
    "nodes": [
        {"id": "g", "x": 0, "y": 0, "ground": True},
        {"id": "t", "x": 0, "y": 3},
        {"id": "r", "x": 4, "y": 3},
        {"id": "b", "x": 4, "y": 0},
    ],
    "parts": [
        {"id": "V1", "kind": "voltage_source", "value": 5, "a": "t", "b": "g"},
        {"id": "R1", "kind": "resistor", "value": 1000, "a": "t", "b": "r"},
        {"id": "C1", "kind": "capacitor", "value": 1e-6, "a": "r", "b": "b"},
        {"id": "W1", "kind": "wire", "a": "b", "b": "g"},
    ],
}


def test_a_circuit_storyboard_carries_the_circuit_and_captions_without_numbers():
    from sapio_inference.visualize import prompts

    board = {**BOARD, "engine": "circuit", "kind": "electronics", "circuit": RC}
    llm = FakeLlm(board)
    out = VisualizeService(llm).storyboard(v.StoryboardRequest(request="How does an RC circuit charge?"))
    assert out.engine == "circuit" and len(out.circuit.parts) == 4 and out.circuit.nodes[0].ground
    prompt = prompts.storyboard_prompt("RC", None)
    assert "never state calculated currents" in prompt
    # Nothing starts charged, so a circuit without a source shows nothing: the prompt says how to build each kind.
    assert (
        "at least one DC source" in prompt and "for an LC oscillation a source, an inductor and a" in prompt
    )

    missing = VisualizeService(FakeLlm({**board, "circuit": None})).storyboard(
        v.StoryboardRequest(request="RC", engine="circuit")
    )
    assert not missing.feasible and "circuit" in missing.reason


def test_a_refused_circuit_is_repaired_from_the_problems():
    llm = FakeLlm({"circuit": RC})
    request = v.CircuitRepairRequest(
        request="RC charging",
        title="RC",
        beats=[v.BeatIo(shows="the loop", says="The capacitor charges.", focus="C1")],
        circuit=v.CircuitIo.model_validate(RC),
        problems=["R1 and W1 cross without connecting; route one around the other."],
    )
    out = VisualizeService(llm).repair_circuit(request)
    assert out.circuit.parts[2].id == "C1"
    assert "cross without connecting" in llm.prompts[-1] and "Focus: C1" in llm.prompts[-1]
    assert "at least one DC source with a non-zero value" in llm.prompts[-1]
