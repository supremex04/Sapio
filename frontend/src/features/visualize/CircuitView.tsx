import { useEffect, useMemo, useRef, useState } from "react";
import type { SceneClock } from "./NarratedScene";
import type { CircuitPart, CircuitScene, Reading } from "./types";
import { si, UNIT } from "./units";

// Grid step and margin in drawing units; tight, so labels stay readable when a circuit spans the grid.
const U = 48;
const PAD = 50;
const DOT_GAP = 22;
const ACCENT = "#facc15";
const INK = "rgb(255 255 255 / 0.82)";

interface Point {
  x: number;
  y: number;
}

/**
 * A circuit, solved exactly by the renderer: the schematic on its grid, the parts the narration is about
 * highlighted, every part's voltage and current beside it, and the current itself as dots moving along the wires
 * (faster for more current, the way it flows). A circuit with capacitors or inductors plays its response from
 * switch-on across the narration, with the curve of the part being discussed below.
 */
export function CircuitView({ scene, clock }: { scene: CircuitScene; clock: SceneClock }) {
  const { circuit, solution } = scene;
  const transient = solution.kind === "transient" && solution.times && solution.parts;
  const sample = transient ? Math.round(clock.fraction * (solution.times!.length - 1)) : 0;
  const at = (id: string): Reading =>
    transient && solution.parts![id]
      ? { current: solution.parts![id].current[sample] ?? 0, voltage: solution.parts![id].voltage[sample] ?? 0 }
      : (solution.steady.parts[id] ?? { current: 0, voltage: 0 });

  const layout = useMemo(() => {
    const xs = circuit.nodes.map((n) => n.x);
    const ys = circuit.nodes.map((n) => n.y);
    const minX = Math.min(...xs);
    const maxY = Math.max(...ys);
    const where = new Map(circuit.nodes.map((n) => [n.id, { x: PAD + (n.x - minX) * U, y: PAD + (maxY - n.y) * U }]));
    const width = (Math.max(...xs) - minX) * U + 2 * PAD;
    const height = (maxY - Math.min(...ys)) * U + 2 * PAD;
    const degree = new Map<string, number>();
    for (const p of circuit.parts) for (const n of [p.a, p.b]) degree.set(n, (degree.get(n) ?? 0) + 1);
    return { where, width, height, degree };
  }, [circuit]);

  // The largest current anywhere, ever: dot speeds are relative to it (on a log scale, so small currents show).
  const largest = useMemo(() => {
    let most = 0;
    if (solution.parts) for (const v of Object.values(solution.parts)) for (const c of v.current) most = Math.max(most, Math.abs(c));
    for (const v of Object.values(solution.steady.parts)) most = Math.max(most, Math.abs(v.current));
    return most;
  }, [solution]);

  const focus = new Set(scene.focus[clock.beat] ?? []);
  const phases = useMoving(circuit.parts, at, largest, clock.playing);

  return (
    <div className="viz-circuit">
      <svg className="viz-schematic" viewBox={`0 0 ${layout.width} ${layout.height}`} role="img" aria-label={describe(circuit.parts)}>
        {circuit.parts.map((p) => {
          const a = layout.where.get(p.a)!;
          const b = layout.where.get(p.b)!;
          return <Part key={p.id} part={p} a={a} b={b} lit={focus.has(p.id)} dim={focus.size > 0 && !focus.has(p.id)} />;
        })}
        {circuit.nodes
          .filter((n) => (layout.degree.get(n.id) ?? 0) >= 3)
          .map((n) => {
            const p = layout.where.get(n.id)!;
            return <circle key={n.id} cx={p.x} cy={p.y} r={4} fill={INK} />;
          })}
        {circuit.nodes
          .filter((n) => n.ground)
          .map((n) => {
            const p = layout.where.get(n.id)!;
            return (
              <g key={`g-${n.id}`} stroke={INK} strokeWidth={2} strokeLinecap="round">
                <path d={`M${p.x} ${p.y} v10 M${p.x - 13} ${p.y + 10} h26 M${p.x - 8} ${p.y + 15} h16 M${p.x - 3} ${p.y + 20} h6`} />
              </g>
            );
          })}
        {circuit.parts.map((p) => {
          const a = layout.where.get(p.a)!;
          const b = layout.where.get(p.b)!;
          return <Flow key={`f-${p.id}`} a={a} b={b} phase={phases.current.get(p.id) ?? 0} moving={Math.abs(at(p.id).current) > largest * 1e-4} />;
        })}
        {circuit.parts
          .filter((p) => p.kind !== "wire")
          .map((p) => (
            <Label key={`l-${p.id}`} part={p} a={layout.where.get(p.a)!} b={layout.where.get(p.b)!} reading={at(p.id)} lit={focus.has(p.id)} />
          ))}
      </svg>
      {transient && <Response scene={scene} sample={sample} focus={focus} />}
    </div>
  );
}

/** Advances each part's dot phase every frame while the narration plays: speed from its current, sign its way. */
function useMoving(parts: CircuitPart[], at: (id: string) => Reading, largest: number, playing: boolean) {
  const phases = useRef(new Map<string, number>());
  const [, redraw] = useState(0);
  const read = useRef(at);
  read.current = at;
  useEffect(() => {
    if (!playing || largest <= 0) return;
    let last = performance.now();
    let frame = 0;
    const tick = (now: number) => {
      const dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      for (const p of parts) {
        const i = read.current(p.id).current;
        const size = Math.abs(i);
        if (size <= largest * 1e-4) continue;
        const scale = Math.log10(size / (largest / 1000)) / 3; // 0 at a thousandth of the largest, 1 at the largest
        const speed = 16 + 64 * Math.max(0, Math.min(1, scale));
        phases.current.set(p.id, ((phases.current.get(p.id) ?? 0) + Math.sign(i) * speed * dt) % DOT_GAP);
      }
      redraw((n) => n + 1);
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [parts, largest, playing]);
  return phases;
}

/** Dots along a part from a to b, shifted by its phase: positive phase moves them from a towards b. */
function Flow({ a, b, phase, moving }: { a: Point; b: Point; phase: number; moving: boolean }) {
  if (!moving) return null;
  const length = Math.hypot(b.x - a.x, b.y - a.y);
  const ux = (b.x - a.x) / length;
  const uy = (b.y - a.y) / length;
  const dots = [];
  for (let s = ((phase % DOT_GAP) + DOT_GAP) % DOT_GAP; s < length; s += DOT_GAP) {
    dots.push(<circle key={s} cx={a.x + ux * s} cy={a.y + uy * s} r={2.6} fill="var(--amber-main)" />);
  }
  return <g aria-hidden>{dots}</g>;
}

/** The part's symbol drawn along its segment (a to b), with its leads. */
function Part({ part, a, b, lit, dim }: { part: CircuitPart; a: Point; b: Point; lit: boolean; dim: boolean }) {
  const length = Math.hypot(b.x - a.x, b.y - a.y);
  const angle = (Math.atan2(b.y - a.y, b.x - a.x) * 180) / Math.PI;
  const c = length / 2;
  const colour = lit ? ACCENT : INK;
  const lead = (from: number, to: number) => `M${from} 0 H${to}`;
  let body: React.ReactNode;
  switch (part.kind) {
    case "resistor": {
      const zig = Array.from({ length: 6 }, (_, i) => `L${c - 20 + (i + 0.5) * (40 / 6)} ${i % 2 ? 7 : -7}`).join(" ");
      body = <path d={`${lead(0, c - 20)} ${zig} L${c + 20} 0 ${lead(c + 20, length)}`} />;
      break;
    }
    case "capacitor":
      body = <path d={`${lead(0, c - 5)} M${c - 5} -14 V14 M${c + 5} -14 V14 ${lead(c + 5, length)}`} />;
      break;
    case "inductor":
      body = <path d={`${lead(0, c - 20)} a5 6 0 0 1 10 0 a5 6 0 0 1 10 0 a5 6 0 0 1 10 0 a5 6 0 0 1 10 0 ${lead(c + 20, length)}`} />;
      break;
    case "voltage_source":
    case "current_source":
      body = (
        <>
          <path d={`${lead(0, c - 16)} ${lead(c + 16, length)}`} />
          <circle cx={c} cy={0} r={16} fill="#0b1020" />
          {part.kind === "current_source" && <path d={`M${c - 9} 0 H${c + 8} M${c + 3} -5 L${c + 9} 0 L${c + 3} 5`} />}
        </>
      );
      break;
    default:
      body = <path d={lead(0, length)} />;
  }
  return (
    <g transform={`translate(${a.x} ${a.y}) rotate(${angle})`} stroke={colour} strokeWidth={lit ? 3 : 2} fill="none" strokeLinecap="round" strokeLinejoin="round" opacity={dim ? 0.55 : 1}>
      {lit && part.kind !== "wire" && <rect x={c - 26} y={-20} width={52} height={40} rx={10} fill={ACCENT} fillOpacity={0.12} stroke="none" />}
      {body}
      {part.kind === "voltage_source" && <Polarity c={c} angle={angle} />}
    </g>
  );
}

/** + on the a side of a voltage source and - on the b side, kept upright whatever way the source points. */
function Polarity({ c, angle }: { c: number; angle: number }) {
  const upright = `rotate(${-angle})`;
  return (
    <g stroke="none" fill={INK} fontSize={13} fontWeight={700} textAnchor="middle" dominantBaseline="central">
      <text transform={`translate(${c - 8} 0) ${upright}`}>+</text>
      <text transform={`translate(${c + 8} 0) ${upright}`}>{"−"}</text>
    </g>
  );
}

/** "R1 1.00 kOhm" and, under it, its voltage and current now (from the solver). */
function Label({ part, a, b, reading, lit }: { part: CircuitPart; a: Point; b: Point; reading: Reading; lit: boolean }) {
  const horizontal = a.y === b.y;
  const cx = (a.x + b.x) / 2;
  const cy = (a.y + b.y) / 2;
  const name = `${part.id}  ${si(part.value ?? 0, UNIT[part.kind] ?? "")}`;
  const now = `${si(Math.abs(reading.voltage), "V")} · ${si(Math.abs(reading.current), "A")}`;
  const x = horizontal ? cx : cx + 26;
  const anchor = horizontal ? "middle" : "start";
  return (
    <g className="viz-part-label" textAnchor={anchor} fill={lit ? ACCENT : INK}>
      <text x={x} y={horizontal ? cy - 27 : cy - 4} fontSize={15} fontWeight={700}>
        {name}
      </text>
      <text x={x} y={horizontal ? cy + 37 : cy + 15} fontSize={13.5} fillOpacity={0.85}>
        {now}
      </text>
    </g>
  );
}

/** The response of the reactive part being discussed (else the first one): one quantity, one axis, the narration's moment marked. */
function Response({ scene, sample, focus }: { scene: CircuitScene; sample: number; focus: Set<string> }) {
  const { solution, circuit } = scene;
  const reactive = circuit.parts.filter((p) => p.kind === "capacitor" || p.kind === "inductor");
  const shown = reactive.find((p) => focus.has(p.id)) ?? reactive[0];
  const [hover, setHover] = useState<number | null>(null);
  if (!shown || !solution.times || !solution.parts) return null;
  const quantity = shown.kind === "capacitor" ? "voltage" : "current";
  const values = solution.parts[shown.id]![quantity];
  const unit = quantity === "voltage" ? "V" : "A";
  const times = solution.times;
  const end = times[times.length - 1]!;
  const lo = Math.min(0, ...values);
  const hi = Math.max(...values, lo + 1e-12);
  const W = 560;
  const H = 120;
  const L = 56;
  const R = 12;
  const T = 14;
  const B = 26;
  const px = (k: number) => L + ((times[k]! / end) * (W - L - R));
  const py = (v: number) => T + (1 - (v - lo) / (hi - lo)) * (H - T - B);
  const path = values.map((v, k) => `${k ? "L" : "M"}${px(k).toFixed(1)} ${py(v).toFixed(1)}`).join(" ");
  const k = hover ?? sample;
  const title = `${quantity === "voltage" ? "Voltage across" : "Current through"} ${shown.id}`;
  return (
    <figure className="viz-response">
      <figcaption>
        {title}
        <span>
          t = {si(times[k]!, "s")}: {si(values[k]!, unit)}
        </span>
      </figcaption>
      <svg
        viewBox={`0 0 ${W} ${H}`}
        role="img"
        aria-label={`${title} from switch-on to ${si(end, "s")}: from ${si(values[0]!, unit)} to ${si(values[values.length - 1]!, unit)}`}
        onPointerMove={(e) => {
          const box = e.currentTarget.getBoundingClientRect();
          const x = ((e.clientX - box.left) / box.width) * W;
          const f = Math.max(0, Math.min(1, (x - L) / (W - L - R)));
          setHover(Math.round(f * (times.length - 1)));
        }}
        onPointerLeave={() => setHover(null)}
      >
        <g stroke="rgb(255 255 255 / 0.18)" strokeWidth={1}>
          <line x1={L} x2={W - R} y1={py(lo)} y2={py(lo)} />
          <line x1={L} x2={W - R} y1={py(hi)} y2={py(hi)} strokeDasharray="3 4" />
        </g>
        <g fill="rgb(255 255 255 / 0.6)" fontSize={11}>
          <text x={L - 6} y={py(hi) + 4} textAnchor="end">
            {si(hi, unit)}
          </text>
          <text x={L - 6} y={py(lo) + 4} textAnchor="end">
            {si(lo, unit)}
          </text>
          <text x={L} y={H - 8}>0</text>
          <text x={W - R} y={H - 8} textAnchor="end">
            {si(end, "s")}
          </text>
        </g>
        <path d={path} fill="none" stroke="var(--purple-main)" strokeWidth={2} strokeLinejoin="round" />
        <line x1={px(k)} x2={px(k)} y1={T} y2={H - B} stroke="rgb(255 255 255 / 0.45)" strokeWidth={1} />
        <circle cx={px(k)} cy={py(values[k]!)} r={4.5} fill="var(--purple-main)" stroke="#0b1020" strokeWidth={2} />
      </svg>
    </figure>
  );
}

function describe(parts: CircuitPart[]): string {
  const named = parts.filter((p) => p.kind !== "wire").map((p) => `${p.id} ${si(p.value ?? 0, UNIT[p.kind] ?? "")}`);
  return `Circuit schematic: ${named.join(", ")}.`;
}
