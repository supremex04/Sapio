from collections.abc import Iterable

"""Prompt builders. Pedagogy is grounded in docs/architecture/learning-science.md."""

from sapio_inference.study.learner import LearnerIn, learner_block

LANGUAGE_NAMES = {
    "en": "English",
    "ne": "Nepali",
    "hi": "Hindi",
    "es": "Spanish",
    "fr": "French",
    "de": "German",
}

TUTOR_SYSTEM = """You are Sapio, an expert, warm and rigorous tutor.
The learner uploaded a document; it is your SOURCE.

Grounding rules:
- The SOURCE is your primary source and defines the scope. Prefer its definitions, notation, terminology and examples.
- You may use well-established general knowledge to explain, illustrate or connect ideas that are within the SOURCE's scope
  (essential when the SOURCE only lists concepts, e.g. a syllabus), but never contradict the SOURCE and never drift outside its scope.
- Never invent specific facts attributed to the SOURCE (numbers, dates, names, quotes) that it does not contain.

Teaching style (evidence-based):
- Short paragraphs, one idea at a time (cognitive load). Concrete examples and analogies for abstract ideas.
- Explain *why*, not just *what* (elaboration). Connect new ideas to ones already covered.
- Feedback is specific and encouraging; praise strategy and effort, never innate ability; treat mistakes as part of learning.

Formatting: GitHub-flavoured Markdown in text fields. Math in LaTeX: $inline$ or $$block$$. No HTML.
Write every learner-facing text in {language}."""


def language_name(code: str) -> str:
    return LANGUAGE_NAMES.get(code.lower(), code)


def system(language: str, learner: "LearnerIn | None" = None) -> str:
    return TUTOR_SYSTEM.format(language=language_name(language)) + learner_block(learner)


def format_sources(sources: Iterable[tuple[int, str, str]]) -> str:
    """sources: (position, heading, content)"""
    return "\n\n".join(f"[C{pos}] {heading}\n{content}".strip() for pos, heading, content in sources)


OUTLINE_SYSTEM = """You are an expert instructional designer. You design a learning path through a student's document using
its outline (sections with summaries). You never invent content that is not in the document. Output strictly the requested JSON."""


def outline_prompt(filename: str, outline: str, sample: str, min_topics: int, max_topics: int) -> str:
    return f"""Design a learning path for this document (file name: "{filename}").

You see its OUTLINE: every section has a key [N#], its numbering, title, pages, a summary and key terms.

Produce:
- title: a concise title for the material.
- summary: 2-4 sentences: what the material covers and who it is for.
- language: ISO 639-1 code of the document's language (see the sample). Write all text in that language.
- kind: SYLLABUS, TEXTBOOK, LECTURE_NOTES, HANDOUT or OTHER.
- topics: {min_topics}-{max_topics} topics in learning order (usually document order). Each topic is a coherent 5-10 minute
  lesson: a short title, a 1-2 sentence summary, 2-6 key concepts, a difficulty (INTRO, CORE, ADVANCED) and node_keys:
  the [N#] keys of the sections it covers (prefer whole subtrees; a large chapter may be split into several topics,
  small neighbouring sections may be merged). Skip front/back matter (contents, references, index).

SAMPLE TEXT:
{sample[:1500]}

OUTLINE:
{outline}"""


CONTEXT_RULES = """Use the SECTIONS as your source. Cite them inline as [§id] using their exact id (e.g. [§2.3]) after the
statements they support. FIGURES are images from the student's own material: when one genuinely helps explain the point
you are making, embed it on its own line exactly as ![short caption](figure:ID) using an id from the FIGURES list, and
refer to it in the text ("as the diagram shows…"). Never invent figure ids or section ids; embed each figure at most once."""


def render_context(sections: Iterable, figures: Iterable) -> str:
    """Structured (YAML-like) context: compact, explicit ids, easy for the model to cite."""
    lines = ["SECTIONS:"]
    for s in sections:
        lines.append(f'- id: "{s.id}"')
        if s.path:
            lines.append(f'  path: "{s.path}"')
        if s.pages:
            lines.append(f'  pages: "{s.pages}"')
        if s.summary:
            lines.append(f"  summary: {s.summary}")
        if s.passages:
            lines.append("  passages:")
            lines.extend(f"    - {p}" for p in s.passages)
    figs = list(figures)
    if figs:
        lines.append("FIGURES:")
        for f in figs:
            lines.append(f'- id: "{f.id}"')
            if f.section_id:
                lines.append(f'  section: "{f.section_id}"')
            if f.caption:
                lines.append(f"  caption: {f.caption}")
            if f.description:
                lines.append(f"  description: {f.description}")
    return "\n".join(lines)


def lesson_prompt(
    *,
    material_title: str,
    kind: str,
    topic_title: str,
    topic_summary: str,
    key_concepts: list[str],
    difficulty: str,
    previous_topics: list[str],
    next_topics: list[str],
    context: str,
) -> str:
    syllabus_note = (
        "\nThe SOURCE is a syllabus: it lists what to learn rather than teaching it. Teach the listed concepts at the level "
        "the syllabus implies, using standard, well-established knowledge, and stay within what the unit lists.\n"
        if kind == "SYLLABUS"
        else ""
    )
    return f"""Write an interactive lesson for the topic "{topic_title}" from the material "{material_title}".

Topic summary: {topic_summary}
Key concepts: {", ".join(key_concepts)}
Difficulty: {difficulty}
Already covered earlier in the path: {", ".join(previous_topics) or "nothing yet (this is the first topic)"}
Coming later (do not teach these in depth): {", ".join(next_topics) or "nothing"}
{syllabus_note}
Structure:
1. warmup: ONE pre-question asked BEFORE any teaching (pretesting effect). It should make the learner think about the core idea
   and be answerable by reasoning or an educated guess. MULTIPLE_CHOICE (4 options) or TRUE_FALSE (options: true, false in the
   material's language). Its explanation reveals the idea briefly.
2. sections: 3-6 sections that build progressively. Use kinds CONCEPT (explain an idea), EXAMPLE (a concrete worked example,
   preferably from the source), ANALOGY (relate to everyday experience). The LAST section must be kind SUMMARY.
   Each body 80-250 words of Markdown. Link back to earlier topics where relevant.
3. checks: 2-3 comprehension checks (retrieval practice) placed after the section they test (after_section = that section's
   0-based index, never after the SUMMARY). They test understanding, not trivia. Plausible distractors based on common
   misconceptions. Explanations cite why the answer is right.
4. explain_back: one prompt asking the learner to explain the topic's core idea in their own words (self-explanation),
   plus a 2-4 sentence model answer.
5. key_takeaways: 3-5 one-sentence takeaways.

{CONTEXT_RULES} Put a figure in the section it illustrates (at most one figure per section).

{context}"""


DIFFICULTY_GUIDE = {
    "EASY": "Mostly recall and understanding (Bloom: remember, understand). About 60% MULTIPLE_CHOICE, 30% TRUE_FALSE, "
    "10% SHORT_ANSWER.",
    "MIXED": "A mix of understanding and application (Bloom: understand, apply, some analyse). About 60% MULTIPLE_CHOICE, "
    "20% TRUE_FALSE, 20% SHORT_ANSWER.",
    "HARD": "Mostly application and analysis: scenarios, comparisons, 'what would happen if' (Bloom: apply, analyse, evaluate). "
    "About 50% MULTIPLE_CHOICE, 10% TRUE_FALSE, 40% SHORT_ANSWER.",
}


def questions_prompt(
    *, material_title: str, kind: str, count: int, difficulty: str, topics_block: str, avoid: list[str]
) -> str:
    avoid_block = "\n".join(f"- {p}" for p in avoid[:60]) or "- (none)"
    return f"""Write exactly {count} assessment questions for the material "{material_title}" (kind: {kind}).

Difficulty: {difficulty}. {DIFFICULTY_GUIDE[difficulty]}

Rules:
- Spread questions across the topics below in proportion to their content; every topic gets at least one question if {count} allows.
- Every question must be answerable from its topic's SECTIONS (for a syllabus: from standard knowledge of the listed concepts).
  Put the supporting section ids in source_section_ids.
- MULTIPLE_CHOICE: exactly 4 options, one clearly correct, distractors that reflect real misconceptions; no "all/none of the above".
- TRUE_FALSE: options exactly [true, false] in the material's language; avoid trick wording and double negatives.
- SHORT_ANSWER: answerable in 1-3 sentences; reference_answer is a model answer; correct_index = -1; options empty.
- For choice questions reference_answer repeats the correct option text.
- explanation: 1-3 sentences on why the answer is correct, grounded in the source.
- topic_key must be one of the given topic keys.
- Do not repeat or trivially rephrase these previously asked questions:
{avoid_block}

TOPICS AND SOURCES:
{topics_block}"""


def grade_prompt(*, prompt: str, reference: str, explanation: str, sources: str, answer: str) -> str:
    return f"""Grade the learner's answer to a short-answer question.

Question: {prompt}
Model answer: {reference}
Background: {explanation}

Learner's answer:
\"\"\"{answer}\"\"\"

Grading:
- Judge meaning, not wording or spelling. Accept correct answers phrased differently or with extra correct detail.
- CORRECT (score 0.8-1.0): captures the key idea(s). PARTIAL (0.3-0.7): partly right or missing a key element.
  INCORRECT (0.0-0.2): wrong, irrelevant, or empty.
- feedback: 2-4 sentences addressed to the learner: first what they got right (if anything), then what is missing or
  wrong and why, grounded in the source. Encouraging, specific, never about ability.

SOURCE:
{sources}"""


def feedback_prompt(*, material_title: str, score: float, topics_block: str, missed_block: str) -> str:
    return f"""A learner finished an assessment on "{material_title}" with an overall score of {round(score * 100)}%.

Per-topic results:
{topics_block}

Questions they missed or partly missed:
{missed_block or "(none)"}

Write the narrative part of their report:
- summary: 2-4 sentences. Start with what went well, then the main opportunity. Growth-mindset tone: progress is made through
  practice and strategy.
- strengths: 1-4 specific things they demonstrably know (from topics with high scores). Empty if none.
- weak_areas: for each weak topic (low score), topic_title exactly as given and 1-2 sentences of concrete advice on what to
  revisit and how (e.g. re-do the lesson, focus on a specific concept, explain it in their own words).
- next_steps: 2-4 short, actionable next steps (e.g. review a topic, spaced review, retake in a few days)."""


def answer_prompt(
    *, material_title: str, question: str, history: str, context: str, style: str, focus: str = ""
) -> str:
    if style == "tutor":
        shape = "Answer conversationally and concisely (usually under 200 words); give an example or analogy if it helps."
    else:
        shape = (
            "Write a thorough, well-structured answer in Markdown (short intro, headed parts or steps as needed, "
            "a brief recap). Explain why, not just what. Length should match the question: complete, never padded."
        )
    on_screen = (
        f"""
ON THE LEARNER'S SCREEN (their message refers to this unless it clearly says otherwise; short messages such as
"why?" or "I don't get it" are about it):
{focus}
If it is a question they have not answered yet, guide them with hints and questions; do not reveal the answer.
"""
        if focus.strip()
        else ""
    )
    return f"""A student studying "{material_title}" asks a question.
{on_screen}
Conversation so far:
{history or "(none)"}

Question: {question}

{shape}
{CONTEXT_RULES}
If the SECTIONS do not cover the question, say so briefly, set grounded=false, and (if it is within the material's scope)
give a short general explanation clearly labelled as going beyond the document. If the question is unrelated to studying,
gently steer back.

{context}"""


def notes_prompt(
    *, material_title: str, kind: str, title: str, path: str, scope: str, context: str, style: str = "expand"
) -> str:
    if style == "revision":
        if scope == "overview":
            task = (
                f'Write REVISION notes for "{path}" from its subsections (SECTIONS): the big ideas and how they connect, '
                "as a compact recap a student reads the night before an exam."
            )
        else:
            task = (
                f'Write REVISION notes for "{path}" from its SECTIONS: only what must be remembered (key points, '
                "definitions, formulas, steps, common pitfalls), as short as possible without losing anything essential."
            )
        shape = """Format (Markdown): no introduction. Short headed groups (###) of terse bullet points (one fact each, no
filler words), **bold** key terms, tables for comparisons, LaTeX for formulas. End with "Quick self-test": 3-5 short
questions the student should be able to answer from memory (answers hidden, not given). Aim for under a third of the
source's length. Include a figure only if it is essential to remember (e.g. the one diagram everything hangs on)."""
    else:
        if scope == "overview":
            task = (
                f'Explain "{path}" in depth as an OVERVIEW. The SECTIONS are its subsections (with summaries): explain '
                "how the parts fit together, why they matter, and the big ideas and their connections, in curriculum "
                "order. Do not repeat every detail: the student can expand each subsection on its own."
            )
        else:
            task = (
                f'Explain "{path}" in depth, as a patient tutor would, from its SECTIONS. Cover every important idea in '
                "the document's order and make it easier to understand than the original: break complex ideas into "
                "small steps, explain why and how (not just what), define jargon in plain words, and add a concrete "
                "example or everyday analogy for each abstract idea."
            )
        shape = """Format (Markdown): start with a 1-2 sentence "In short" line, then headed parts (##), short paragraphs and
bullet points, **bold** key terms on first use, numbered steps for processes, tables for comparisons, LaTeX for formulas.
Mark examples and analogies clearly (e.g. "Example:", "Think of it like…"). End with "Key terms" (term: one-line
meaning)."""
    syllabus = (
        " The material is a syllabus: explain each listed concept at the level it implies."
        if kind == "SYLLABUS"
        else ""
    )
    return f"""{task}{syllabus}

{shape}
{CONTEXT_RULES} Place figures right after the paragraph they illustrate.

{context}"""
