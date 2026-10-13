from sapio_inference.study import prompts


def test_answer_prompt_puts_the_learners_screen_first():
    prompt = prompts.answer_prompt(
        material_title="Spring",
        question="why?",
        history="",
        context="SECTIONS:",
        style="tutor",
        focus="Check question: How many queries for 100 lazy orders?\nCorrect answer: 101",
    )
    assert "ON THE LEARNER'S SCREEN" in prompt and "101" in prompt
    assert "ON THE LEARNER'S SCREEN" not in prompts.answer_prompt(
        material_title="S", question="q", history="", context="", style="tutor"
    )


def test_notes_prompts_differ_by_style():
    common = {
        "material_title": "M",
        "kind": "OTHER",
        "title": "T",
        "path": "1.2 T",
        "scope": "section",
        "context": "",
    }
    expand = prompts.notes_prompt(**common, style="expand")
    revision = prompts.notes_prompt(**common, style="revision")
    assert "analogy" in expand and "Quick self-test" not in expand
    assert "REVISION" in revision and "Quick self-test" in revision
