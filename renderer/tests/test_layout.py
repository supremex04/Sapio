import json

import pytest

pytest.importorskip("manim")  # installed in the Docker image

from sapio_renderer import render

BAD = """from manim import *
from sapio_narration import narrate

class Visualization(Scene):
    def construct(self):
        wall = Rectangle(height=2, width=0.2, color=GREY, fill_opacity=1).move_to([-4, 0, 0])
        mass = Square(side_length=1.2, color=RED, fill_opacity=0.8).move_to([-1.5, 0, 0])
        spring = Line(wall.get_right(), mass.get_left(), color=BLUE)
        with narrate(self, 1) as seconds:
            self.add(wall, spring, mass)
        with narrate(self, 2) as seconds:
            axes = Axes(x_range=[0, 4, 1], y_range=[-2, 2, 1], x_length=6, y_length=3).move_to([-1.5, 0, 0])
            curve = axes.plot(lambda x: np.cos(2 * x), x_range=[0, 9], color=GREEN)
            force = MathTex("F = -kx", color=RED).move_to(mass.get_corner(UR))
            self.add(axes, curve, force)
"""

GOOD = """from manim import *
from sapio_narration import narrate

class Visualization(Scene):
    def construct(self):
        wall = Rectangle(height=2, width=0.2, color=GREY, fill_opacity=1).move_to([-4, 1, 0])
        mass = Square(side_length=0.8, color=RED, fill_opacity=0.8).move_to([0, 1, 0])
        with narrate(self, 1) as seconds:
            self.add(wall, mass, MathTex("m").move_to(mass), MathTex("F = -kx").next_to(mass, UP, buff=0.2))
        with narrate(self, 2) as seconds:
            axes = Axes(x_range=[0, 4, 1], y_range=[-1, 1, 1], x_length=6, y_length=2).move_to([0, -1.2, 0])
            curve = axes.plot(lambda x: np.cos(2 * x), x_range=[0, 4], color=GREEN)
            self.add(axes, curve)
"""

LINES = ["First step.", "Second step."]


def test_a_graph_drawn_over_the_system_and_off_the_frame_is_reported():
    problems = render.render(BAD, "preview", LINES).layout_problems
    text = json.dumps(problems)
    assert "outside the frame" in text and "GREEN" in text  # the curve runs past its axes and off the screen
    assert "Axes (new in this step) is drawn over the RED Square" in text
    assert "the label 'F = -kx' partly covers the RED Square" in text


def test_a_tidy_scene_has_no_problems():
    # An "m" written on the mass and a label beside it are fine.
    assert render.render(GOOD, "preview", LINES).layout_problems == []


PASSING = """from manim import *
from sapio_narration import narrate

class Visualization(Scene):
    def construct(self):
        t = ValueTracker(0.01)
        curve = always_redraw(lambda: ParametricFunction(
            lambda u: np.array([2 * np.cos(u), 6 * np.sin(u), 0]), t_range=[0, max(t.get_value() % TAU, 0.01)],
            color=PURPLE))
        with narrate(self, 1) as seconds:
            self.add(curve)
            self.play(t.animate.set_value(TAU), run_time=2, rate_func=linear)
"""


def test_a_problem_that_comes_and_goes_during_a_beat_is_seen():
    # The curve runs off the top and bottom while it is drawn and wraps back to nothing as the beat ends.
    problems = render.render(PASSING, "preview", ["A curve."]).layout_problems
    assert len(problems) == 1 and "PURPLE" in problems[0] and "outside the frame" in problems[0]
