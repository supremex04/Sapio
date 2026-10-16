import json
import wave
from io import BytesIO

import numpy as np
import pytest

import sapio_narration
from sapio_renderer import speech


class FakeRenderer:
    time = 0.0


class FakeScene:
    """Just what narrate() touches: the clock, add_sound and wait."""

    def __init__(self):
        self.renderer = FakeRenderer()
        self.sounds = []
        self.waits = []

    def add_sound(self, path):
        self.sounds.append((path, self.renderer.time))

    def wait(self, seconds):
        self.waits.append(seconds)
        self.renderer.time += seconds

    def play_for(self, seconds):
        self.renderer.time += seconds


@pytest.fixture
def lines(tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    monkeypatch.setattr(sapio_narration, "_lines", None)
    monkeypatch.setattr(sapio_narration, "_spoken", set())
    manifest = [{"file": "beat-1.wav", "seconds": 3.0}, {"file": "beat-2.wav", "seconds": 2.0}]
    (tmp_path / "narration.json").write_text(json.dumps(manifest))
    return manifest


def test_each_beat_starts_its_line_and_waits_for_it_to_finish(lines):
    scene = FakeScene()
    with sapio_narration.narrate(scene, 1) as seconds:
        assert seconds == 3.0
        scene.play_for(1.0)  # animations shorter than the line
    with sapio_narration.narrate(scene, 2):
        scene.play_for(2.5)  # longer than the line: no extra wait
    assert scene.sounds == [("beat-1.wav", 0.0), ("beat-2.wav", 3.0)]
    assert scene.waits == [2.0]


def test_a_wrong_or_repeated_beat_number_takes_the_next_unspoken_line(lines, monkeypatch):
    monkeypatch.setattr(sapio_narration, "_spoken", set())
    scene = FakeScene()
    for _ in range(3):
        with sapio_narration.narrate(scene, 5):
            scene.play_for(0.5)
    assert [path for path, _ in scene.sounds] == [
        "beat-1.wav",
        "beat-2.wav",
    ]  # the third has nothing left to say


def test_without_narration_it_is_silent_with_a_default_length(tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)
    monkeypatch.setattr(sapio_narration, "_lines", None)
    scene = FakeScene()
    with sapio_narration.narrate(scene, 1) as seconds:
        pass
    assert scene.sounds == []
    assert seconds > 0
    assert scene.waits == [seconds]


def test_clips_are_normalised_wav_with_a_pause_after():
    clip = speech._to_wav(np.full(24_000, 0.1, dtype=np.float32), 24_000)
    with wave.open(BytesIO(clip.wav)) as w:
        frames = np.frombuffer(w.readframes(w.getnframes()), dtype="<i2")
        assert w.getframerate() == 24_000
    assert clip.seconds == pytest.approx(1.0 + speech.PAUSE_SECONDS)
    assert frames.max() == pytest.approx(speech.PEAK * 32767, rel=0.01)
    assert frames[-100:].max() == 0
