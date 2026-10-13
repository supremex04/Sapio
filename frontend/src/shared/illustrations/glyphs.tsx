import type { ReactElement } from "react";

/** The rounded tile with its chunky bottom edge, in one of the design system's colours. */
function Tile({ tone }: { tone: "teal" | "blue" | "amber" | "green" | "purple" }) {
  return (
    <>
      <rect x="3" y="6" width="42" height="39" rx="12" fill={`var(--${tone}-edge)`} />
      <rect x="3" y="3" width="42" height="38" rx="12" fill={`var(--${tone}-main)`} />
      <rect x="9" y="7" width="30" height="7" rx="3.5" fill="#fff" opacity="0.14" />
    </>
  );
}

/**
 * Ved's own icons for core product concepts, drawn in the design system's style (rounded tile, chunky bottom
 * edge, colours from tokens, so they follow the theme). Used through <Emoji name="…">, like the Fluent set.
 */
const GLYPHS = {
  /** Recall (spaced retrieval practice): a loop that brings knowledge back. */
  recall: () => (
    <>
      <Tile tone="teal" />
      <g fill="none" stroke="#fff" strokeWidth="4.5" strokeLinecap="round" strokeLinejoin="round">
        <path d="M14.6 18.6A10 10 0 0 1 32.4 16.6" />
        <path d="M33.4 25.4A10 10 0 0 1 15.6 27.4" />
      </g>
      <g fill="#fff" stroke="#fff" strokeWidth="1.6" strokeLinejoin="round">
        <path d="M35.4 21.2 36.2 13.6 28.6 17.4Z" />
        <path d="M12.6 22.8 11.8 30.4 19.4 26.6Z" />
      </g>
    </>
  ),
  /** The library: books on a shelf, one leaning. */
  library: () => (
    <>
      <Tile tone="blue" />
      <g fill="#fff">
        <rect x="11" y="12" width="6" height="20" rx="2" />
        <rect x="19" y="15" width="6" height="17" rx="2" />
        <rect x="27.5" y="13" width="6" height="19" rx="2" transform="rotate(14 30.5 32)" />
        <rect x="9" y="32" width="30" height="3.5" rx="1.75" />
      </g>
      <g fill="var(--blue-main)">
        <rect x="12.5" y="16" width="3" height="2" rx="1" />
        <rect x="20.5" y="19" width="3" height="2" rx="1" />
      </g>
    </>
  ),
  /** A document (uploaded, imported or being processed): a page with a folded corner and lines of text. */
  document: () => (
    <>
      <Tile tone="amber" />
      <path d="M15 10.5h12.5l6.5 6.5v16.5a2.5 2.5 0 0 1-2.5 2.5h-16.5a2.5 2.5 0 0 1-2.5-2.5v-20.5a2.5 2.5 0 0 1 2.5-2.5Z" fill="#fff" />
      <path d="M27.5 10.5v4.5a2 2 0 0 0 2 2h4.5Z" fill="var(--amber-edge)" opacity="0.45" />
      <g stroke="var(--amber-main)" strokeWidth="2.6" strokeLinecap="round">
        <path d="M17 21.5h11" />
        <path d="M17 26.5h11" />
        <path d="M17 31.5h6.5" />
      </g>
    </>
  ),
  /** Recent activity: rising bars. */
  activity: () => (
    <>
      <Tile tone="green" />
      <g fill="#fff">
        <rect x="11" y="24" width="6.5" height="10" rx="2.2" />
        <rect x="20.75" y="18" width="6.5" height="16" rx="2.2" />
        <rect x="30.5" y="11" width="6.5" height="23" rx="2.2" />
      </g>
    </>
  ),
  /** Personalize: sliders set to the learner. */
  personalize: () => (
    <>
      <Tile tone="purple" />
      <g stroke="#fff" strokeWidth="3.6" strokeLinecap="round">
        <path d="M12 14h24" />
        <path d="M12 22h24" />
        <path d="M12 30h24" />
      </g>
      <g fill="var(--purple-main)" stroke="#fff" strokeWidth="3.2">
        <circle cx="29" cy="14" r="3.6" />
        <circle cx="18" cy="22" r="3.6" />
        <circle cx="25" cy="30" r="3.6" />
      </g>
    </>
  ),
} satisfies Record<string, () => ReactElement>;

export type GlyphName = keyof typeof GLYPHS;

export const isGlyph = (name: string): name is GlyphName => name in GLYPHS;

export function Glyph({ name, size, label, className }: { name: GlyphName; size: number; label?: string; className?: string }) {
  const Body = GLYPHS[name];
  return (
    <svg
      viewBox="0 0 48 48"
      width={size}
      height={size}
      className={className}
      role={label ? "img" : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      focusable="false"
    >
      <Body />
    </svg>
  );
}
