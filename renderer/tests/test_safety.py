import pytest

from sapio_renderer import safety

GOOD = """
from manim import *
import numpy as np

class Visualization(Scene):
    def construct(self):
        eq = MathTex(r"e^{i\\pi} + 1 = 0")
        self.play(Write(eq))
        self.wait(1)
"""


def test_a_plain_scene_passes():
    safety.check(GOOD)


@pytest.mark.parametrize(
    "line, reason",
    [
        ("import os", "Importing 'os'"),
        ("from subprocess import run", "Importing 'subprocess'"),
        ("x = open('/etc/passwd')", "'open'"),
        ("x = __import__('os')", "'__import__'"),
        ("x = ().__class__.__mro__", "Access to '__"),
        ("x = '{0.__class__}'.format(1)", "'__'"),
        ("x = eval('1')", "'eval'"),
        ("x = getattr(1, 'real')", "'getattr'"),
        ("import manim.utils.file_ops as f\nx = f.os", "'.os'"),
    ],
)
def test_dangerous_code_is_rejected(line, reason):
    code = GOOD.replace("import numpy as np", "import numpy as np\n" + line)
    with pytest.raises(safety.Rejected, match=reason):
        safety.check(code)


def test_the_scene_class_is_required():
    with pytest.raises(safety.Rejected, match="exactly one scene class named Visualization"):
        safety.check(GOOD.replace("class Visualization", "class Other"))
    with pytest.raises(safety.Rejected, match="must extend"):
        safety.check(GOOD.replace("(Scene)", "(object)"))
    with pytest.raises(safety.Rejected, match="Syntax error"):
        safety.check("class Visualization(Scene)\n  pass")


NARRATED = """
from manim import *
from sapio_narration import narrate

class Visualization(Scene):
    def construct(self):
        with narrate(self, 1) as seconds:
            self.play(Write(MathTex("x")), run_time=seconds)
"""


def test_the_narration_helper_may_be_imported():
    safety.check(NARRATED)


@pytest.mark.parametrize(
    "line",
    [
        "import sapio_narration",
        "from sapio_narration import _load",
        "from sapio_narration import narrate as n",
    ],
)
def test_nothing_else_of_the_narration_module_is_reachable(line):
    with pytest.raises(safety.Rejected):
        safety.check(NARRATED.replace("from sapio_narration import narrate", line))


def test_dunder_names_are_refused_in_scenes_too():
    with pytest.raises(safety.Rejected):
        safety.check(GOOD.replace("self.wait(1)", "x = __builtins__['op' + 'en']"))
