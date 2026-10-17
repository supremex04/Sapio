# 0018. Circuits: described as grid data, solved exactly, drawn in the browser

- Status: Accepted
- Date: 2026-10-17

## Context

Electric circuits are a large part of physics and electronics teaching. An animation can draw a circuit but cannot be
trusted with its numbers, and the product rule is to ship only what works fully. Options considered: SchemDraw
(schematics from Python), lcapy (symbolic analysis), PySpice/ngspice (full SPICE), Falstad CircuitJS (an interactive
simulator in the browser), WaveDrom and DigitalJS (digital logic).

## Decision

- **A fifth engine, `CIRCUIT`, for linear DC circuits**: resistors, capacitors, inductors, DC voltage and current
  sources, wires. The AI describes the circuit as data (nodes on a grid, parts between them) in the storyboard call;
  it never writes code and never states computed values (captions explain; the viewer shows the numbers).
- **Checked, then solved exactly**, in the renderer (`circuit.py`). As a drawing: straight horizontal or vertical
  parts, nothing crossing, overlapping or passing over a node; as a circuit: grounded, connected, no shorted part,
  solvable, no current over 100 A. Each problem is described in words and the AI repairs the circuit (up to
  `max-attempts` tries). Solving: the DC operating point, or the response from switch-on by reducing the circuit to
  x' = A x + b (capacitor voltages, inductor currents) and using x(t) = x_ss + expm(A t)(x0 - x_ss): no time
  stepping, so no integration error; LC circuits oscillate without damping; the window is five time constants or
  three cycles, shortened so the fastest oscillation keeps 20 samples a cycle (never aliased).
- **Drawn in the browser**, not by a schematic library: the viewer knows every part's position, so it highlights the
  parts a step is about, moves current as dots along the wires (speed from the solved current, direction from its
  sign), shows each part's voltage and current, plots the response of the reactive part being discussed with the
  narration's moment marked, and lists all values in a table. No WebGL, no new dependency (the solver uses numpy and
  scipy, already in the image).
- **Routing**: rules for unmistakable circuit questions (voltage divider, Ohm's or Kirchhoff's laws, RC/RL/LC/RLC,
  series and parallel circuits), else the storyboard chooses; a selector option "Circuit".
- **Not taken now**, because they would not work as fully: diodes, LEDs and transistors (non-linear: an iterative
  solver that can fail to converge), AC sources and frequency response, digital logic, and an embedded Falstad
  simulator (its circuit-text format is easy for the AI to get subtly wrong). AC, signals, how a part works inside,
  and circuit boards stay animations.

## Consequences

- A circuit costs one AI call (plus a repair call for each refused attempt) and milliseconds of CPU.
- Every number on screen is computed; the narration is kept qualitative by the prompt.
- Adding a part kind means adding it to the checker, the solver (as a stamp in the network or a state), the
  symbols and the prompt together, with tests against known answers.
