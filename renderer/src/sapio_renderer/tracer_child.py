"""Runs a learner's (or the AI's) Python program under sys.settrace and prints the trace as JSON.

This module runs in the locked-down child process (empty environment, resource limits, timeout), after the program
passed safety.check_program. The program gets its own globals with a reduced set of builtins; input() reads the
lines given with the request. The trace is a list of steps: the line about to run (or a return / exception), the
call stack with each frame's variables (as short reprs) and the output so far.
"""

import builtins
import io
import json
import os
import sys
import types

FILENAME = "<program>"
MAX_STEPS = 400
MAX_FRAMES = 8
MAX_VARS = 24
MAX_REPR = 80
MAX_OUTPUT = 4000

_REMOVED = {
    "open",
    "eval",
    "exec",
    "compile",
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
    "__import__",
}


def main() -> None:
    program, stdin_path = sys.argv[1], sys.argv[2]
    code = open(program, encoding="utf-8").read()  # noqa: SIM115
    lines = json.load(open(stdin_path, encoding="utf-8"))  # noqa: SIM115
    real_stdout = sys.stdout
    output = io.StringIO()
    steps: list[dict] = []

    def finish(error: str | None = None, truncated: bool = False) -> None:
        sys.settrace(None)
        sys.stdout = real_stdout
        real_stdout.write(
            json.dumps(
                {
                    "steps": steps,
                    "output": output.getvalue()[-MAX_OUTPUT:],
                    "error": error,
                    "truncated": truncated,
                }
            )
        )
        real_stdout.flush()
        os._exit(0)  # nothing the program does afterwards (a bare except, a loop) can change the result

    def record(frame, event: str, value=None) -> None:
        step = {
            "line": frame.f_lineno,
            "event": event,
            "stack": _stack(frame),
            "output": output.getvalue()[-MAX_OUTPUT:],
        }
        if event == "return":
            step["value"] = _repr(value)
        if event == "exception":
            step["value"] = f"{value[0].__name__}: {value[1]}"
        steps.append(step)
        if len(steps) >= MAX_STEPS:
            finish(truncated=True)

    def tracer(frame, event, arg):
        if frame.f_code.co_filename != FILENAME:
            return None
        if event in ("line", "return", "exception"):
            record(frame, event, arg)
        return tracer

    allowed_modules = set(json.loads(os.environ.get("PROGRAM_IMPORTS", "[]")))
    real_import = builtins.__import__

    def guarded_import(name, *args, **kwargs):
        if name.split(".")[0] not in allowed_modules:
            raise ImportError(f"Importing '{name}' is not allowed here")
        return real_import(name, *args, **kwargs)

    feed = iter(lines)

    def fake_input(prompt=""):
        output.write(str(prompt))
        try:
            value = next(feed)
        except StopIteration:
            raise EOFError("No more input was given") from None
        output.write(value + "\n")
        return value

    safe_builtins = {k: v for k, v in vars(builtins).items() if k not in _REMOVED}
    safe_builtins["__import__"] = guarded_import
    safe_builtins["input"] = fake_input
    env = {"__name__": "__main__", "__builtins__": safe_builtins}

    compiled = compile(code, FILENAME, "exec")
    sys.stdout = output
    sys.settrace(tracer)
    try:
        exec(compiled, env)  # noqa: S102 - checked program, locked-down process
    except BaseException as e:  # noqa: BLE001 - the program's own error is part of what the learner sees
        line = e.__traceback__.tb_next.tb_lineno if e.__traceback__ and e.__traceback__.tb_next else None
        finish(error=f"{type(e).__name__}: {e}" + (f" (line {line})" if line else ""))
    finish()


def _stack(frame) -> list[dict]:
    frames = []
    while frame is not None and len(frames) < MAX_FRAMES:
        if frame.f_code.co_filename == FILENAME:
            name = "main" if frame.f_code.co_name == "<module>" else frame.f_code.co_name
            frames.append({"name": name, "line": frame.f_lineno, "vars": _vars(frame.f_locals)})
        frame = frame.f_back
    return list(reversed(frames))


def _vars(scope: dict) -> dict[str, str]:
    shown = {}
    for name, value in scope.items():
        if name.startswith("__") or isinstance(value, (types.ModuleType, types.BuiltinFunctionType)):
            continue
        if isinstance(value, types.FunctionType):
            shown[name] = f"function {name}"
        elif isinstance(value, type):
            shown[name] = f"class {name}"
        else:
            shown[name] = _repr(value)
        if len(shown) >= MAX_VARS:
            break
    return shown


def _repr(value) -> str:
    try:
        text = repr(value)
    except Exception:  # noqa: BLE001 - a broken __repr__ in the program
        text = f"<{type(value).__name__}>"
    return text if len(text) <= MAX_REPR else text[: MAX_REPR - 3] + "..."


if __name__ == "__main__":
    main()
