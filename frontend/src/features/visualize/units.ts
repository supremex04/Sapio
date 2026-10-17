const PREFIXES: [number, string][] = [
  [1e9, "G"],
  [1e6, "M"],
  [1e3, "k"],
  [1, ""],
  [1e-3, "m"],
  [1e-6, "\u00b5"],
  [1e-9, "n"],
  [1e-12, "p"],
];

/** A value with an SI prefix and three significant figures: 0.0025 A as "2.50 mA", 1000 ohm as "1.00 k\u2126". */
export function si(value: number, unit: string): string {
  if (!Number.isFinite(value)) return "\u2013";
  const size = Math.abs(value);
  if (size < 1e-15) return `0 ${unit}`;
  const [scale, prefix] = PREFIXES.find(([s]) => size >= s * 0.9995) ?? PREFIXES[PREFIXES.length - 1]!;
  const scaled = value / scale;
  const digits = Math.abs(scaled) >= 100 ? 0 : Math.abs(scaled) >= 10 ? 1 : 2;
  return `${scaled.toFixed(digits)} ${prefix}${unit}`;
}

export const UNIT: Record<string, string> = {
  resistor: "\u2126",
  capacitor: "F",
  inductor: "H",
  voltage_source: "V",
  current_source: "A",
};
