"""Static checks on scene code before it runs.

The code is written by an AI from a learner's request, so it is untrusted. This check is the first of several
layers (the others: an empty environment, resource limits and a time limit on the render process, and a
container with no secrets); on its own it is not a sandbox. It allows a small, known vocabulary: Manim, math,
numpy, random, and nothing that reaches files, processes, the network or Python's internals.
"""

import ast
from dataclasses import dataclass

#: The class the scene must define; the renderer renders exactly this one.
SCENE_CLASS = "Visualization"
SCENE_BASES = {"Scene", "MovingCameraScene", "ThreeDScene", "ZoomedScene"}

ALLOWED_IMPORTS = {"manim", "math", "numpy", "random", "typing", "itertools", "functools", "colorsys"}

FORBIDDEN_NAMES = {
    "__import__",
    "eval",
    "exec",
    "compile",
    "open",
    "input",
    "breakpoint",
    "help",
    "exit",
    "quit",
    "globals",
    "locals",
    "vars",
    "getattr",
    "setattr",
    "delattr",
    "hasattr",
    "dir",
    "memoryview",
    "os",
    "sys",
    "subprocess",
    "socket",
    "shutil",
    "pathlib",
    "importlib",
    "builtins",
    "ctypes",
    "pickle",
    "marshal",
    "signal",
    "threading",
    "multiprocessing",
    "asyncio",
    "urllib",
    "http",
    "requests",
    "tempfile",
}

MAX_CODE_CHARS = 30_000


@dataclass
class Rejected(Exception):
    """The code may not run; ``reason`` is safe to show (and to give back to the AI to fix)."""

    reason: str

    def __str__(self) -> str:
        return self.reason


def check(code: str) -> None:
    """Raises Rejected when the code is not acceptable scene code."""
    if len(code) > MAX_CODE_CHARS:
        raise Rejected(f"The scene code is too long (over {MAX_CODE_CHARS} characters).")
    try:
        tree = ast.parse(code)
    except SyntaxError as e:
        raise Rejected(f"Syntax error on line {e.lineno}: {e.msg}") from e

    scenes = []
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                _allowed_module(alias.name)
        elif isinstance(node, ast.ImportFrom):
            if node.level:
                raise Rejected("Relative imports are not allowed.")
            _allowed_module(node.module or "")
        elif isinstance(node, ast.Name) and node.id in FORBIDDEN_NAMES:
            raise Rejected(f"'{node.id}' is not allowed in scene code.")
        elif isinstance(node, ast.Attribute):
            if node.attr.startswith("__") and node.attr.endswith("__"):
                raise Rejected(f"Access to '{node.attr}' is not allowed in scene code.")
            if node.attr in FORBIDDEN_NAMES:
                raise Rejected(f"'.{node.attr}' is not allowed in scene code.")
        elif isinstance(node, ast.Constant) and isinstance(node.value, str) and "__" in node.value:
            # str.format can reach attributes from inside a string: "{0.__class__}".
            raise Rejected("Strings with '__' are not allowed in scene code.")
        elif isinstance(node, (ast.Global, ast.Nonlocal)):
            raise Rejected("'global' and 'nonlocal' are not allowed in scene code.")
        elif isinstance(node, ast.ClassDef) and node.name == SCENE_CLASS:
            scenes.append(node)

    if len(scenes) != 1:
        raise Rejected(f"Define exactly one scene class named {SCENE_CLASS}.")
    bases = {b.id if isinstance(b, ast.Name) else getattr(b, "attr", "") for b in scenes[0].bases}
    if not bases & SCENE_BASES:
        raise Rejected(f"{SCENE_CLASS} must extend one of: {', '.join(sorted(SCENE_BASES))}.")
    if not any(isinstance(n, ast.FunctionDef) and n.name == "construct" for n in scenes[0].body):
        raise Rejected(f"{SCENE_CLASS} needs a construct(self) method.")


def _allowed_module(name: str) -> None:
    root = name.split(".")[0]
    if root not in ALLOWED_IMPORTS:
        raise Rejected(f"Importing '{name}' is not allowed (only {', '.join(sorted(ALLOWED_IMPORTS))}).")
