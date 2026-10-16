/** Chain colours for the protein view and its legend (one per chain, in file order; they repeat after eight). */
const CHAIN_COLOURS = ["#60a5fa", "#f472b6", "#34d399", "#fbbf24", "#a78bfa", "#fb923c", "#22d3ee", "#f87171"];

export function chainColour(index: number): string {
  return CHAIN_COLOURS[index % CHAIN_COLOURS.length] ?? "#cbd5e1";
}
