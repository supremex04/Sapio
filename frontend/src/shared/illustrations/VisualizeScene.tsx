import { useId } from "react";
import { Sage } from "./Sage";

/**
 * Sapi visualizing: sitting on the shore at dusk, looking up, its thought rising into the sky as a chalk stream that
 * branches into what Sapio can show (a wave and a formula, an idea, an atom, a molecule ring, code). The cover of a
 * visualization that has no picture of its own (a traced program, one still being made). An original drawing (ours),
 * 16:9 like the thumbnails; the sky stays a dusk sky in both themes, the accents come from tokens.
 */
export function VisualizeScene({ className, label }: { className?: string; label?: string }) {
  const id = useId().replace(/:/g, "");
  const sky = `viz-sky-${id}`;
  const sea = `viz-sea-${id}`;
  const glow = `viz-glow-${id}`;
  return (
    <svg
      viewBox="0 0 320 180"
      preserveAspectRatio="xMidYMid slice"
      className={className}
      role={label ? "img" : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    >
      <defs>
        <linearGradient id={sky} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#141029" />
          <stop offset="0.45" stopColor="#2a1f55" />
          <stop offset="0.68" stopColor="#4f356f" />
          <stop offset="0.8" stopColor="#b86f70" />
          <stop offset="0.86" stopColor="#f2a97a" />
        </linearGradient>
        <linearGradient id={sea} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#3b4f6e" />
          <stop offset="1" stopColor="#141c2e" />
        </linearGradient>
        <radialGradient id={glow} cx="0.5" cy="0.5" r="0.5">
          <stop offset="0" stopColor="#fff" stopOpacity="0.35" />
          <stop offset="1" stopColor="#fff" stopOpacity="0" />
        </radialGradient>
      </defs>

      {/* Dusk sky, the horizon, and calm water with the last light on it */}
      <rect width="320" height="156" fill={`url(#${sky})`} />
      <rect y="154" width="320" height="26" fill={`url(#${sea})`} />
      <rect y="153.5" width="320" height="1.2" fill="#f7c9a3" opacity="0.7" />
      <path d="M40 162 h70 M150 166 h40 M222 161 h60 M96 171 h34 M206 172 h28" stroke="#f7c9a3" strokeOpacity="0.3" strokeWidth="1.2" strokeLinecap="round" />

      {/* Stars */}
      <g fill="#fff">
        {STARS.map(([x, y, r], i) => (
          <circle key={i} cx={x} cy={y} r={r} opacity={0.35 + (i % 3) * 0.2} />
        ))}
      </g>
      <g fill="var(--amber-main)">
        <path d={sparkle(22, 26, 4)} />
        <path d={sparkle(302, 104, 3.2)} />
        <path d={sparkle(128, 12, 2.6)} />
      </g>

      {/* The thought, rising from Sapi's thought bubbles in dotted trails toward what it pictures */}
      <g fill="none" stroke="#fff" strokeOpacity="0.7" strokeWidth="2" strokeLinecap="round" strokeDasharray="0.1 5">
        <path d="M181 108 C172 90 146 80 116 72" />
        <path d="M184 106 C192 88 170 66 160 40" />
        <path d="M187 108 C202 88 236 76 262 58" />
      </g>
      <g fill="none" stroke="#fff" strokeOpacity="0.88" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">

        {/* A wave and a formula */}
        <path d="M62 46 q7 -11 14 0 t14 0 t14 0" />
        <path d="M58 54 h52" strokeOpacity="0.5" />
        {/* An idea */}
        <circle cx="156" cy="21" r="6.5" />
        <path d="M152.5 27 v3.5 h7 v-3.5 M153.5 33 h5" />
        <path d="M156 9 v-3 M146 13 l-2.2 -2.2 M166 13 l2.2 -2.2 M143 21 h-3 M169 21 h3" strokeOpacity="0.6" />
        {/* An atom */}
        <ellipse cx="104" cy="98" rx="13" ry="4.6" />
        <ellipse cx="104" cy="98" rx="13" ry="4.6" transform="rotate(60 104 98)" />
        <ellipse cx="104" cy="98" rx="13" ry="4.6" transform="rotate(-60 104 98)" />
        {/* A molecule ring */}
        <path d="M276 30 l9.5 5.5 v11 l-9.5 5.5 l-9.5 -5.5 v-11 Z" />
        <circle cx="276" cy="41" r="5" strokeOpacity="0.7" />
        <path d="M285.5 35.5 l7 -4 M266.5 46.5 l-7 4" strokeOpacity="0.7" />
        {/* A signal through time */}
        <path d="M262 84 h8 l4 -10 l6 20 l6 -16 l4 6 h10" strokeOpacity="0.85" />
      </g>
      <circle cx="104" cy="98" r="2" fill="var(--amber-main)" />
      <g fill="#fff" fillOpacity="0.88" fontFamily="'Latin Modern Math', 'Cambria Math', Georgia, serif" fontStyle="italic">
        <text x="56" y="72" fontSize="11">
          a² + b² = c²
        </text>
        <text x="214" y="104" fontSize="12" fontStyle="normal" fontFamily="ui-monospace, Menlo, Consolas, monospace">
          {"{ }"}
        </text>
      </g>

      {/* Sapi, looking up at it all, on the shore; a soft light around it */}
      <ellipse cx="160" cy="146" rx="34" ry="22" fill={`url(#${glow})`} />
      <svg x="135" y="110" width="50" height="52" overflow="visible">
        <Sage mood="thinking" size={50} still />
      </svg>
    </svg>
  );
}

/** A four-pointed sparkle centred on (x, y). */
function sparkle(x: number, y: number, r: number): string {
  const k = r * 0.28;
  return `M${x} ${y - r} L${x + k} ${y - k} L${x + r} ${y} L${x + k} ${y + k} L${x} ${y + r} L${x - k} ${y + k} L${x - r} ${y} L${x - k} ${y - k} Z`;
}

const STARS: [number, number, number][] = [
  [12, 10, 0.9], [48, 14, 0.7], [70, 8, 1], [96, 18, 0.6], [118, 40, 0.8], [140, 6, 0.7], [178, 10, 0.9],
  [200, 30, 0.6], [214, 8, 1], [240, 18, 0.7], [300, 12, 0.9], [312, 58, 0.7], [292, 84, 0.6], [16, 70, 0.8],
  [30, 104, 0.6], [62, 86, 0.7], [140, 70, 0.6], [208, 52, 0.8], [228, 120, 0.6], [74, 122, 0.5], [296, 132, 0.5],
];
