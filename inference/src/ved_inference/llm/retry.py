"""Retry policy shared by all providers: honour server hints, back off exponentially, detect exhausted quotas."""

import logging
import time
from collections.abc import Callable

import httpx

log = logging.getLogger(__name__)

RETRYABLE_STATUS = {429, 500, 502, 503, 504}


class Retryable(Exception):
    """Transient failure; ``delay`` is the server's hint in seconds (Retry-After / RetryInfo)."""

    def __init__(self, message: str, delay: float | None = None) -> None:
        super().__init__(message)
        self.delay = delay


class ModelUnavailable(Exception):
    """This model can't serve now (unknown model, exhausted quota, retries used up): try the next in the route."""


def retry_delay(response: httpx.Response) -> float | None:
    """Retry-After header, or google.rpc.RetryInfo ("retryDelay": "31s") in a JSON error body."""
    header = response.headers.get("retry-after")
    if header:
        try:
            return float(header)
        except ValueError:
            pass
    try:
        for detail in response.json().get("error", {}).get("details", []):
            delay = detail.get("retryDelay")
            if isinstance(delay, str) and delay.endswith("s"):
                return float(delay[:-1])
    except (ValueError, AttributeError):
        pass
    return None


def with_retries[R](call: Callable[[], R], *, label: str, max_retries: int, max_backoff: float) -> R:
    """Runs ``call``; on ``Retryable`` waits (server hint or 2^n s, capped) and retries.

    A hint far beyond the cap (e.g. "retry in 17 h": a daily quota) raises ``ModelUnavailable`` at once,
    so the router moves to the next model instead of waiting pointlessly.
    """
    last: Retryable | None = None
    for attempt in range(max_retries + 1):
        try:
            return call()
        except Retryable as exc:
            last = exc
            if exc.delay is not None and exc.delay > 2 * max_backoff:
                raise ModelUnavailable(f"{label}: quota exhausted (retry in {exc.delay:.0f}s)") from exc
            log.warning("%s attempt %d failed: %s", label, attempt + 1, exc)
            if attempt < max_retries:
                time.sleep(min(exc.delay if exc.delay is not None else 2.0 ** (attempt + 1), max_backoff))
    raise ModelUnavailable(f"{label}: unavailable after {max_retries + 1} attempts ({last})") from last
