import math

import pytest

from sapio_renderer import circuit


def loop(*parts, extra_nodes=()):
    """A rectangular loop: g (0,0, ground) - t (0,3) - r (4,3) - b (4,0), plus any extra nodes."""
    nodes = [
        {"id": "g", "x": 0, "y": 0, "ground": True},
        {"id": "t", "x": 0, "y": 3},
        {"id": "r", "x": 4, "y": 3},
        {"id": "b", "x": 4, "y": 0},
        *extra_nodes,
    ]
    return {"nodes": nodes, "parts": list(parts)}


def at(result, part, quantity, t):
    """A part's value at time t (nearest sample)."""
    times = result["times"]
    k = min(range(len(times)), key=lambda i: abs(times[i] - t))
    return result["parts"][part][quantity][k]


def test_a_voltage_divider_matches_ohms_law():
    c = loop(
        {"id": "V1", "kind": "voltage_source", "value": 9, "a": "t", "b": "g"},
        {"id": "R1", "kind": "resistor", "value": 1000, "a": "t", "b": "r"},
        {"id": "R2", "kind": "resistor", "value": 2000, "a": "r", "b": "b"},
        {"id": "W1", "kind": "wire", "a": "b", "b": "g"},
    )
    out = circuit.solve(c)
    assert out["kind"] == "dc"
    s = out["steady"]
    assert s["nodes"]["r"] == pytest.approx(6.0)
    assert s["parts"]["R1"]["current"] == pytest.approx(0.003)
    assert s["parts"]["R2"]["voltage"] == pytest.approx(6.0)
    assert s["parts"]["W1"]["current"] == pytest.approx(0.003)  # the return wire carries the loop current
    assert s["parts"]["V1"]["current"] == pytest.approx(-0.003)  # it flows out of the + terminal


def test_parallel_resistors_share_the_current():
    c = {
        "nodes": [
            {"id": "g", "x": 0, "y": 0, "ground": True},
            {"id": "t", "x": 0, "y": 3},
            {"id": "m", "x": 3, "y": 3},
            {"id": "mb", "x": 3, "y": 0},
            {"id": "r", "x": 6, "y": 3},
            {"id": "rb", "x": 6, "y": 0},
        ],
        "parts": [
            {"id": "V1", "kind": "voltage_source", "value": 12, "a": "t", "b": "g"},
            {"id": "W1", "kind": "wire", "a": "t", "b": "m"},
            {"id": "R1", "kind": "resistor", "value": 6, "a": "m", "b": "mb"},
            {"id": "W2", "kind": "wire", "a": "m", "b": "r"},
            {"id": "R2", "kind": "resistor", "value": 6, "a": "r", "b": "rb"},
            {"id": "W3", "kind": "wire", "a": "rb", "b": "mb"},
            {"id": "W4", "kind": "wire", "a": "mb", "b": "g"},
        ],
    }
    s = circuit.solve(c)["steady"]["parts"]
    assert s["R1"]["current"] == pytest.approx(2.0) and s["R2"]["current"] == pytest.approx(2.0)
    assert s["W1"]["current"] == pytest.approx(4.0) and s["W2"]["current"] == pytest.approx(2.0)


def test_a_capacitor_charges_with_time_constant_rc():
    c = loop(
        {"id": "V1", "kind": "voltage_source", "value": 5, "a": "t", "b": "g"},
        {"id": "R1", "kind": "resistor", "value": 1000, "a": "t", "b": "r"},
        {"id": "C1", "kind": "capacitor", "value": 1e-6, "a": "r", "b": "b"},
        {"id": "W1", "kind": "wire", "a": "b", "b": "g"},
    )
    out = circuit.solve(c)
    assert out["kind"] == "transient"
    assert out["times"][-1] == pytest.approx(5e-3)  # five time constants
    assert at(out, "C1", "voltage", 0) == pytest.approx(0.0, abs=1e-9)
    assert at(out, "C1", "voltage", 1e-3) == pytest.approx(5 * (1 - math.exp(-1)), rel=1e-6)
    assert at(out, "R1", "current", 0) == pytest.approx(5e-3)  # all of the source across R at switch-on
    assert out["steady"]["parts"]["C1"]["voltage"] == pytest.approx(5.0)
    assert out["steady"]["parts"]["R1"]["current"] == pytest.approx(0.0, abs=1e-12)


def test_an_inductor_current_rises_with_time_constant_l_over_r():
    c = loop(
        {"id": "V1", "kind": "voltage_source", "value": 10, "a": "t", "b": "g"},
        {"id": "R1", "kind": "resistor", "value": 10, "a": "t", "b": "r"},
        {"id": "L1", "kind": "inductor", "value": 0.01, "a": "r", "b": "b"},
        {"id": "W1", "kind": "wire", "a": "b", "b": "g"},
    )
    out = circuit.solve(c)
    assert at(out, "L1", "current", 1e-3) == pytest.approx(1 - math.exp(-1), rel=1e-6)
    assert at(out, "L1", "voltage", 0) == pytest.approx(10.0)
    assert out["steady"]["parts"]["L1"]["current"] == pytest.approx(1.0)


def test_an_lc_circuit_oscillates_without_losing_energy():
    c = loop(
        {"id": "V1", "kind": "voltage_source", "value": 1, "a": "t", "b": "g"},
        {"id": "L1", "kind": "inductor", "value": 1e-3, "a": "t", "b": "r"},
        {"id": "C1", "kind": "capacitor", "value": 1e-6, "a": "r", "b": "b"},
        {"id": "W1", "kind": "wire", "a": "b", "b": "g"},
    )
    out = circuit.solve(c)
    vc = out["parts"]["C1"]["voltage"]
    period = 2 * math.pi * math.sqrt(1e-3 * 1e-6)
    assert out["times"][-1] == pytest.approx(3 * period, rel=1e-6)
    assert max(vc) == pytest.approx(2.0, abs=0.02) and min(vc) == pytest.approx(0.0, abs=1e-6)
    assert at(out, "C1", "voltage", period / 2) == pytest.approx(2.0, abs=0.02)  # swings to twice the source


def test_long_ringing_is_sampled_finely_enough_to_be_true():
    c = loop(
        {"id": "V1", "kind": "voltage_source", "value": 1, "a": "t", "b": "g"},
        {"id": "L1", "kind": "inductor", "value": 1e-3, "a": "t", "b": "r"},
        {"id": "C1", "kind": "capacitor", "value": 1e-9, "a": "r", "b": "b"},
        {"id": "R1", "kind": "resistor", "value": 0.5, "a": "b", "b": "g"},  # very light damping
    )
    out = circuit.solve(c)
    period = 2 * math.pi * math.sqrt(1e-3 * 1e-9)
    samples_per_cycle = period / (out["times"][1] - out["times"][0])
    assert samples_per_cycle >= circuit.SAMPLES_PER_CYCLE - 0.01


@pytest.mark.parametrize(
    ("change", "says"),
    [
        (
            lambda c: c["parts"].append({"id": "R9", "kind": "resistor", "value": 5, "a": "t", "b": "b"}),
            "straight",
        ),
        (lambda c: c["nodes"].append({"id": "x", "x": 2, "y": 3}), "middle of R1"),
        (lambda c: c["nodes"][0].update(ground=False), "ground"),
        (
            lambda c: c["parts"].append({"id": "W9", "kind": "wire", "a": "t", "b": "r"}),
            "on top of each other",
        ),
        (lambda c: c["parts"][1].update(value=-5), "value between"),
        (lambda c: c["parts"][1].update(kind="diode"), "is not one of"),
    ],
)
def test_broken_drawings_are_refused_in_words_the_ai_can_act_on(change, says):
    c = loop(
        {"id": "V1", "kind": "voltage_source", "value": 9, "a": "t", "b": "g"},
        {"id": "R1", "kind": "resistor", "value": 1000, "a": "t", "b": "r"},
        {"id": "R2", "kind": "resistor", "value": 2000, "a": "r", "b": "b"},
        {"id": "W1", "kind": "wire", "a": "b", "b": "g"},
    )
    change(c)
    with pytest.raises(circuit.Invalid) as e:
        circuit.solve(c)
    assert says in str(e.value)


def test_crossing_parts_are_refused():
    c = loop(
        {"id": "V1", "kind": "voltage_source", "value": 9, "a": "t", "b": "g"},
        {"id": "R1", "kind": "resistor", "value": 1000, "a": "t", "b": "r"},
        {"id": "R2", "kind": "resistor", "value": 2000, "a": "r", "b": "b"},
        {"id": "W1", "kind": "wire", "a": "b", "b": "g"},
        {"id": "R3", "kind": "resistor", "value": 100, "a": "u", "b": "d"},
        {"id": "R4", "kind": "resistor", "value": 100, "a": "le", "b": "ri"},
        extra_nodes=[
            {"id": "u", "x": 8, "y": 4},
            {"id": "d", "x": 8, "y": 0},
            {"id": "le", "x": 6, "y": 2},
            {"id": "ri", "x": 10, "y": 2},
        ],
    )
    with pytest.raises(circuit.Invalid) as e:
        circuit.solve(c)
    assert "cross without connecting" in str(e.value)


def test_shorts_and_impossible_circuits_are_refused():
    shorted = loop(
        {"id": "V1", "kind": "voltage_source", "value": 9, "a": "t", "b": "g"},
        {"id": "R1", "kind": "resistor", "value": 1000, "a": "t", "b": "r"},
        {"id": "W2", "kind": "wire", "a": "r", "b": "b"},
        {"id": "W1", "kind": "wire", "a": "b", "b": "g"},
    )
    shorted["parts"].append({"id": "R2", "kind": "resistor", "value": 5, "a": "b", "b": "x"})
    shorted["parts"].append({"id": "W3", "kind": "wire", "a": "x", "b": "g"})
    shorted["nodes"].append({"id": "x", "x": 4, "y": -1})  # off the grid too
    with pytest.raises(circuit.Invalid):
        circuit.solve(shorted)

    source_short = loop(
        {"id": "V1", "kind": "voltage_source", "value": 9, "a": "t", "b": "g"},
        {"id": "W2", "kind": "wire", "a": "t", "b": "r"},
        {"id": "W3", "kind": "wire", "a": "r", "b": "b"},
        {"id": "W1", "kind": "wire", "a": "b", "b": "g"},
    )
    with pytest.raises(circuit.Invalid) as e:
        circuit.solve(source_short)
    assert "shorted" in str(e.value)

    cap_across_source = loop(
        {"id": "V1", "kind": "voltage_source", "value": 9, "a": "t", "b": "g"},
        {"id": "W2", "kind": "wire", "a": "t", "b": "r"},
        {"id": "C1", "kind": "capacitor", "value": 1e-6, "a": "r", "b": "b"},
        {"id": "W1", "kind": "wire", "a": "b", "b": "g"},
    )
    with pytest.raises(circuit.Invalid) as e:
        circuit.solve(cap_across_source)
    assert "capacitor" in str(e.value)

    charging_forever = loop(
        {"id": "I1", "kind": "current_source", "value": 0.01, "a": "g", "b": "t"},
        {"id": "W2", "kind": "wire", "a": "t", "b": "r"},
        {"id": "C1", "kind": "capacitor", "value": 1e-6, "a": "r", "b": "b"},
        {"id": "W1", "kind": "wire", "a": "b", "b": "g"},
    )
    with pytest.raises(circuit.Invalid) as e:
        circuit.solve(charging_forever)
    assert "never settles" in str(e.value)

    no_path = loop(
        {"id": "I1", "kind": "current_source", "value": 0.01, "a": "g", "b": "t"},
        {"id": "W2", "kind": "wire", "a": "t", "b": "r"},
        {"id": "C1", "kind": "capacitor", "value": 1e-6, "a": "r", "b": "b"},
    )
    no_path["nodes"] = [n for n in no_path["nodes"]]  # b connects only to C1: no way back to the source
    with pytest.raises(circuit.Invalid):
        circuit.solve(no_path)


def test_a_circuit_where_nothing_happens_is_refused_with_a_way_to_fix_it():
    # Seen in the cloud: an LC "switched on" with no source stays at 0 V and 0 A, a flat line on screen.
    no_source = loop(
        {"id": "C1", "kind": "capacitor", "value": 1e-3, "a": "t", "b": "g"},
        {"id": "W2", "kind": "wire", "a": "t", "b": "r"},
        {"id": "L1", "kind": "inductor", "value": 0.1, "a": "r", "b": "b"},
        {"id": "W1", "kind": "wire", "a": "b", "b": "g"},
    )
    with pytest.raises(circuit.Invalid) as e:
        circuit.solve(no_source)
    assert "no source" in str(e.value) and "in series with the inductor and the capacitor" in str(e.value)

    zero_source = loop(
        {"id": "V1", "kind": "voltage_source", "value": 0, "a": "t", "b": "g"},
        {"id": "R1", "kind": "resistor", "value": 100, "a": "t", "b": "r"},
        {"id": "C1", "kind": "capacitor", "value": 1e-6, "a": "r", "b": "b"},
        {"id": "W1", "kind": "wire", "a": "b", "b": "g"},
    )
    with pytest.raises(circuit.Invalid) as e:
        circuit.solve(zero_source)
    assert "no source" in str(e.value)


def test_a_current_source_drives_its_current_through_the_load():
    c = loop(
        {"id": "I1", "kind": "current_source", "value": 0.01, "a": "g", "b": "t"},
        {"id": "W2", "kind": "wire", "a": "t", "b": "r"},
        {"id": "R1", "kind": "resistor", "value": 100, "a": "r", "b": "b"},
        {"id": "W1", "kind": "wire", "a": "b", "b": "g"},
    )
    assert circuit.solve(c)["steady"]["parts"]["R1"]["voltage"] == pytest.approx(1.0)
