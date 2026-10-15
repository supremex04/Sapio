"""Several API keys for one provider: use one until it runs out, then the next.

Keys are used in order and the pool sticks with the current key while it works (quota is spent key by key, and
logs stay readable). A key that hits a rate or quota limit rests, per model (quotas are per model): for the time
the server asks, or an hour for a daily quota. A key the provider rejects (invalid, its project blocked) rests for
an hour, in case it was a temporary problem. Keys never appear in logs, only their position and last characters.
"""

import logging
import threading
import time

log = logging.getLogger(__name__)

REJECTED_REST_SECONDS = 3600.0
DAILY_QUOTA_REST_SECONDS = 3600.0  # the quota resets once a day; look again every hour
DEFAULT_LIMIT_REST_SECONDS = 20.0  # a rate limit without a server hint


def parse_keys(value: str) -> list[str]:
    """Keys from one setting: separated by commas, spaces or new lines; duplicates dropped, order kept."""
    keys: list[str] = []
    for part in value.replace(",", " ").split():
        if part not in keys:
            keys.append(part)
    return keys


class KeyPool:
    def __init__(self, keys: list[str], label: str) -> None:
        if not keys:
            raise ValueError("a key pool needs at least one key")
        self._keys = keys
        self._label = label
        self._lock = threading.Lock()
        self._current = 0
        self._limited: dict[tuple[int, str], float] = {}  # (key, model) -> resting until (monotonic)
        self._rejected: dict[int, float] = {}  # key -> resting until

    def __len__(self) -> int:
        return len(self._keys)

    def describe(self, index: int) -> str:
        return f"key {index + 1}/{len(self._keys)} (...{self._keys[index][-4:]})"

    def pick(self, model: str) -> tuple[int, str] | None:
        """The key to use for ``model`` now: the current one if it is not resting, else the next that is."""
        now = time.monotonic()
        with self._lock:
            for offset in range(len(self._keys)):
                i = (self._current + offset) % len(self._keys)
                if self._rejected.get(i, 0.0) > now or self._limited.get((i, model), 0.0) > now:
                    continue
                if i != self._current:
                    log.warning("%s: switching to %s", self._label, self.describe(i))
                    self._current = i
                return i, self._keys[i]
        return None

    def limited(self, index: int, model: str, seconds: float) -> None:
        """The key hit a rate or quota limit for this model: let it rest."""
        with self._lock:
            self._limited[(index, model)] = time.monotonic() + seconds
        log.warning(
            "%s: %s reached its limit for %s; resting %.0fs",
            self._label,
            self.describe(index),
            model,
            seconds,
        )

    def rejected(self, index: int, reason: str) -> None:
        """The provider refused the key itself (invalid, blocked): set it aside for a while."""
        with self._lock:
            self._rejected[index] = time.monotonic() + REJECTED_REST_SECONDS
        log.error("%s: %s was rejected (%s); not used for an hour", self._label, self.describe(index), reason)

    def all_rejected(self) -> bool:
        now = time.monotonic()
        with self._lock:
            return all(self._rejected.get(i, 0.0) > now for i in range(len(self._keys)))

    def soonest(self, model: str) -> float | None:
        """Every key is resting for ``model``: the wait until the first comes back (which is then allowed again,
        as the caller waits that long before retrying). None when no key can come back (all rejected)."""
        now = time.monotonic()
        with self._lock:
            waiting = [
                (until, i)
                for (i, m), until in self._limited.items()
                if m == model and self._rejected.get(i, 0.0) <= now
            ]
            if not waiting:
                return None
            until, i = min(waiting)
            del self._limited[(i, model)]
            return max(0.0, until - now)


_pools: dict[tuple[str, tuple[str, ...]], KeyPool] = {}
_pools_lock = threading.Lock()


def shared_pool(label: str, keys: list[str]) -> KeyPool:
    """One pool per provider and key list, shared by its LLM and embedding clients (they spend the same quota)."""
    with _pools_lock:
        return _pools.setdefault((label, tuple(keys)), KeyPool(keys, label))
