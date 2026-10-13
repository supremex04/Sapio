import { cx } from "../ui/cx";
import "./sage.css";

/**
 * Sapi: Sapio's mascot, the face of a kindly sage: white topknot, bushy brows, a round beard and moustache.
 * Face only, so expressions read even at small sizes. Flat colour with solid darker "edges" for depth, like
 * the rest of the design system (no outlines). An original character, hand-made SVG (ours). Colours come
 * from tokens (--skin-*, --hair-*, --clay-*), so it follows the theme. Use at most once per screen, at moments
 * that matter. See docs/specs/design-system.md.
 */
export type SageMood = "happy" | "cheer" | "thinking" | "encourage" | "oops" | "sleepy";

interface Props {
  mood?: SageMood;
  size?: number;
  /** Accessible description; omit when decorative. */
  label?: string;
  className?: string;
  /** Disable idle animation (e.g. in dense lists). */
  still?: boolean;
}

export function Sage({ mood = "happy", size = 96, label, className, still }: Props) {
  return (
    <svg
      viewBox="0 0 120 124"
      width={size}
      height={size * (124 / 120)}
      className={cx("sage", `sage--${mood}`, still && "sage--still", className)}
      role={label ? "img" : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    >
      <ellipse cx="60" cy="119" rx="30" ry="4" className="sage-shadow" />

      <g className="sage-head">
        {/* Topknot */}
        <circle cx="60" cy="18" r="10" className="sage-hair-edge" />
        <circle cx="60" cy="15" r="10" className="sage-hair" />
        <rect x="52" y="22" width="16" height="6" rx="3" className="sage-band" />

        {/* Ears and face */}
        <circle cx="24" cy="60" r="7.5" className="sage-skin-edge" />
        <circle cx="96" cy="60" r="7.5" className="sage-skin-edge" />
        <ellipse cx="60" cy="62" rx="36" ry="33" className="sage-skin-edge" />
        <ellipse cx="60" cy="58" rx="36" ry="33" className="sage-skin" />

        {/* Hair cap */}
        <path d={CAP} className="sage-hair-edge" transform="translate(0 3)" />
        <path d={CAP} className="sage-hair" />

        {/* Beard, moustache and nose */}
        <path d={BEARD} className="sage-hair-edge" transform="translate(0 4)" />
        <path d={BEARD} className="sage-hair" />
        <path d={MOUSTACHE} className="sage-hair-edge" transform="translate(0 2.5)" />
        <path d={MOUSTACHE} className="sage-hair" />
        <ellipse cx="60" cy="68" rx="5.5" ry="4.5" className="sage-skin-edge" />
        <ellipse cx="58.5" cy="66.5" rx="2" ry="1.4" className="sage-glint" />
      </g>

      <Face mood={mood} />
    </svg>
  );
}

const CAP = "M24 54 C22 34 38 25 60 25 C82 25 98 34 96 54 C88 42 76 37 60 37 C44 37 32 42 24 54 Z";
const BEARD = "M25 66 C31 78 44 82 60 82 C76 82 89 78 95 66 C99 94 82 112 60 114 C38 112 21 94 25 66 Z";
const MOUSTACHE = "M60 75 C55 69 44 69 39 76 C46 81 55 80 60 75 C65 80 74 81 81 76 C76 69 65 69 60 75 Z";

/** Bushy white brows with a solid edge: half of every expression. */
function Brows({ tilt = 0, raise = 0 }: { tilt?: number; raise?: number }) {
  const left = `M38 ${46 + tilt} Q46 ${38 - tilt} 54 ${43 - tilt}`;
  const right = `M66 ${43 - tilt - raise} Q74 ${38 - tilt - raise} 82 ${46 + tilt - raise}`;
  return (
    <g>
      <g transform="translate(0 2.5)" className="sage-brow sage-brow--edge">
        <path d={left} />
        <path d={right} />
      </g>
      <g className="sage-brow">
        <path d={left} />
        <path d={right} />
      </g>
    </g>
  );
}

function Face({ mood }: { mood: SageMood }) {
  const cheeks = (
    <>
      <ellipse cx="38" cy="66" rx="6" ry="3.6" className="sage-cheek" />
      <ellipse cx="82" cy="66" rx="6" ry="3.6" className="sage-cheek" />
    </>
  );
  const openEyes = (dx = 0, dy = 0) => (
    <g className="sage-eyes">
      <ellipse cx={47 + dx} cy={56 + dy} rx="4.6" ry="6" className="sage-ink" />
      <ellipse cx={73 + dx} cy={56 + dy} rx="4.6" ry="6" className="sage-ink" />
      <circle cx={48.6 + dx} cy={53.6 + dy} r="1.7" className="sage-shine" />
      <circle cx={74.6 + dx} cy={53.6 + dy} r="1.7" className="sage-shine" />
    </g>
  );
  switch (mood) {
    case "cheer":
      return (
        <g>
          <Brows tilt={-2} />
          <path d="M41 58 Q47 50 53 58" className="sage-line" />
          <path d="M67 58 Q73 50 79 58" className="sage-line" />
          {cheeks}
          <path d="M50 84 Q60 99 70 84 Z" className="sage-ink" />
          <path d="M54 91 Q60 96 66 91 Q60 88 54 91 Z" className="sage-tongue" />
          <path d="M10 34 l3 -7 l3 7 l7 3 l-7 3 l-3 7 l-3 -7 l-7 -3 Z" className="sage-spark" />
          <path d="M104 26 l2.4 -5.6 l2.4 5.6 l5.6 2.4 l-5.6 2.4 l-2.4 5.6 l-2.4 -5.6 l-5.6 -2.4 Z" className="sage-spark" />
        </g>
      );
    case "thinking":
      return (
        <g>
          <Brows raise={4} />
          {openEyes(2, -2)}
          <circle cx="65" cy="88" r="3.4" className="sage-ink" />
          {cheeks}
          <circle cx="102" cy="36" r="2.6" className="sage-thought" />
          <circle cx="108" cy="26" r="3.4" className="sage-thought" />
          <circle cx="112" cy="14" r="4.4" className="sage-thought" />
        </g>
      );
    case "encourage":
      return (
        <g>
          <Brows />
          <path d="M41 57 Q47 51 53 57" className="sage-line" />
          <g className="sage-eyes">
            <ellipse cx="73" cy="56" rx="4.6" ry="6" className="sage-ink" />
            <circle cx="74.6" cy="53.6" r="1.7" className="sage-shine" />
          </g>
          {cheeks}
          <path d="M51 85 Q60 93 69 85" className="sage-line" />
        </g>
      );
    case "oops":
      return (
        <g>
          <Brows tilt={4} />
          {openEyes()}
          <path d="M51 88 Q55.5 84 60 88 Q64.5 92 69 88" className="sage-line" />
          <path d="M100 34 C103 39 103 43 100 44 C97 43 97 39 100 34 Z" className="sage-drop" />
        </g>
      );
    case "sleepy":
      return (
        <g>
          <Brows tilt={3} />
          <path d="M41 56 Q47 61 53 56" className="sage-line" />
          <path d="M67 56 Q73 61 79 56" className="sage-line" />
          <ellipse cx="60" cy="88" rx="3" ry="2.4" className="sage-ink" />
          {cheeks}
          <text x="96" y="34" className="sage-z">z</text>
          <text x="106" y="22" className="sage-z sage-z--small">z</text>
        </g>
      );
    default:
      return (
        <g>
          <Brows />
          {openEyes()}
          {cheeks}
          <path d="M51 85 Q60 94 69 85" className="sage-line" />
        </g>
      );
  }
}
