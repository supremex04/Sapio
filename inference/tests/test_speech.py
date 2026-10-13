import io
import math
import wave

import numpy as np
import pytest
from fastapi.testclient import TestClient

from sapio_inference.config import LectureNotesConfig, ParsingConfig
from sapio_inference.errors import DocumentError
from sapio_inference.main import create_app
from sapio_inference.speech.audio import SAMPLE_RATE, decode_windows, probe_duration, quiet_cut
from sapio_inference.speech.base import Transcript, TranscriptSegment
from sapio_inference.speech.notes import LectureNotes, transcript_markdown
from tests.fakes import FakeEmbedder, FakeLlm, FakeSpeech

KEY = {"X-Internal-Api-Key": "test-key"}


def wav(seconds: float, rate: int = 44_100) -> bytes:
    """A stereo 440 Hz tone (any rate / layout: the decoder resamples to 16 kHz mono)."""
    t = np.arange(int(seconds * rate)) / rate
    tone = (0.3 * np.sin(2 * math.pi * 440 * t) * 32767).astype(np.int16)
    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(np.repeat(tone, 2).tobytes())
    return buffer.getvalue()


def test_audio_is_decoded_to_16k_mono_windows():
    source = io.BytesIO(wav(130))
    assert probe_duration(source) == pytest.approx(130, abs=0.1)
    windows = list(decode_windows(source, window_seconds=60))
    assert len(windows) == 3
    assert windows[0].offset == 0
    assert sum(w.samples.size for w in windows) == pytest.approx(130 * SAMPLE_RATE, abs=SAMPLE_RATE // 10)
    assert windows[1].offset == pytest.approx(windows[0].samples.size / SAMPLE_RATE)
    assert windows[0].samples.dtype == np.float32 and abs(windows[0].samples).max() <= 1


def test_windows_end_at_the_quietest_moment():
    samples = np.full(10 * SAMPLE_RATE, 10_000, dtype=np.int16)
    samples[int(8.5 * SAMPLE_RATE) : int(8.7 * SAMPLE_RATE)] = 0  # a pause 1.5 s before the window end
    cut = quiet_cut(samples, 10 * SAMPLE_RATE)
    assert 8.5 * SAMPLE_RATE <= cut <= 8.7 * SAMPLE_RATE


def test_unreadable_files_are_rejected():
    with pytest.raises(DocumentError):
        probe_duration(io.BytesIO(b"not audio at all"))


def lecture(minutes: float) -> Transcript:
    segments = [
        TranscriptSegment(i * 10.0, i * 10.0 + 9.5, f"Sentence {i} about indexes and B-trees in databases.")
        for i in range(int(minutes * 6))
    ]
    return Transcript(language="en", duration=minutes * 60, engine="fake-stt", segments=segments)


def section(heading, start, end, markdown):
    return {"heading": heading, "start_seconds": start, "end_seconds": end, "markdown": markdown}


def test_notes_become_the_tree_and_the_transcript_the_raw_text():
    llm = FakeLlm(
        {
            "sections": [
                section("B-tree indexes", 0, 400, "- **B-tree**: keeps keys sorted\n- Lookups are fast")
            ]
        },
        {
            "sections": [
                section(
                    "Composite indexes",
                    920,
                    1180,
                    "Column order matters.\n\n### Leading column\n\nUsed first.",
                )
            ]
        },
    )
    notes = LectureNotes(llm, LectureNotesConfig(window_seconds=900), ParsingConfig())
    parsed = notes.build(lecture(20), "databases.mp4")

    assert parsed.page_count == 20 and parsed.engine == "fake-stt+notes" and parsed.language == "en"
    assert [n.title for n in parsed.tree.nodes] == ["B-tree indexes", "Composite indexes", "Leading column"]
    composite = parsed.tree.nodes[1]
    assert composite.page_from == 16  # minute of 15:20
    assert "**B-tree**" in parsed.tree.chunks[0].text
    assert [p.method for p in parsed.pages] == ["stt", "stt"]
    assert parsed.pages[0].markdown.startswith("**00:00** Sentence 0")
    assert llm.tasks == ["lecture_notes", "lecture_notes"]
    assert "B-tree indexes" in llm.prompts[1]  # continuity: earlier headings are passed on


def test_a_recording_without_speech_is_rejected():
    notes = LectureNotes(FakeLlm(), LectureNotesConfig(), ParsingConfig())
    silent = Transcript(language="en", duration=300, engine="fake-stt", segments=[])
    with pytest.raises(DocumentError, match="No speech"):
        notes.build(silent, "silence.mp3")


def test_transcript_paragraphs_carry_their_time():
    text = transcript_markdown(lecture(2).segments, paragraph_seconds=45)
    assert text.split("\n\n")[1].startswith("**00:50**")


def client(speech=None, llm=None) -> TestClient:
    return TestClient(
        create_app(
            engine_factory=lambda _s: None,
            llm_factory=lambda _s: llm or FakeLlm(),
            embedder_factory=lambda _s: FakeEmbedder(),
            speech_factory=lambda _s: speech or FakeSpeech(),
        ),
        raise_server_exceptions=False,
    )


def test_transcribe_endpoint_returns_timed_segments():
    speech = FakeSpeech()
    with client(speech) as c:
        response = c.post("/v1/transcribe", headers=KEY, files={"file": ("lecture.wav", wav(5), "audio/wav")})
    assert response.status_code == 200
    body = response.json()
    assert body["durationSeconds"] == pytest.approx(5, abs=0.1) and body["language"] == "en"
    assert body["segments"][1] == {"start": 6.0, "end": 12.0, "text": "A B-tree keeps keys sorted."}
    assert speech.windows == [0.0]


def test_transcribe_rejects_files_without_audio():
    with client() as c:
        response = c.post("/v1/transcribe", headers=KEY, files={"file": ("x.mp3", b"garbage", "audio/mpeg")})
    assert response.status_code == 422 and response.json()["code"] == "INPUT_REJECTED"


def test_notes_endpoint_returns_a_document_tree():
    llm = FakeLlm({"sections": [section("Indexes", 0, 50, "- A B-tree keeps keys sorted.")]})
    transcript = {
        "language": "en",
        "durationSeconds": 60,
        "engine": "fake-stt",
        "segments": [{"start": i * 5.0, "end": i * 5.0 + 4, "text": "index words " * 4} for i in range(12)],
    }
    with client(llm=llm) as c:
        response = c.post(
            "/v1/extract/transcript", headers=KEY, json={"title": "db.mp3", "transcript": transcript}
        )
    assert response.status_code == 200
    body = response.json()
    assert body["tree"]["nodes"][0]["title"] == "Indexes"
    assert body["segments"][0]["method"] == "stt" and body["pageCount"] == 1
