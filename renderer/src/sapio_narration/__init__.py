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
from contextlib import suppress as _suppress

_DEFAULT_SECONDS = 3.0
_CHARS_PER_LINE = 64  # a subtitle line at the font size below fits the frame with a margin
_MAX_WIDTH = 12.6  # frame units (the frame is about 14.2 wide)
_lines = None
_spoken = set()
_problems = []  # layout problems found while the beats play, written to layout.json for the renderer
_reported = set()

# Layout checks at the end of each beat (the renderer turns them into a repair request, see render.py).
_EDGE = 0.15  # how far past the frame edge counts as outside
_SUBTITLE_TOP = -2.75  # the subtitle band is below this
_LARGE = 1.5  # area (frame units squared) above which an object counts for overlaps
_OVERLAP = 0.3  # share of the smaller object covered
_BACKDROPS = {"NumberPlane", "ComplexPlane", "Axes", "ThreeDAxes", "PolarPlane"}  # meant to be drawn on
_TEXTS = {"MathTex", "Tex", "Text", "MarkupText", "SingleStringMathTex", "DecimalNumber", "Integer"}
_LABEL_CLASH, _LABEL_INSIDE = (
    0.15,
    0.85,
)  # share of a label's area over another object: a clash / wholly inside


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


def _colour_names():
    import manim

    names = {}
    for base in (
        "WHITE",
        "GREY",
        "GRAY",
        "BLUE",
        "TEAL",
        "GREEN",
        "YELLOW",
        "GOLD",
        "ORANGE",
        "RED",
        "PINK",
        "PURPLE",
        "MAROON",
    ):
        for suffix in ("", "_A", "_B", "_C", "_D", "_E"):
            colour = getattr(manim, base + suffix, None)
            if colour is not None:
                names.setdefault(str(manim.ManimColor(colour).to_hex()).upper(), base + suffix)
    return names


def _describe(mob, names):
    try:
        colour = names.get(str(mob.get_color().to_hex()).upper())
    except Exception:  # noqa: BLE001 - some mobjects have no single colour
        colour = None
    return f"the {colour} {type(mob).__name__}" if colour else f"a {type(mob).__name__}"


def _visible(mob):
    """Whether any part of it shows: a formula's colour is on its glyphs, not on the group that holds them."""
    for part in mob.get_family():
        try:
            if max(part.get_stroke_opacity(), part.get_fill_opacity()) > 0.05:
                return True
        except Exception:  # noqa: BLE001 - not a VMobject (an image, say): count it as visible
            return True
    return False


def _box(mob):
    return mob.get_left()[0], mob.get_right()[0], mob.get_bottom()[1], mob.get_top()[1]


def _check_layout(scene, beat, before, skip):
    """What is wrong with the frame at the end of a beat, in words the scene's author can act on."""
    from manim import config

    half_w, half_h = config.frame_width / 2, config.frame_height / 2
    names = _colour_names()
    shown = [
        m for m in scene.mobjects if m not in skip and (m.width > 1e-3 or m.height > 1e-3) and _visible(m)
    ]
    found = []
    for mob in shown:
        left, right, bottom, top = _box(mob)
        what = _describe(mob, names)
        if (
            left < -half_w - _EDGE
            or right > half_w + _EDGE
            or bottom < -half_h - _EDGE
            or top > half_h + _EDGE
        ):
            found.append(
                f"Step {beat}: {what} spans x {left:.1f} to {right:.1f}, y {bottom:.1f} to {top:.1f}: part of it is "
                f"outside the frame (x from {-half_w:.1f} to {half_w:.1f}, y from {-half_h:.1f} to {half_h:.1f})."
            )
        elif bottom < _SUBTITLE_TOP and right - left > 0.6 and type(mob).__name__ not in _BACKDROPS:
            found.append(
                f"Step {beat}: {what} reaches down to y {bottom:.1f}, into the subtitle area (below y "
                f"{_SUBTITLE_TOP})."
            )
    # A large new object (a graph's axes, a second diagram) laid over something already there: the old thing only has
    # to be a real object (not a dot or a thin line), since covering a small labelled part is the same mistake.
    large = [m for m in shown if m.width >= 0.5 and m.height >= 0.5 and m.width * m.height >= _LARGE]
    solid = [m for m in shown if m.width >= 0.3 and m.height >= 0.3 and type(m).__name__ not in _BACKDROPS]
    for new in (m for m in large if id(m) not in before):
        for old in (m for m in solid if id(m) in before):
            a, b = _box(new), _box(old)
            dx = min(a[1], b[1]) - max(a[0], b[0])
            dy = min(a[3], b[3]) - max(a[2], b[2])
            if dx > 0 and dy > 0 and dx * dy > _OVERLAP * old.width * old.height:
                found.append(
                    f"Step {beat}: {_describe(new, names)} (new in this step) is drawn over "
                    f"{_describe(old, names)} already on screen."
                )
    # A label (text or formula) partly over another object collides with it; one wholly inside a shape (an "m" on the
    # mass) is meant to be there.
    for label in (m for m in shown if type(m).__name__ in _TEXTS):
        area = max(label.width * label.height, 1e-6)
        for other in (m for m in shown if m is not label and (m in solid or type(m).__name__ in _TEXTS)):
            a, b = _box(label), _box(other)
            dx = min(a[1], b[1]) - max(a[0], b[0])
            dy = min(a[3], b[3]) - max(a[2], b[2])
            if dx > 0 and dy > 0 and _LABEL_CLASH < dx * dy / area < _LABEL_INSIDE:
                found.append(
                    f"Step {beat}: the label {_text_of(label)} partly covers {_describe(other, names)}; put it "
                    f"beside it (next_to with a buff) or inside it."
                )
    return found


def _text_of(mob):
    text = getattr(mob, "tex_string", None) or getattr(mob, "text", None) or type(mob).__name__
    return repr(" ".join(str(text).split())[:40])


def _record(problems):
    import json
    import re
    from pathlib import Path

    changed = False
    for problem in problems:
        # One report per problem: the same object off-frame at several moments differs only in its numbers.
        key = re.sub(r"-?\d+(\.\d+)?", "#", problem)
        if key not in _reported and len(_problems) < 8:
            _reported.add(key)
            _problems.append(problem)
            changed = True
    if changed:
        Path("layout.json").write_text(json.dumps(_problems), encoding="utf-8")


_SAMPLE_SECONDS = 0.5  # how often the frame is checked while a beat plays


def _probe(scene, beat, before, skip):
    """An invisible object whose updater checks the frame every half second while the beat plays, so a problem
    that comes and goes (a curve drawn out and wrapped back) is seen, not only how the beat ends."""
    from manim import Mobject

    probe = Mobject()
    clock = {"since": 0.0}

    def check(_mob, dt):
        clock["since"] += dt
        if clock["since"] >= _SAMPLE_SECONDS:
            clock["since"] = 0.0
            with _suppress(Exception):
                _record(_check_layout(scene, beat, before, skip | {probe}))

    probe.add_updater(check)
    return probe


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
    before = {id(m) for m in getattr(scene, "mobjects", [])}  # what was on screen when the step began
    beat_number = index + 1 if line else beat
    probe = None
    with _suppress(Exception):
        probe = _probe(scene, beat_number, before, set(getattr(scene, "foreground_mobjects", [])))
        scene.add(probe)
    try:
        yield seconds
        left = start + seconds - scene.renderer.time
        if left > 0.05:
            scene.wait(left)
        # A check that cannot run must never break the video.
        with _suppress(Exception):
            skip = set(getattr(scene, "foreground_mobjects", [])) | ({probe} if probe else set())
            _record(_check_layout(scene, beat_number, before, skip))
    finally:
        if probe is not None:
            with _suppress(Exception):
                probe.clear_updaters()
                scene.remove(probe)
        if subtitle is not None:
            scene.remove_foreground_mobjects(subtitle)
            scene.remove(subtitle)


__all__ = ["narrate"]
