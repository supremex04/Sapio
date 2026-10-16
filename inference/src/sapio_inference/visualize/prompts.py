"""Prompts for visualizations: a storyboard, then Manim Community 0.19 scene code (and repairs)."""

from sapio_inference.study.learner import LearnerIn, learner_block
from sapio_inference.study.prompts import language_name

STORYBOARD_SYSTEM = """You are a brilliant teacher who explains maths and science with short animations, in the style of
3Blue1Brown. You plan animations that make one idea visibly clear: build it up step by step, show what changes and why.
Write captions in {language}. Output strictly the requested JSON."""


ENGINES = """How to show it (engine):
- animation: an animated 2D/3D video drawn with code (formulas, graphs, geometry, physics, processes, algorithms as
  pictures). The default.
- molecule: one small molecule shown in 3D (structure, bonds, functional groups, shape). Give its name and SMILES;
  each beat's focus says which atoms light up, or empty for the whole molecule. For a common group use its name:
  group:methyl, group:n-methyl, group:carbonyl, group:hydroxyl, group:carboxylic acid, group:ester, group:ether,
  group:amide, group:amine, group:aldehyde, group:ketone, group:phenol, group:nitro, group:nitrile, group:thiol,
  group:phosphate, group:halogen, group:alkene, group:alkyne, group:benzene ring, group:purine, group:imidazole,
  group:pyrimidine. Otherwise a specific SMARTS of just those atoms (aromatic ring atoms are lowercase: c, n, o).
  Highlight only what the caption talks about, never most of the molecule.
- protein: a protein, nucleic acid or complex from the Protein Data Bank shown in 3D. Give the 4-character PDB ID
  of a well-known entry you are sure of, in the state the narration describes (e.g. 1HHO oxyhaemoglobin, 4HHB
  deoxyhaemoglobin, 1BNA DNA, 1LYZ lysozyme). Each beat's focus is a chain (A), one residue with its name
  (A:HIS87), a residue range (A:58-63), a ligand (ligand:HEM), or empty for the whole structure. Mind which chain
  is which: residue numbers differ between chains (the proximal histidine is alpha His87 but beta His92).
- code: a short Python program run line by line, showing variables change. Use for questions about how code
  behaves (loops, recursion, sorting, data structures). Put the program in program (at most 40 lines, with a small
  example input, printing its result; if the learner gave Python, copy it exactly; if they gave another language,
  write the equivalent Python and say so in the first beat). Each beat's focus is the exact text of the line it
  explains, copied from the program (if arr[j] > arr[j + 1]:), or of the first and last lines of a block joined
  with " | " (for i in range(n): | arr[j], arr[j + 1] = arr[j + 1], arr[j]). Point at the lines the caption is
  about: a sentence about each pass points at the loop that makes the passes."""


def storyboard_prompt(request: str, context: str | None, engine: str = "auto") -> str:
    context_block = (
        f"\n\nWhere it comes from (the learner's material):\n{context.strip()[:4000]}" if context else ""
    )
    choice = (
        "Choose the engine that shows it best."
        if engine == "auto"
        else f"Use the engine {engine} (the learner chose it)."
    )
    return f"""Plan a short visualization (20-45 seconds of narration) that helps a learner understand this:

\"\"\"{request.strip()[:4000]}\"\"\"{context_block}

{ENGINES}

{choice}

Rules:
- 3-6 beats. Each beat says concretely what appears, moves or is pointed at, and gives one caption sentence.
- The caption is read aloud as the narration and shown as a subtitle, so write it the way a teacher would say it:
  plain words, no LaTeX or symbols ("a squared plus b squared equals c squared", "x of t"), at most about 25 words.
- Visual first: show the quantity, the geometry, the structure or the motion, not paragraphs of text.
- For animation: formulas appear as LaTeX; two dimensions unless depth is essential; nothing that needs images,
  photos or real-world footage.
- If the request is not something any engine can show (e.g. an opinion, a list of facts with no structure, a
  request to render something unsafe), set feasible = false and explain in reason; beats can then be empty."""


CODE_SYSTEM = """You write Manim Community Edition 0.19 scenes. Your code renders on the first try: you only use the API
listed below, exactly as written. Output strictly the requested JSON with the complete Python file."""

MANIM_GUIDE = r"""
FILE CONTRACT (the renderer rejects anything else)
- First lines: from manim import *
               from sapio_narration import narrate
  (optionally also: import numpy as np, import math, import random)
- Exactly one class: class Visualization(Scene):  (or MovingCameraScene / ThreeDScene when truly needed)
  with def construct(self): ... All code inside construct (helpers may be nested functions).
- No other imports. No files, images (ImageMobject, SVGMobject), sounds of your own (add_sound), internet, or
  reading/writing anything.
- Never use: open, eval, exec, getattr, setattr, globals, __anything__, os, sys, subprocess.
- Default background (black). The length follows the narration (below).

NARRATION (every beat is spoken; the voice is prepared for you)
- Wrap each beat, in storyboard order, in its own block with its own number, exactly as listed with the beats:
  beat 1 in  with narrate(self, 1) as seconds:  beat 2 in  with narrate(self, 2) as seconds:  and so on.
  seconds is how long that beat's spoken caption lasts. Pace the beat's animations to it, e.g.
  self.play(Create(circle), run_time=seconds * 0.5), and then the next step with the rest. When the block ends it waits
  for the voice to finish, so never add a long self.wait() at the end of a beat, and never let a beat's animations
  run much longer than seconds.
- An optional title before beat 1 (1-2 s) and a short self.wait(1) after the last beat are fine.

MANIM 0.19 API THAT WORKS
- Text: Text("caption", font_size=28). Formulas: MathTex(r"E = mc^2"), multi-part MathTex(r"a^2", "+", r"b^2", "=", r"c^2")
  (LaTeX: only amsmath / amssymb; use \text{...} for words inside formulas). Tex(r"...") for text with inline maths.
- Shapes: Dot, Circle, Square, Rectangle, Triangle, Polygon(*points), Line(a, b), Arrow(a, b, buff=0), DashedLine,
  Arc(radius, start_angle, angle), Sector, Annulus, Brace(mobject, direction), BraceLabel, SurroundingRectangle,
  NumberLine(x_range=[a, b, step], length=...), Vector([x, y]).
- Coordinates: axes = Axes(x_range=[-3, 3, 1], y_range=[-2, 2, 1], x_length=8, y_length=5, axis_config={"include_numbers": True});
  graph = axes.plot(lambda x: x**2, x_range=[-2, 2], color=BLUE); label = axes.get_graph_label(graph, MathTex("x^2"));
  axes.c2p(x, y) (coords -> point); axes.get_area(graph, x_range=[0, 1], opacity=0.4); axes.get_riemann_rectangles(graph, x_range=[0, 2], dx=0.25);
  axes.get_vertical_line(axes.c2p(1, 1)); NumberPlane(); plane.add_coordinates().
  Parametric: ParametricFunction(lambda t: np.array([np.cos(t), np.sin(t), 0]), t_range=[0, TAU]).
- Changing values: k = ValueTracker(1); curve = always_redraw(lambda: axes.plot(lambda x: k.get_value() * x**2, color=YELLOW));
  number = DecimalNumber(1, num_decimal_places=2); number.add_updater(lambda m: m.set_value(k.get_value()));
  self.play(k.animate.set_value(3), run_time=2). Remove updaters with mob.clear_updaters() when done.
- Animations: self.play(Create(m)), Write(text), FadeIn(m, shift=UP), FadeOut(m), Transform(a, b), ReplacementTransform(a, b),
  TransformMatchingTex(eq1, eq2), GrowArrow(arrow), GrowFromCenter(m), Indicate(m), Circumscribe(m), Flash(point),
  m.animate.shift(RIGHT).set_color(RED), Rotate(m, angle=PI/2), MoveAlongPath(dot, path), LaggedStart(*anims, lag_ratio=0.2),
  AnimationGroup(*anims), Succession(*anims). self.wait(1). run_time=... on self.play.
- Layout: m.to_edge(UP), m.to_corner(UL), m.next_to(other, DOWN, buff=0.3), m.move_to(ORIGIN), m.shift(2*LEFT),
  m.scale(0.8), m.arrange(DOWN, buff=0.4) on VGroup, VGroup(a, b). Keep everything inside the frame (x in [-7, 7], y in [-4, 4]).
- Colours: WHITE, GREY, BLUE, TEAL, GREEN, YELLOW, GOLD, ORANGE, RED, PINK, PURPLE (and _A.._E shades, e.g. BLUE_C).
- Constants: PI, TAU, DEGREES, UP, DOWN, LEFT, RIGHT, UL, UR, DL, DR, ORIGIN.
- 3D (ThreeDScene only): self.set_camera_orientation(phi=70*DEGREES, theta=-45*DEGREES); ThreeDAxes(); Surface(lambda u, v: axes.c2p(u, v, f(u, v)), u_range=[-2, 2], v_range=[-2, 2]).

DO NOT USE (removed or wrong in 0.19): ShowCreation, TextMobject, TexMobject, get_graph (use axes.plot), GraphScene,
self.camera.frame in a plain Scene, config changes, Code(), Paragraph() with odd fonts, external LaTeX packages.

STYLE
- Show each beat's caption as a subtitle at the bottom, inside its narrate block: caption = Text(beat_text,
  font_size=26).to_edge(DOWN); replace the previous caption with FadeOut(old), FadeIn(new) at the start of the beat
  (break long lines with "\n" so they fit in one or two lines).
- Title for 1-2 s at the start, then the idea. Use colour to link a symbol in a formula to the shape it describes.
- Clean up (FadeOut) objects you no longer need so the frame never gets crowded.
"""


def code_prompt(title: str, beats: list[tuple[str, str]]) -> str:
    board = "\n".join(
        f"{i}. Shows: {shows}\n   Caption (spoken): {says}\n   Block: with narrate(self, {i}) as seconds:"
        for i, (shows, says) in enumerate(beats, 1)
    )
    return f"""Write the Manim scene for this storyboard.

Title: {title}

Beats:
{board}

{MANIM_GUIDE}"""


def repair_prompt(title: str, beats: list[tuple[str, str]], code: str, error: str) -> str:
    return f"""{code_prompt(title, beats)}

A previous version failed. Fix it so it renders (keep the same storyboard), changing only what is needed.

Previous code:
```python
{code[:12000]}
```

The renderer reported:
```
{error[-3000:]}
```"""


def storyboard_system(language: str, learner: LearnerIn | None = None) -> str:
    return STORYBOARD_SYSTEM.format(language=language_name(language)) + learner_block(learner)
