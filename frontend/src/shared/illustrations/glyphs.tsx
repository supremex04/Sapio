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
 * Sapio's own icons for core product concepts, drawn in the design system's style (rounded tile, chunky bottom
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
  // ---- Personalize: what you do (blue)
  graduate: () => (
    <>
      <Tile tone="blue" />
      <path d="M24 11 39 17.5 24 24 9 17.5Z" fill="#fff" />
      <path d="M16 20.5v6c0 2.6 3.6 4.8 8 4.8s8-2.2 8-4.8v-6" fill="#fff" opacity="0.85" />
      <path d="M36.5 18.6v8.4" stroke="#fff" strokeWidth="2.6" strokeLinecap="round" />
      <circle cx="36.5" cy="28.6" r="2.2" fill="#fff" />
    </>
  ),
  briefcase: () => (
    <>
      <Tile tone="blue" />
      <path d="M19 17v-2.5a2.5 2.5 0 0 1 2.5-2.5h5a2.5 2.5 0 0 1 2.5 2.5V17" fill="none" stroke="#fff" strokeWidth="3" />
      <rect x="11" y="16.5" width="26" height="17" rx="3.5" fill="#fff" />
      <path d="M11 24h26" stroke="var(--blue-main)" strokeWidth="2.4" />
      <rect x="21.5" y="22" width="5" height="4.5" rx="1.2" fill="var(--blue-main)" />
    </>
  ),
  teacher: () => (
    <>
      <Tile tone="blue" />
      <rect x="10" y="10" width="28" height="19" rx="2.5" fill="none" stroke="#fff" strokeWidth="3" />
      <path d="M15.5 16.5h10M15.5 22h15" stroke="#fff" strokeWidth="3" strokeLinecap="round" />
      <path d="M18 29l-3 6.5M30 29l3 6.5" stroke="#fff" strokeWidth="3" strokeLinecap="round" />
    </>
  ),
  compass: () => (
    <>
      <Tile tone="blue" />
      <circle cx="24" cy="22" r="11.5" fill="none" stroke="#fff" strokeWidth="3" />
      <path d="M29.5 16.5 26.3 24.3 18.5 27.5 21.7 19.7Z" fill="#fff" />
    </>
  ),
  // ---- Personalize: academic level (purple)
  pencil: () => (
    <>
      <Tile tone="purple" />
      <path d="M13 33.5l1.8-7.2L30 11.1a3 3 0 0 1 4.3 4.3L19.1 30.6Z" fill="#fff" />
      <path d="M15.3 26.6l4.6 4.6" stroke="var(--purple-main)" strokeWidth="2.2" />
    </>
  ),
  books: () => (
    <>
      <Tile tone="purple" />
      <rect x="11" y="27" width="26" height="6.5" rx="2" fill="#fff" />
      <rect x="13.5" y="19.5" width="21" height="6.5" rx="2" fill="#fff" opacity="0.85" />
      <rect x="16" y="12" width="16" height="6.5" rx="2" fill="#fff" opacity="0.7" />
    </>
  ),
  university: () => (
    <>
      <Tile tone="purple" />
      <path d="M9.5 18 24 10 38.5 18Z" fill="#fff" />
      <g fill="#fff">
        <rect x="13" y="20" width="4" height="10" rx="1" />
        <rect x="22" y="20" width="4" height="10" rx="1" />
        <rect x="31" y="20" width="4" height="10" rx="1" />
        <rect x="10" y="31" width="28" height="4" rx="1.5" />
      </g>
    </>
  ),
  diploma: () => (
    <>
      <Tile tone="purple" />
      <rect x="9.5" y="12" width="29" height="18" rx="3" fill="#fff" />
      <path d="M14.5 18h14M14.5 23h9" stroke="var(--purple-main)" strokeWidth="2.6" strokeLinecap="round" />
      <circle cx="32" cy="29" r="4.6" fill="var(--purple-edge)" stroke="#fff" strokeWidth="2.4" />
    </>
  ),
  flask: () => (
    <>
      <Tile tone="purple" />
      <path
        d="M20 10.5h8M21.5 10.5v8L13.2 31.4A2.8 2.8 0 0 0 15.6 35.5h16.8a2.8 2.8 0 0 0 2.4-4.1L26.5 18.5v-8"
        fill="none"
        stroke="#fff"
        strokeWidth="3"
        strokeLinejoin="round"
        strokeLinecap="round"
      />
      <path d="M16.6 27.5h14.8l3 5H13.6Z" fill="#fff" />
    </>
  ),
  sprout: () => (
    <>
      <Tile tone="purple" />
      <path d="M24 35V22" stroke="#fff" strokeWidth="3.2" strokeLinecap="round" />
      <path d="M24 27C23 18.5 18 14 10.5 14 10.5 21.5 15.5 27 24 27Z" fill="#fff" />
      <path d="M24 23C25 15.5 29.5 10.5 37 10.5 37 18 32 23 24 23Z" fill="#fff" opacity="0.85" />
    </>
  ),
  // ---- Personalize: what helps you (teal)
  target: () => (
    <>
      <Tile tone="teal" />
      <circle cx="24" cy="22" r="11.5" fill="none" stroke="#fff" strokeWidth="3" />
      <circle cx="24" cy="22" r="6.2" fill="none" stroke="#fff" strokeWidth="3" />
      <circle cx="24" cy="22" r="2.3" fill="#fff" />
    </>
  ),
  venn: () => (
    <>
      <Tile tone="teal" />
      <circle cx="19" cy="22" r="8.5" fill="#fff" opacity="0.6" />
      <circle cx="29" cy="22" r="8.5" fill="#fff" opacity="0.6" />
      <circle cx="19" cy="22" r="8.5" fill="none" stroke="#fff" strokeWidth="2.6" />
      <circle cx="29" cy="22" r="8.5" fill="none" stroke="#fff" strokeWidth="2.6" />
    </>
  ),
  gear: () => (
    <>
      <Tile tone="teal" />
      <circle cx="24" cy="22" r="9" fill="none" stroke="#fff" strokeWidth="5" strokeDasharray="3.5 3.57" />
      <circle cx="24" cy="22" r="7.2" fill="#fff" />
      <circle cx="24" cy="22" r="3" fill="var(--teal-main)" />
    </>
  ),
  sigma: () => (
    <>
      <Tile tone="teal" />
      <path d="M32.5 12H15.5l9 10-9 10h17" fill="none" stroke="#fff" strokeWidth="3.8" strokeLinecap="round" strokeLinejoin="round" />
    </>
  ),
  code: () => (
    <>
      <Tile tone="teal" />
      <g fill="none" stroke="#fff" strokeWidth="3.4" strokeLinecap="round" strokeLinejoin="round">
        <path d="M18 15l-7 7 7 7M30 15l7 7-7 7" />
        <path d="M26.5 12.5l-5 19" />
      </g>
    </>
  ),
  stairs: () => (
    <>
      <Tile tone="teal" />
      <path d="M10 33.5h8v-7h7v-7h7v-7h6" fill="none" stroke="#fff" strokeWidth="3.6" strokeLinecap="round" strokeLinejoin="round" />
    </>
  ),
  // ---- Personalize: goal (amber)
  trophy: () => (
    <>
      <Tile tone="amber" />
      <path d="M17 11h14v7.5a7 7 0 0 1-14 0Z" fill="#fff" />
      <path d="M17 14h-3.5a4 4 0 0 0 4 6M31 14h3.5a4 4 0 0 1-4 6" fill="none" stroke="#fff" strokeWidth="2.6" strokeLinecap="round" />
      <path d="M24 25.5v5" stroke="#fff" strokeWidth="3" />
      <rect x="17.5" y="30.5" width="13" height="4" rx="1.5" fill="#fff" />
    </>
  ),
  bulb: () => (
    <>
      <Tile tone="amber" />
      <path d="M24 9.5a9.5 9.5 0 0 0-5.5 17.2V29.5h11v-2.8A9.5 9.5 0 0 0 24 9.5Z" fill="#fff" />
      <path d="M20 33h8M21.5 36h5" stroke="#fff" strokeWidth="2.6" strokeLinecap="round" />
    </>
  ),
  wrench: () => (
    <>
      <Tile tone="amber" />
      <path
        d="M34.2 11.3a6.6 6.6 0 0 0-8.6 8.3L12.6 32.6a2.9 2.9 0 0 0 4.1 4.1L29.7 23.7a6.6 6.6 0 0 0 8.3-8.6l-4 4-3.4-.6-.6-3.4Z"
        fill="#fff"
      />
    </>
  ),
  sparkle: () => (
    <>
      <Tile tone="amber" />
      <path d="M22 9c1 7.5 4 10.5 11.5 11.5C26 21.5 23 24.5 22 32c-1-7.5-4-10.5-11.5-11.5C18 19.5 21 16.5 22 9Z" fill="#fff" />
      <path d="M33 26c.4 2.6 1.4 3.6 4 4-2.6.4-3.6 1.4-4 4-.4-2.6-1.4-3.6-4-4 2.6-.4 3.6-1.4 4-4Z" fill="#fff" />
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
