"""Circuits for the circuit engine: checked, then solved exactly.

A circuit comes as data, never code: nodes on a grid (integer x, y; at least one marked ground) and parts between
two nodes (resistor, capacitor, inductor, DC voltage source, DC current source, wire), each a straight horizontal or
vertical segment. It is checked as a drawing (nothing overlapping, crossing or touching without a node) and as a
circuit (connected, grounded, solvable, no short circuit), and every problem is described in words the AI can fix.

Then it is solved. Without capacitors or inductors: the DC operating point. With them: the response from the moment
the sources switch on (capacitors uncharged, inductor currents zero), computed exactly. The circuit reduces to
x' = A x + b with x the capacitor voltages and inductor currents, so x(t) = x_ss + expm(A t) (x0 - x_ss): no time
stepping, no integration error, oscillations included. Every value the viewer shows comes from here, none from the AI.

Conventions: a part's current is measured from its node a to its node b through the part. A voltage source's a is its
+ terminal (V(a) - V(b) = value); a current source drives its value from a to b through itself; a capacitor's voltage
is V(a) - V(b).
"""

from dataclasses import dataclass
from math import isfinite

import numpy as np

KINDS = {"resistor", "capacitor", "inductor", "voltage_source", "current_source", "wire"}
#: Allowed values per kind (SI units): wide enough for teaching circuits, narrow enough to keep the maths well posed.
RANGES = {
    "resistor": (1e-3, 1e8),
    "capacitor": (1e-12, 10.0),
    "inductor": (1e-9, 100.0),
    "voltage_source": (-1000.0, 1000.0),
    "current_source": (-100.0, 100.0),
}
UNITS = {"resistor": "ohm", "capacitor": "F", "inductor": "H", "voltage_source": "V", "current_source": "A"}
MAX_NODES = 30
MAX_PARTS = 30
GRID_W, GRID_H = 12, 8
MAX_CURRENT = 100.0  # more than this through any part is a short circuit, not a teaching circuit
SAMPLES = 241
_NEVER_SETTLES = (
    "The circuit never settles: a capacitor is fed by a current source with no resistor path, or an inductor is "
    "driven by a voltage source with no resistance. Add a resistor."
)


@dataclass
class Invalid(Exception):
    problems: list[str]

    def __str__(self) -> str:
        return " ".join(self.problems)


# ------------------------------------------------------------------------------------------------- checking


def check(circuit: dict) -> tuple[dict[str, tuple[int, int]], set[str], list[dict]]:
    """Positions by node, ground nodes, parts; raises Invalid with every problem found."""
    problems: list[str] = []
    nodes = circuit.get("nodes") or []
    parts = circuit.get("parts") or []
    if not nodes or not parts:
        raise Invalid(["The circuit needs nodes and parts."])
    if len(nodes) > MAX_NODES or len(parts) > MAX_PARTS:
        raise Invalid([f"Keep the circuit to at most {MAX_NODES} nodes and {MAX_PARTS} parts."])

    where: dict[str, tuple[int, int]] = {}
    grounds: set[str] = set()
    taken: dict[tuple[int, int], str] = {}
    for n in nodes:
        nid, x, y = str(n.get("id", "")).strip(), n.get("x"), n.get("y")
        if not nid or nid in where:
            problems.append(f"Node ids must be unique and not empty ({nid!r}).")
            continue
        if not isinstance(x, int) or not isinstance(y, int) or not (0 <= x <= GRID_W and 0 <= y <= GRID_H):
            problems.append(
                f"Node {nid} must sit on the grid: whole numbers, x 0 to {GRID_W}, y 0 to {GRID_H}."
            )
            continue
        if (x, y) in taken:
            problems.append(
                f"Nodes {taken[(x, y)]} and {nid} are both at ({x}, {y}); merge them into one node."
            )
            continue
        where[nid], taken[(x, y)] = (x, y), nid
        if n.get("ground"):
            grounds.add(nid)
    if not grounds:
        problems.append("Mark one node as ground (the reference all voltages are measured from).")

    ids: set[str] = set()
    good: list[dict] = []
    for p in parts:
        pid, kind, a, b = str(p.get("id", "")).strip(), p.get("kind"), p.get("a"), p.get("b")
        if not pid or pid in ids:
            problems.append(f"Part ids must be unique and not empty ({pid!r}).")
            continue
        ids.add(pid)
        if kind not in KINDS:
            problems.append(f"{pid}: kind {kind!r} is not one of {', '.join(sorted(KINDS))}.")
            continue
        if a not in where or b not in where:
            problems.append(f"{pid} connects {a!r} and {b!r}, but both must be listed nodes.")
            continue
        if a == b:
            problems.append(f"{pid} starts and ends at the same node {a}.")
            continue
        (x1, y1), (x2, y2) = where[a], where[b]
        if x1 != x2 and y1 != y2:
            problems.append(
                f"{pid} goes from {a} ({x1}, {y1}) to {b} ({x2}, {y2}): parts must be straight, horizontal or "
                "vertical. Add a corner node and a wire."
            )
            continue
        if kind != "wire":
            value = p.get("value")
            low, high = RANGES[kind]
            if not isinstance(value, (int, float)) or not isfinite(value) or not (low <= value <= high):
                problems.append(
                    f"{pid}: a {kind.replace('_', ' ')} value between {low:g} and {high:g} {UNITS[kind]}."
                )
                continue
            if kind in ("resistor", "capacitor", "inductor") and value <= 0:
                problems.append(f"{pid}: the value must be positive.")
                continue
        good.append({"id": pid, "kind": kind, "value": float(p.get("value") or 0.0), "a": a, "b": b})
    problems += _drawing_problems(good, where)
    if not problems:
        problems += _connection_problems(good, where, grounds)
    if problems:
        raise Invalid(problems)
    return where, grounds, good


def _segment(part, where):
    (x1, y1), (x2, y2) = where[part["a"]], where[part["b"]]
    return (min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))


def _drawing_problems(parts, where) -> list[str]:
    """What would look connected without being so, or overlap: the drawing must say exactly what the circuit is."""
    problems = []
    segments = [(p, _segment(p, where)) for p in parts]
    for p, (x1, y1, x2, y2) in segments:
        for nid, (x, y) in where.items():
            if nid in (p["a"], p["b"]):
                continue
            inside = (y1 == y2 == y and x1 < x < x2) or (x1 == x2 == x and y1 < y < y2)
            if inside:
                problems.append(
                    f"Node {nid} lies in the middle of {p['id']}; end {p['id']} at {nid} and continue from there."
                )
    for i, (p, s) in enumerate(segments):
        for q, t in segments[i + 1 :]:
            horizontal_p, horizontal_q = s[1] == s[3], t[1] == t[3]
            if horizontal_p == horizontal_q:  # parallel: overlapping along the same line?
                same_line = s[1] == t[1] if horizontal_p else s[0] == t[0]
                lo = max(s[0], t[0]) if horizontal_p else max(s[1], t[1])
                hi = min(s[2], t[2]) if horizontal_p else min(s[3], t[3])
                if same_line and hi > lo:
                    problems.append(
                        f"{p['id']} and {q['id']} lie on top of each other; give each its own path."
                    )
            else:  # perpendicular: crossing away from a shared node?
                h, v = (s, t) if horizontal_p else (t, s)
                if v[1] < h[1] < v[3] and h[0] < v[0] < h[2]:
                    problems.append(
                        f"{p['id']} and {q['id']} cross without connecting; route one around the other."
                    )
    return problems


def _connection_problems(parts, where, grounds) -> list[str]:
    neighbours: dict[str, set[str]] = {n: set() for n in where}
    for p in parts:
        neighbours[p["a"]].add(p["b"])
        neighbours[p["b"]].add(p["a"])
    problems = [
        f"Node {n} is not connected to anything; remove it." for n, ns in neighbours.items() if not ns
    ]
    start = next(iter(grounds))
    seen, todo = {start}, [start]
    while todo:
        for nxt in neighbours[todo.pop()]:
            if nxt not in seen:
                seen.add(nxt)
                todo.append(nxt)
    apart = sorted(n for n in where if n not in seen and neighbours[n])
    if apart:
        problems.append(f"Nodes {', '.join(apart)} are not connected to the rest of the circuit.")
    return problems


# -------------------------------------------------------------------------------------------------- solving


class _Nets:
    """Nodes joined by wires are one electrical net; ground nets are the reference (index -1)."""

    def __init__(self, where, grounds, parts):
        parent = {n: n for n in where}

        def find(n):
            while parent[n] != n:
                parent[n] = parent[parent[n]]
                n = parent[n]
            return n

        for p in parts:
            if p["kind"] == "wire":
                parent[find(p["a"])] = find(p["b"])
        for g in grounds:
            parent[find(g)] = find(next(iter(grounds)))
        ground = find(next(iter(grounds)))
        roots = sorted({find(n) for n in where} - {ground})
        self.index = {r: i for i, r in enumerate(roots)}
        self.of = {n: (-1 if find(n) == ground else self.index[find(n)]) for n in where}
        self.count = len(roots)


def solve(circuit: dict) -> dict:
    """Checks and solves; raises Invalid. Returns the values the viewer shows."""
    where, grounds, parts = check(circuit)
    nets = _Nets(where, grounds, parts)
    problems = [
        f"{p['id']} is shorted: a wire joins its two ends, so it does nothing."
        for p in parts
        if p["kind"] != "wire" and nets.of[p["a"]] == nets.of[p["b"]]
    ]
    if problems:
        raise Invalid(problems)

    caps = [p for p in parts if p["kind"] == "capacitor"]
    inds = [p for p in parts if p["kind"] == "inductor"]
    model = _Model(nets, parts, caps, inds)
    n_states = len(caps) + len(inds)

    if n_states == 0:
        snapshot = model.snapshot(np.zeros(0))
        result = {"kind": "dc", "steady": snapshot}
    else:
        a_matrix, b_vector = model.state_space()
        eigen = np.linalg.eigvals(a_matrix)
        if np.any(eigen.real > 1e-9 * max(1.0, np.max(np.abs(eigen)))):
            raise Invalid(["The circuit is unstable; check the source and part values."])
        try:
            x_ss = np.linalg.solve(a_matrix, -b_vector)
        except np.linalg.LinAlgError:
            raise Invalid([_NEVER_SETTLES]) from None
        times = _window(eigen)
        from scipy.linalg import expm

        step = expm(a_matrix * (times[1] - times[0]))
        x, xs = -x_ss.copy(), []  # deviation from steady state; x(0) = 0
        for _ in times:
            xs.append(x_ss + x)
            x = step @ x
        series = [model.snapshot(state) for state in xs]
        result = {
            "kind": "transient",
            "steady": model.snapshot(x_ss),
            "times": [float(t) for t in times],
            "nodes": {n: [s["nodes"][n] for s in series] for n in where},
            "parts": {
                p["id"]: {
                    "current": [s["parts"][p["id"]]["current"] for s in series],
                    "voltage": [s["parts"][p["id"]]["voltage"] for s in series],
                }
                for p in parts
            },
        }

    worst = max(abs(v["current"]) for v in result["steady"]["parts"].values())
    if result["kind"] == "transient":
        worst = max(worst, max(abs(c) for v in result["parts"].values() for c in v["current"]))
    if worst > MAX_CURRENT:
        raise Invalid([f"A part carries {worst:.3g} A: that is a short circuit. Add resistance."])
    return result


#: Samples per cycle of the fastest oscillation, so a ringing response is drawn as it is, never aliased.
SAMPLES_PER_CYCLE = 20


def _window(eigen: np.ndarray) -> np.ndarray:
    """From switch-on to settled (five slowest time constants), or three cycles of an undamped oscillation; shorter
    when needed so the fastest oscillation keeps SAMPLES_PER_CYCLE samples a cycle (the start of a long ringing is
    then shown, exactly, rather than all of it, falsely)."""
    decay = [-e.real for e in eigen if e.real < -1e-12 * max(1.0, abs(e))]
    undamped = [abs(e.imag) for e in eigen if abs(e.imag) > 1e-12 and abs(e.real) <= 1e-9 * abs(e.imag)]
    ringing = [abs(e.imag) for e in eigen if abs(e.imag) > 1e-12]
    end = 0.0
    if decay:
        end = 5.0 / min(decay)
    if undamped:
        end = max(end, 3 * 2 * np.pi / min(undamped))
    if ringing:
        end = min(end, (SAMPLES - 1) / SAMPLES_PER_CYCLE * 2 * np.pi / max(ringing))
    if end <= 0:
        end = 1.0
    return np.linspace(0.0, end, SAMPLES)


class _Model:
    """The resistive network with capacitors as voltage sources and inductors as current sources."""

    def __init__(self, nets, parts, caps, inds):
        self.nets, self.parts, self.caps, self.inds = nets, parts, caps, inds
        self.vtype = [p for p in parts if p["kind"] in ("voltage_source", "capacitor")]
        self.size = nets.count + len(self.vtype)

    def _solve(self, state: np.ndarray, sources: bool) -> tuple[np.ndarray, np.ndarray]:
        n = self.nets.count
        m = np.zeros((self.size, self.size))
        rhs = np.zeros(self.size)
        cap_v = {c["id"]: state[i] for i, c in enumerate(self.caps)}
        ind_i = {ind["id"]: state[len(self.caps) + i] for i, ind in enumerate(self.inds)}

        def at(node):
            return self.nets.of[node]

        def inject(node, current):  # current leaving the node into a part
            k = at(node)
            if k >= 0:
                rhs[k] -= current

        for p in self.parts:
            a, b = at(p["a"]), at(p["b"])
            if p["kind"] == "resistor":
                g = 1.0 / p["value"]
                for i, j, s in ((a, a, g), (b, b, g), (a, b, -g), (b, a, -g)):
                    if i >= 0 and j >= 0:
                        m[i, j] += s
            elif p["kind"] == "current_source" and sources:
                inject(p["a"], p["value"])
                inject(p["b"], -p["value"])
            elif p["kind"] == "inductor":
                inject(p["a"], ind_i[p["id"]])
                inject(p["b"], -ind_i[p["id"]])
        for k, p in enumerate(self.vtype):
            row = n + k
            a, b = at(p["a"]), at(p["b"])
            if a >= 0:
                m[a, row] += 1.0
                m[row, a] += 1.0
            if b >= 0:
                m[b, row] -= 1.0
                m[row, b] -= 1.0
            if p["kind"] == "voltage_source":
                rhs[row] = p["value"] if sources else 0.0
            else:
                rhs[row] = cap_v[p["id"]]
        try:
            if np.linalg.cond(m) > 1e15:
                raise np.linalg.LinAlgError
            z = np.linalg.solve(m, rhs)
        except np.linalg.LinAlgError:
            raise Invalid([_why_singular(self.parts)]) from None
        voltages = np.concatenate([z[:n], [0.0]])  # index -1: ground
        return voltages, z[n:]

    def state_space(self) -> tuple[np.ndarray, np.ndarray]:
        size = len(self.caps) + len(self.inds)
        a_matrix = np.zeros((size, size))
        for k in range(size):
            unit = np.zeros(size)
            unit[k] = 1.0
            a_matrix[:, k] = self._derivative(unit, sources=False)
        return a_matrix, self._derivative(np.zeros(size), sources=True)

    def _derivative(self, state: np.ndarray, sources: bool) -> np.ndarray:
        voltages, branch = self._solve(state, sources)
        out = []
        for c in self.caps:
            out.append(branch[self.vtype.index(c)] / c["value"])
        for ind in self.inds:
            v = voltages[self.nets.of[ind["a"]]] - voltages[self.nets.of[ind["b"]]]
            out.append(v / ind["value"])
        return np.array(out)

    def snapshot(self, state: np.ndarray) -> dict:
        voltages, branch = self._solve(state, sources=True)
        ind_i = {ind["id"]: state[len(self.caps) + i] for i, ind in enumerate(self.inds)}
        node_v = {node: float(voltages[k]) for node, k in self.nets.of.items()}
        parts = {}
        for p in self.parts:
            if p["kind"] == "wire":
                continue
            v = node_v[p["a"]] - node_v[p["b"]]
            if p["kind"] == "resistor":
                i = v / p["value"]
            elif p["kind"] in ("voltage_source", "capacitor"):
                i = float(branch[self.vtype.index(p)])
            elif p["kind"] == "inductor":
                i = float(ind_i[p["id"]])
            else:
                i = p["value"]
            parts[p["id"]] = {"current": float(i), "voltage": float(v)}
        for wid, current in _wire_currents(self.parts, parts, node_v).items():
            parts[wid] = {"current": current, "voltage": 0.0}
        return {"nodes": node_v, "parts": parts}


def _wire_currents(all_parts, part_values, node_v) -> dict[str, float]:
    """Currents in the wires, from current balance at each node: wires form trees within a net; a wire that closes a
    loop of wires carries nothing it can be told apart by, so it is shown carrying none."""
    wires = [p for p in all_parts if p["kind"] == "wire"]
    leaving = {n: 0.0 for n in node_v}  # current each node sends into its non-wire parts
    for p in all_parts:
        if p["kind"] != "wire":
            i = part_values[p["id"]]["current"]
            leaving[p["a"]] += i
            leaving[p["b"]] -= i
    current = {w["id"]: 0.0 for w in wires}
    edges = {w["id"]: (w["a"], w["b"]) for w in wires}
    # Keep a spanning forest (drop wires that close a loop), then peel leaves.
    parent: dict[str, str] = {}

    def find(n):
        parent.setdefault(n, n)
        while parent[n] != n:
            n = parent[n]
        return n

    tree = {}
    for wid, (a, b) in edges.items():
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[ra] = rb
            tree[wid] = (a, b)
    need = dict(leaving)  # current a node must still receive through its wires
    remaining = dict(tree)
    while remaining:
        degree: dict[str, list[str]] = {}
        for wid, (a, b) in remaining.items():
            degree.setdefault(a, []).append(wid)
            degree.setdefault(b, []).append(wid)
        leaf = next((n for n, ws in degree.items() if len(ws) == 1), None)
        if leaf is None:
            break
        wid = degree[leaf][0]
        a, b = remaining.pop(wid)
        other = b if leaf == a else a
        # The leaf node needs need[leaf] arriving through this wire: flow other -> leaf.
        flow = need[leaf]
        current[wid] = flow if leaf == b else -flow  # measured a -> b
        need[other] += flow
        need[leaf] = 0.0
    return {w: float(c) for w, c in current.items()}


def _why_singular(parts) -> str:
    kinds = {p["kind"] for p in parts}
    if "current_source" in kinds:
        return (
            "The circuit cannot be solved: a current source has no complete path, or a part is left floating. Give "
            "every current a closed loop."
        )
    if "voltage_source" in kinds and "capacitor" in kinds:
        return (
            "The circuit cannot be solved: a capacitor sits directly across a voltage source (or forms a loop with "
            "it), so it would charge instantly. Put a resistor in series."
        )
    return (
        "The circuit cannot be solved: two voltage sources form a loop, or part of the circuit has no path to "
        "ground. Check how the parts connect."
    )
