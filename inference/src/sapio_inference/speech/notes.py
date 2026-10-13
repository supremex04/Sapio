"""Transcript → study notes → document tree.

A transcript alone is a poor study source (fillers, repetitions, tangents, no structure). The notes are written by
the LLM, window by window, faithful to what was said, organised by topic with time ranges; they then become the
document's tree, so lessons, tests, recall and search work on them like on any document. The transcript itself is
kept as the document's raw text.

Pages: a recording has none, so "page" means minute of the recording (page 1 = 00:00-00:59). Citations and
coverage ("pp. 3-8") then read as minutes on media documents.
"""

import logging
import math

from pydantic import BaseModel, Field

from sapio_inference.config import LectureNotesConfig, ParsingConfig
from sapio_inference.errors import DocumentError, EngineError
from sapio_inference.llm.base import LlmClient
from sapio_inference.parsing.layout import blocks_from_markdown
from sapio_inference.parsing.models import Block
from sapio_inference.parsing.parser import PageText, ParsedDocument
from sapio_inference.parsing.tree import build_tree
from sapio_inference.speech.base import Transcript, TranscriptSegment
from sapio_inference.study.prompts import language_name

log = logging.getLogger(__name__)

LECTURE_SYSTEM = """You turn a recorded lecture or course video into study notes.
Your SOURCE is an automatic TRANSCRIPT of the recording: every line starts with its time in seconds.
Speech recognition makes mistakes, people repeat themselves, and recordings contain chatter.

Rules:
- Faithful: write only what the speaker teaches. Do not add facts, examples or claims they did not make.
  Fix an obvious recognition error only when the context makes the intended word certain
  (e.g. "B3" in a talk about B-trees is "B-tree").
- Organise by topic: start a new section where the subject changes. Each heading names the topic
  ("Composite indexes and column order"), never "Part 2" or "Introduction to the lecture".
- Each section: one or two sentences on what it covers, then the substance as Markdown:
  bullet points for key ideas, **bold** key terms with their definitions, numbered lists for steps or procedures,
  a table when the speaker compares things, LaTeX for formulas ($...$ or $$...$$), fenced code for code.
  Keep worked examples the speaker gives, with their numbers.
- Drop filler, greetings, microphone checks, repetitions and off-topic chatter. Keep course announcements
  that matter to a student (deadlines, exam hints, assignments) in a short "Course announcements" section.
- When the speaker points at something the transcript cannot show ("as you see on this slide"), say so briefly
  in brackets, e.g. "(diagram on the slide)".
- start_seconds / end_seconds: the transcript times where the section's content begins and ends.
- Use ### sub-headings inside a section only for clearly separate sub-topics. No HTML.
Write the notes in {language}."""


class NoteSection(BaseModel):
    heading: str = Field(description="Descriptive topic heading, no numbering")
    start_seconds: float
    end_seconds: float
    markdown: str = Field(description="The section's notes in Markdown (no top-level heading)")


class LectureNotesDraft(BaseModel):
    sections: list[NoteSection]


class LectureNotes:
    def __init__(self, llm: LlmClient, config: LectureNotesConfig, parsing: ParsingConfig) -> None:
        self._llm = llm
        self._config = config
        self._parsing = parsing

    def build(self, transcript: Transcript, title: str) -> ParsedDocument:
        words = sum(len(s.text.split()) for s in transcript.segments)
        if words < self._config.min_words:
            raise DocumentError("No speech was found in this recording")
        minutes = max(1, math.ceil(transcript.duration / 60))
        windows = _windows(transcript.segments, self._config.window_seconds)

        blocks: list[Block] = []
        headings: list[str] = []
        for window in windows:
            for section in self._notes(window, transcript.language, title, headings):
                page = _minute(section.start_seconds, minutes)
                end_page = max(page, _minute(section.end_seconds, minutes))
                blocks.append(Block(page=page, kind="heading", text=section.heading.strip(), level=1))
                body = blocks_from_markdown(section.markdown, page, end_page)
                # Spread the body over the section's minutes so chunks and citations point at the right time.
                for i, block in enumerate(body):
                    block.page = page + (end_page - page) * i // max(1, len(body))
                blocks.extend(body)
                headings.append(section.heading.strip())
        if not any(b.kind != "heading" and b.text.strip() for b in blocks):
            raise EngineError("The lecture notes came back empty. Please retry.")

        tree = build_tree(blocks, minutes, self._parsing)
        pages = [
            PageText(page=_minute(w[0].start, minutes), method="stt", markdown=transcript_markdown(w))
            for w in windows
        ]
        return ParsedDocument(
            pages=pages,
            tree=tree,
            page_count=minutes,
            engine=f"{transcript.engine}+notes",
            language=transcript.language,
        )

    def _notes(
        self, window: list[TranscriptSegment], language: str, title: str, earlier: list[str]
    ) -> list[NoteSection]:
        start, end = window[0].start, window[-1].end
        lines = "\n".join(f"[{s.start:.0f}] {s.text}" for s in window)
        before = (
            "\n".join(f"- {h}" for h in earlier[-20:]) or "(none: this is the beginning of the recording)"
        )
        prompt = (
            f"Recording: {title}\n"
            f"This part covers {_clock(start)}-{_clock(end)}.\n"
            f"Sections already written for earlier parts (continue from them, do not repeat them):\n{before}\n\n"
            f"TRANSCRIPT:\n{lines}"
        )
        draft = self._llm.generate(
            system=LECTURE_SYSTEM.format(language=language_name(language)),
            prompt=prompt,
            schema=LectureNotesDraft,
            temperature=0.3,
            task="lecture_notes",
        )
        sections = []
        for s in draft.sections:
            if not s.heading.strip() or not s.markdown.strip():
                continue
            lo = min(max(s.start_seconds, start), end)
            hi = min(max(s.end_seconds, lo), end)
            sections.append(s.model_copy(update={"start_seconds": lo, "end_seconds": hi}))
        sections.sort(key=lambda s: s.start_seconds)
        return sections


def _windows(segments: list[TranscriptSegment], seconds: int) -> list[list[TranscriptSegment]]:
    windows: list[list[TranscriptSegment]] = []
    for seg in segments:
        if not windows or seg.start - windows[-1][0].start >= seconds:
            windows.append([])
        windows[-1].append(seg)
    return windows


def _minute(seconds: float, minutes: int) -> int:
    return min(max(1, int(seconds // 60) + 1), minutes)


def _clock(seconds: float) -> str:
    seconds = int(seconds)
    h, rest = divmod(seconds, 3600)
    m, s = divmod(rest, 60)
    return f"{h}:{m:02d}:{s:02d}" if h else f"{m:02d}:{s:02d}"


def transcript_markdown(segments: list[TranscriptSegment], paragraph_seconds: float = 45.0) -> str:
    """The transcript as readable paragraphs, each starting with its time ("**12:30** ...")."""
    paragraphs: list[str] = []
    current: list[str] = []
    started = None
    for seg in segments:
        if started is None:
            started = seg.start
        current.append(seg.text)
        if seg.end - started >= paragraph_seconds:
            paragraphs.append(f"**{_clock(started)}** {' '.join(current)}")
            current, started = [], None
    if current and started is not None:
        paragraphs.append(f"**{_clock(started)}** {' '.join(current)}")
    return "\n\n".join(paragraphs)
