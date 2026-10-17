"""Prompts for visualizations: a storyboard, then Manim Community 0.19 scene code (and repairs)."""

from sapio_inference.study.learner import LearnerIn, learner_block
from sapio_inference.study.prompts import language_name

STORYBOARD_SYSTEM = """You are a brilliant teacher who explains maths and science with short animations, in the style of
3Blue1Brown. You plan animations that make one idea visibly clear: build it up step by step, show what changes and why.
This is explanation only: the narration explains the idea and what is on screen. It never gives exam or test advice,
"remember for your exam" lines, mnemonics, quiz questions or study tips, and it does not end with a summary of what to
learn; the last beat finishes the explanation. Write captions in {language}. Output strictly the requested JSON."""


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
  about: a sentence about each pass points at the loop that makes the passes.
- circuit: an electric circuit of resistors, capacitors, inductors, DC voltage sources and DC current sources, drawn
  as a schematic and solved exactly: the viewer shows live voltages and currents, current flowing along the wires, and
  for capacitors and inductors how they change from the moment the circuit is switched on. Use it for Ohm's and
  Kirchhoff's laws, dividers, series and parallel circuits, and RC, RL or LC behaviour (not for AC, signals,
  semiconductors, how a part works inside, or circuit boards: those are animations). Give the circuit on a grid:
  nodes have an id, x from 0 to 12, y from 0 to 8 (y up), and ground true on exactly one node, at the bottom;
  parts have an id (R1, C1, L1, V1, I1, and W1, W2 for wires), a kind (resistor, capacitor, inductor,
  voltage_source, current_source, wire), a value in ohms, farads, henries, volts or amperes (none for a wire), and
  the node ids a and b it joins. Every part is a straight horizontal or vertical line between its two nodes; turn
  corners with a node and a wire; no part may cross another, lie on another, or pass over a node; a voltage
  source's a is its + terminal; a current source drives its current from a to b. Example, 5 V charging 1 uF through
  1 kilohm: nodes g (0,0, ground), t (0,3), r (4,3), b (4,0); parts V1 voltage_source 5 a=t b=g, R1 resistor 1000
  a=t b=r, C1 capacitor 0.000001 a=r b=b, W1 wire a=b b=g. Each beat's focus is the ids of the parts it is about
  (R1,C1) or empty. Captions explain what happens and why; they never state calculated currents, voltages or times
  (the viewer shows the exact values), only the part values you chose ("a one kilohm resistor"). There is no switch
  in the drawing: the response starts when the circuit is switched on, so say that, never "the switch closes"."""


def storyboard_prompt(request: str, context: str | None, engine: str = "auto") -> str:
    context_block = (
        f"\n\nWhere it comes from (the learner's material):\n{context.strip()[:4000]}" if context else ""
    )
    choice = (
        "Choose the engine that shows it best."
        if engine == "auto"
        else f"Use the engine {engine} (the learner chose it)."
    )
    return f"""Plan a short visualization (25-60 seconds of narration) that helps a learner understand this:

\"\"\"{request.strip()[:4000]}\"\"\"{context_block}

{ENGINES}

{choice}

Rules:
- 3-6 beats. Each beat says concretely what appears, moves or is pointed at, and gives one caption sentence. Say
  which parts are attached and how they move (a mass fixed to the end of a spring that stretches and compresses as
  the mass oscillates), so the animation is physically right, and show the behaviour the caption talks about.
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
- SUBTITLES ARE AUTOMATIC: narrate() shows each beat's spoken caption at the bottom of the frame. Never draw
  captions, subtitles or narration text yourself (no Text of the caption anywhere), and keep the bottom 1.3 units of
  the frame (y below -2.7) clear of your objects so the subtitle never covers them.

MANIM 0.19 API THAT WORKS
- Text: Text("label", font_size=28). Formulas: MathTex(r"E = mc^2"), multi-part MathTex(r"a^2", "+", r"b^2", "=", r"c^2")
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
  m.scale(0.8), m.arrange(DOWN, buff=0.4) on VGroup, VGroup(a, b). Keep everything inside the frame (x in [-7, 7], y in [-2.7, 4]; the bottom is for subtitles).
- Colours: WHITE, GREY, BLUE, TEAL, GREEN, YELLOW, GOLD, ORANGE, RED, PINK, PURPLE (and _A.._E shades, e.g. BLUE_C).
- Constants: PI, TAU, DEGREES, UP, DOWN, LEFT, RIGHT, UL, UR, DL, DR, ORIGIN.
- 3D (ThreeDScene only): self.set_camera_orientation(phi=70*DEGREES, theta=-45*DEGREES); ThreeDAxes(); Surface(lambda u, v: axes.c2p(u, v, f(u, v)), u_range=[-2, 2], v_range=[-2, 2]).

DO NOT USE (removed or wrong in 0.19): ShowCreation, TextMobject, TexMobject, get_graph (use axes.plot), GraphScene,
self.camera.frame in a plain Scene, config changes, Code(), Paragraph() with odd fonts, external LaTeX packages.

GETTING THE PHYSICS AND THE PICTURE RIGHT
- Show the behaviour, not a still: if the narration says it oscillates, rotates, falls, flows or grows, animate that
  over time for a few cycles with a ValueTracker driving positions (rate_func=linear), not a single shift.
- Connected things stay connected while anything moves. Build every dependent part with always_redraw or an
  updater from the parts it hangs on: a spring from the wall to the mass, a rod from the pivot to the bob, a force
  arrow from the object, a label next to its object. Never move one part of a connected system on its own.
- A spring is a zigzag between its two ends that stretches and compresses, never a straight Line. Use this helper
  (define it inside construct) and redraw it every frame:
      def zigzag(start, end, coils=10, width=0.18):
          start, end = np.array(start), np.array(end)
          d = end - start
          length = max(np.linalg.norm(d), 1e-6)
          u = d / length
          n = np.array([-u[1], u[0], 0])
          points = [start, start + u * 0.15]
          for i in range(1, 2 * coils):
              points.append(start + u * (0.15 + (length - 0.3) * i / (2 * coils)) + n * width * (1 if i % 2 else -1))
          points += [end - u * 0.15, end]
          return VMobject(color=BLUE_C).set_points_as_corners(points)
  Example: a mass oscillating on a spring.
      t = ValueTracker(0)
      x0, A, w = 0.5, 1.2, 2.0
      mass.add_updater(lambda m: m.move_to([x0 + A * np.cos(w * t.get_value()), 0, 0]))
      self.add(wall, mass)  # the mass first: it moves before the spring redraws, so no gap opens
      spring = always_redraw(lambda: zigzag(wall.get_right(), mass.get_left()))
      self.add(spring)
      self.play(t.animate.set_value(2 * TAU / w), run_time=seconds * 0.8, rate_func=linear)
- What is drawn agrees with the maths: the same amplitude, frequency, signs and directions in the motion, the graph
  and the formula (a restoring force points back toward equilibrium; a graph of x(t) uses the motion's own A and w).
  Label what the narration names (m, k, x, axes as t and x) with MathTex.
- Layout: formulas at the top (y above 2.6), the main picture in the middle, nothing below y = -2.7 (subtitles).
  Place each new thing where nothing else is (next_to, arrange, or shift the picture aside); to replace a picture,
  FadeOut the old one first in its own play, then build the new one, so a frame never shows two half-drawn scenes.
- Fewer objects, done right, beats many objects done loosely.

STYLE
- Words on screen are labels and formulas only (MathTex for anything mathematical, never LaTeX inside Text); the
  narration is shown for you.
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
    # The learner's level, field and depth shape how it is explained. Their goal does not: it steers study material
    # (an exam goal asks for "what is typically examined"), and a visualization is explanation only.
    explained_to = learner.model_copy(update={"goal": None}) if learner else None
    return STORYBOARD_SYSTEM.format(language=language_name(language)) + learner_block(explained_to)


CIRCUIT_SYSTEM = """You fix circuit descriptions for a schematic viewer that also solves them exactly. Keep the idea and
the part ids the storyboard refers to; change only what the problems require. Output strictly the requested JSON."""


def circuit_repair_prompt(
    request: str, title: str, beats: list[tuple[str, str, str]], circuit: str, problems: list[str]
) -> str:
    board = "\n".join(
        f"{i}. Shows: {shows}\n   Caption: {says}\n   Focus: {focus}"
        for i, (shows, says, focus) in enumerate(beats, 1)
    )
    listed = "\n".join(f"- {p}" for p in problems)
    return f"""The learner asked: \"\"\"{request.strip()[:2000]}\"\"\"

Visualization: {title}
{board}

This circuit could not be used:
{circuit[:8000]}

The viewer reported:
{listed}

Rules: nodes on a grid (x 0 to 12, y 0 to 8, y up), exactly one ground node; parts resistor, capacitor, inductor,
voltage_source, current_source or wire, each a straight horizontal or vertical line between its nodes a and b, with
corners made by a node and a wire, never crossing, overlapping or passing over a node; values in ohms, farads,
henries, volts or amperes (none for wires); a voltage source's a is its + terminal. Return the whole fixed circuit."""
