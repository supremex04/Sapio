"""The one helper scene code may import besides Manim: ``from sapio_narration import narrate``.

    with narrate(self, 1) as seconds:      # beat 1 of the storyboard
        self.play(Write(formula), run_time=seconds * 0.6)

Entering starts that beat's spoken line (prepared by the renderer before the scene runs), shows the same words as
the subtitle at the bottom of the frame, and gives the line's length in seconds, so the animations can be paced to
it; leaving waits for whatever is left of the line and takes the subtitle away, so the next beat never talks over
this one. Subtitles are always exactly what is spoken: scene code does not draw its own.

Each line plays once, in order: a beat number that is wrong or used twice takes the next line not yet spoken, so a
numbering slip cannot silence the video. Without a voice the subtitles still show, for an estimated time; with no
narration prepared at all, it gives a default length and shows nothing.
"""

from contextlib import contextmanager as _contextmanager

_DEFAULT_SECONDS = 3.0
_CHARS_PER_LINE = 64  # a subtitle line at the font size below fits the frame with a margin
_MAX_WIDTH = 12.6  # frame units (the frame is about 14.2 wide)
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


def _wrap(text):
    """Lines of about equal length (not a full line and an orphan word), none over _CHARS_PER_LINE."""
    words = text.split()
    total = len(" ".join(words))
    count = max(1, -(-total // _CHARS_PER_LINE))  # ceiling
    target = total / count
    lines, current = [], ""
    for word in words:
        candidate = f"{current} {word}".strip()
        if current and (
            len(candidate) > _CHARS_PER_LINE or (len(lines) < count - 1 and len(current) >= target)
        ):
            lines.append(current)
            current = word
        else:
            current = candidate
    if current:
        lines.append(current)
    return "\n".join(lines)


def _subtitle(text):
    """The words at the bottom of the frame, on a dark band so they read over anything."""
    from manim import DOWN, BackgroundRectangle, Text, VGroup

    # One Text per line, centred under each other, in the sans-serif the app uses for its own subtitles.
    words = VGroup(*(Text(line, font="DejaVu Sans", font_size=22) for line in _wrap(text).split("\n")))
    words.arrange(DOWN, buff=0.14)
    if words.width > _MAX_WIDTH:
        words.scale_to_fit_width(_MAX_WIDTH)
    words.to_edge(DOWN, buff=0.3)
    return VGroup(BackgroundRectangle(words, fill_opacity=0.6, buff=0.12), words)


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
    subtitle = _subtitle(line["text"]) if line and line.get("text") else None
    if line and line.get("file"):
        scene.add_sound(line["file"])
    if subtitle is not None:
        scene.add_foreground_mobjects(subtitle)  # above whatever the scene draws
    try:
        yield seconds
        left = start + seconds - scene.renderer.time
        if left > 0.05:
            scene.wait(left)
    finally:
        if subtitle is not None:
            scene.remove_foreground_mobjects(subtitle)
            scene.remove(subtitle)


__all__ = ["narrate"]
