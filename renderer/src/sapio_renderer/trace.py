"""Traces a program line by line (the code engine) in a locked-down child process."""

import json
import os
import resource
import subprocess
import sys
import tempfile
import threading
from dataclasses import dataclass
from pathlib import Path

from sapio_renderer import safety
from sapio_renderer.render import RenderFailed

_two_at_a_time = threading.Semaphore(2)


@dataclass(frozen=True)
class Limits:
    timeout_seconds: float = 15.0
    cpu_seconds: int = 8
    memory_bytes: int = 512 * 1024**2


DEFAULT_LIMITS = Limits()


def trace(code: str, stdin: list[str], limits: Limits = DEFAULT_LIMITS) -> dict:
    """``{steps, output, error, truncated}``; raises safety.Rejected or RenderFailed."""
    safety.check_program(code)
    if not _two_at_a_time.acquire(timeout=limits.timeout_seconds):
        raise RenderFailed("timeout", "The tracer is busy; please retry.")
    try:
        with tempfile.TemporaryDirectory(prefix="trace-") as tmp:
            folder = Path(tmp)
            (folder / "program.py").write_text(code, encoding="utf-8")
            (folder / "stdin.json").write_text(json.dumps(stdin[:50]), encoding="utf-8")
            env = {
                "PATH": "/usr/local/bin:/usr/bin:/bin",
                "HOME": tmp,
                "LANG": "C.UTF-8",
                "PROGRAM_IMPORTS": json.dumps(sorted(safety.PROGRAM_IMPORTS)),
            }
            try:
                done = subprocess.run(
                    [sys.executable, "-I", "-m", "sapio_renderer.tracer_child", "program.py", "stdin.json"],
                    check=False,
                    cwd=tmp,
                    env=env,
                    capture_output=True,
                    text=True,
                    timeout=limits.timeout_seconds,
                    preexec_fn=lambda: _limit(limits),
                )
            except subprocess.TimeoutExpired as e:
                raise RenderFailed("timeout", "The program ran too long to trace.") from e
            try:
                return json.loads(done.stdout)
            except json.JSONDecodeError as e:
                tail = (done.stderr or "").strip().splitlines()[-1:] or ["no output"]
                raise RenderFailed("error", f"The program could not be traced ({tail[0][:300]}).") from e
    finally:
        _two_at_a_time.release()


def _limit(limits: Limits) -> None:
    os.setsid()
    resource.setrlimit(resource.RLIMIT_CPU, (limits.cpu_seconds, limits.cpu_seconds))
    resource.setrlimit(resource.RLIMIT_AS, (limits.memory_bytes, limits.memory_bytes))
    resource.setrlimit(resource.RLIMIT_FSIZE, (1024**2, 1024**2))
    resource.setrlimit(resource.RLIMIT_NPROC, (64, 64))
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
