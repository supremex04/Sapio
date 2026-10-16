"""The one helper scene code may import besides Manim: ``from sapio_narration import narrate``.

    with narrate(self, 1) as seconds:      # beat 1 of the storyboard
        self.play(Write(formula), run_time=seconds * 0.6)

Entering starts that beat's spoken line (prepared by the renderer before the scene runs) and gives its length in
seconds, so the animations can be paced to it; leaving waits for whatever is left of the line, so the next beat
never talks over this one. Each line plays once, in order: a beat number that is wrong or used twice takes the next
line not yet spoken, so a numbering slip cannot silence the video. Without narration (none prepared, speech
unavailable, or every line already spoken) it gives a default length and plays nothing.
"""

from contextlib import contextmanager as _contextmanager

_DEFAULT_SECONDS = 3.0
_lines = None
_spoken = set()


def _load():
    global _lines
    if _lines is None:
        import json
        from pathlib import Path

        manifest = Path("narration.json")  # written by the renderer into the scene's working folder
        _lines = json.loads(manifest.read_text(encoding="utf-8")) if manifest.exists() else []
    return _lines


@_contextmanager
def narrate(scene, beat):
    lines = _load()
    index = int(beat) - 1
    if not 0 <= index < len(lines) or index in _spoken:
        index = next((i for i in range(len(lines)) if i not in _spoken), -1)
    line = lines[index] if index >= 0 else None
    if line:
        _spoken.add(index)
    seconds = float(line["seconds"]) if line else _DEFAULT_SECONDS
    start = scene.renderer.time
    if line:
        scene.add_sound(line["file"])
    yield seconds
    left = start + seconds - scene.renderer.time
    if left > 0.05:
        scene.wait(left)


__all__ = ["narrate"]
