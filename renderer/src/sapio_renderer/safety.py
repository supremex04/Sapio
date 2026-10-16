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
#: Our narration helper, importable only as ``from sapio_narration import narrate`` (its module has file access).
NARRATION_MODULE = "sapio_narration"
NARRATION_NAMES = {"narrate"}

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


#: Programs traced line by line (the code engine): ordinary teaching Python, still without files, processes or
#: Python's internals. They may read input() (fed from the request) and use a few common dunders.
PROGRAM_IMPORTS = {
    "math",
    "random",
    "itertools",
    "functools",
    "collections",
    "heapq",
    "bisect",
    "string",
    "re",
    "dataclasses",
    "typing",
    "statistics",
    "fractions",
    "decimal",
    "operator",
    "copy",
    "enum",
}
PROGRAM_DUNDERS = {
    "__init__",
    "__str__",
    "__repr__",
    "__len__",
    "__eq__",
    "__ne__",
    "__lt__",
    "__le__",
    "__gt__",
    "__ge__",
    "__hash__",
    "__iter__",
    "__next__",
    "__getitem__",
    "__setitem__",
    "__contains__",
    "__add__",
    "__sub__",
    "__mul__",
    "__bool__",
    "__name__",
}
MAX_PROGRAM_CHARS = 8_000


def check(code: str) -> None:
    """Raises Rejected when the code is not acceptable scene code."""
    tree = _parse(code, MAX_CODE_CHARS, "scene code")
    scenes = []
    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom) and node.module == NARRATION_MODULE and not node.level:
            if {a.name for a in node.names} - NARRATION_NAMES or any(a.asname for a in node.names):
                raise Rejected(
                    f"Import only this from {NARRATION_MODULE}: from {NARRATION_MODULE} import narrate"
                )
            continue
        _check_node(node, ALLOWED_IMPORTS, FORBIDDEN_NAMES, set(), "scene code")
        if isinstance(node, (ast.Global, ast.Nonlocal)):
            raise Rejected("'global' and 'nonlocal' are not allowed in scene code.")
        if isinstance(node, ast.ClassDef) and node.name == SCENE_CLASS:
            scenes.append(node)

    if len(scenes) != 1:
        raise Rejected(f"Define exactly one scene class named {SCENE_CLASS}.")
    bases = {b.id if isinstance(b, ast.Name) else getattr(b, "attr", "") for b in scenes[0].bases}
    if not bases & SCENE_BASES:
        raise Rejected(f"{SCENE_CLASS} must extend one of: {', '.join(sorted(SCENE_BASES))}.")
    if not any(isinstance(n, ast.FunctionDef) and n.name == "construct" for n in scenes[0].body):
        raise Rejected(f"{SCENE_CLASS} needs a construct(self) method.")


def check_program(code: str) -> None:
    """Raises Rejected when a program to trace is not acceptable (or not Python)."""
    tree = _parse(code, MAX_PROGRAM_CHARS, "the program")
    for node in ast.walk(tree):
        _check_node(node, PROGRAM_IMPORTS, FORBIDDEN_NAMES - {"input"}, PROGRAM_DUNDERS, "the program")


def _parse(code: str, limit: int, what: str) -> ast.AST:
    if len(code) > limit:
        raise Rejected(f"{what[0].upper() + what[1:]} is too long (over {limit} characters).")
    try:
        return ast.parse(code)
    except SyntaxError as e:
        raise Rejected(f"Syntax error on line {e.lineno}: {e.msg}") from e


def _check_node(node: ast.AST, imports: set[str], forbidden: set[str], dunders: set[str], what: str) -> None:
    if isinstance(node, ast.Import):
        for alias in node.names:
            _allowed_module(alias.name, imports)
    elif isinstance(node, ast.ImportFrom):
        if node.level:
            raise Rejected("Relative imports are not allowed.")
        _allowed_module(node.module or "", imports)
    elif isinstance(node, ast.Name):
        if node.id in forbidden:
            raise Rejected(f"'{node.id}' is not allowed in {what}.")
        if _dunder(node.id) and node.id not in dunders:
            # e.g. __builtins__["op" + "en"] would reach what the names above keep out
            raise Rejected(f"'{node.id}' is not allowed in {what}.")
    elif isinstance(node, ast.Attribute):
        if _dunder(node.attr) and node.attr not in dunders:
            raise Rejected(f"Access to '{node.attr}' is not allowed in {what}.")
        if node.attr in forbidden:
            raise Rejected(f"'.{node.attr}' is not allowed in {what}.")
    elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and _dunder(node.name):
        if node.name not in dunders:
            raise Rejected(f"Defining '{node.name}' is not allowed in {what}.")
    elif (
        isinstance(node, ast.Constant)
        and isinstance(node.value, str)
        and "__" in node.value
        # programs may compare __name__ with "__main__"
        and not (node.value == "__main__" and "__name__" in dunders)
    ):
        # str.format can reach attributes from inside a string: "{0.__class__}".
        raise Rejected(f"Strings with '__' are not allowed in {what}.")


def _dunder(name: str) -> bool:
    return name.startswith("__") and name.endswith("__")


def _allowed_module(name: str, allowed: set[str]) -> None:
    root = name.split(".")[0]
    if root not in allowed:
        raise Rejected(f"Importing '{name}' is not allowed (only {', '.join(sorted(allowed))}).")
