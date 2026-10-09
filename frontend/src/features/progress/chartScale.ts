/** "Nice" axis maximum and ticks for minute values (0, 5, 10, 15 … / 0, 10, 20 …). */
export function niceTicks(maxValue: number, targetTicks = 4): number[] {
  const max = Math.max(maxValue, 5); // never fractional minutes on an empty chart
  const rough = max / targetTicks;
  const pow = 10 ** Math.floor(Math.log10(rough));
  const step = [1, 2, 5, 10].map((m) => m * pow).find((s) => s >= rough) ?? 10 * pow;
  const top = Math.ceil(max / step) * step;
  const ticks: number[] = [];
  for (let v = 0; v <= top + 1e-9; v += step) ticks.push(Math.round(v * 1000) / 1000);
  return ticks;
}
