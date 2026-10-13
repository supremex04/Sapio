import { cx } from "../ui/cx";
import "./sage.css";

/**
 * Sapi: Sapio's mascot, a small sage who is mostly beard. A topknot, a face peeking out between bushy brows
 * and a long flowing beard, with a clay robe hem and two feet underneath. The brows and eyes carry the mood.
 * An original character, hand-made SVG (ours). Colours come from tokens (--skin-*, --hair-*, --clay-*), so it
 * follows the theme. Use at most once per screen, at moments that matter. See docs/specs/design-system.md.
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
      <ellipse cx="60" cy="119" rx="26" ry="3.5" className="sage-shadow" />

      <g className="sage-body">
        {/* Robe hem and feet, peeking out under the beard */}
        <ellipse cx="50" cy="116" rx="7" ry="3.6" className="sage-foot" />
        <ellipse cx="70" cy="116" rx="7" ry="3.6" className="sage-foot" />
        <path d="M40 100 C40 110 47 115 60 115 C73 115 80 110 80 100 Z" className="sage-robe" />

        {/* Head and topknot */}
        <circle cx="60" cy="14" r="7.5" className="sage-hair" />
        <rect x="54.5" y="18.5" width="11" height="4" rx="2" className="sage-band" />
        <circle cx="38.5" cy="46" r="4.2" className="sage-skin-edge" />
        <circle cx="81.5" cy="46" r="4.2" className="sage-skin-edge" />
        <ellipse cx="60" cy="44" rx="21" ry="20" className="sage-skin" />
        <path d="M39 42 C39 30 48 23 60 23 C72 23 81 30 81 42 C76 35 69 32 60 32 C51 32 44 35 39 42 Z" className="sage-hair" />

        {/* The beard is the body */}
        <path
          d="M40 48 C33 64 33 86 44 100 C49 106 55 110 60 113 C65 110 71 106 76 100 C87 86 87 64 80 48 C75 57 68 61 60 61 C52 61 45 57 40 48 Z"
          className="sage-hair"
        />
        <path d="M51 74 C49 86 52 96 57 104 M69 74 C71 86 68 96 63 104 M60 80 V100" className="sage-strand" />
        <path d="M60 55 C56 52 50 52.5 47 56.5 C52 59.5 57 59 60 55 C63 59 68 59.5 73 56.5 C70 52.5 64 52 60 55 Z" className="sage-hair" />
        <ellipse cx="60" cy="52" rx="2.8" ry="2.2" className="sage-nose" />
      </g>

      <Face mood={mood} />
    </svg>
  );
}

/** Bushy white brows that droop outward: half of every expression. */
function Brows({ tilt = 0, raise = 0 }: { tilt?: number; raise?: number }) {
  return (
    <g>
      <path d={`M42 ${41 + tilt} Q48 ${35.5 - tilt} 55 ${38 - tilt}`} className="sage-brow" />
      <path d={`M65 ${38 - tilt - raise} Q72 ${35.5 - tilt - raise} 78 ${41 + tilt - raise}`} className="sage-brow" />
    </g>
  );
}

function Face({ mood }: { mood: SageMood }) {
  const cheeks = (
    <>
      <ellipse cx="46" cy="50" rx="3.6" ry="2.2" className="sage-cheek" />
      <ellipse cx="74" cy="50" rx="3.6" ry="2.2" className="sage-cheek" />
    </>
  );
  const openEyes = (dx = 0, dy = 0) => (
    <g className="sage-eyes">
      <ellipse cx={51.5 + dx} cy={45 + dy} rx="2.5" ry="3.2" className="sage-ink" />
      <ellipse cx={68.5 + dx} cy={45 + dy} rx="2.5" ry="3.2" className="sage-ink" />
      <circle cx={52.3 + dx} cy={43.9 + dy} r="0.9" className="sage-shine" />
      <circle cx={69.3 + dx} cy={43.9 + dy} r="0.9" className="sage-shine" />
    </g>
  );
  switch (mood) {
    case "cheer":
      return (
        <g>
          <Brows tilt={-1.5} />
          <path d="M48 46 Q51.5 42 55 46" className="sage-line" />
          <path d="M65 46 Q68.5 42 72 46" className="sage-line" />
          {cheeks}
          <path d="M55 62 Q60 69 65 62 Z" className="sage-ink" />
          <path d="M20 34 l2.4 -5.6 l2.4 5.6 l5.6 2.4 l-5.6 2.4 l-2.4 5.6 l-2.4 -5.6 l-5.6 -2.4 Z" className="sage-spark" />
          <path d="M96 22 l1.8 -4.2 l1.8 4.2 l4.2 1.8 l-4.2 1.8 l-1.8 4.2 l-1.8 -4.2 l-4.2 -1.8 Z" className="sage-spark" />
        </g>
      );
    case "thinking":
      return (
        <g>
          <Brows raise={2.5} />
          {openEyes(1.5, -1)}
          <circle cx="63" cy="63.5" r="1.9" className="sage-ink" />
          <circle cx="90" cy="34" r="2" className="sage-thought" />
          <circle cx="96" cy="26" r="2.8" className="sage-thought" />
          <circle cx="104" cy="16" r="3.6" className="sage-thought" />
        </g>
      );
    case "encourage":
      return (
        <g>
          <Brows />
          <path d="M48 46 Q51.5 43 55 46" className="sage-line" />
          <g className="sage-eyes">
            <ellipse cx="68.5" cy="45" rx="2.5" ry="3.2" className="sage-ink" />
            <circle cx="69.3" cy="43.9" r="0.9" className="sage-shine" />
          </g>
          {cheeks}
          <path d="M55.5 62.5 Q60 66.5 64.5 62.5" className="sage-line" />
        </g>
      );
    case "oops":
      return (
        <g>
          <Brows tilt={2.5} />
          {openEyes()}
          <path d="M55 64 Q57.5 62 60 64 Q62.5 66 65 64" className="sage-line" />
          <path d="M86 28 C88.5 32 88.5 35 86 36 C83.5 35 83.5 32 86 28 Z" className="sage-drop" />
        </g>
      );
    case "sleepy":
      return (
        <g>
          <Brows tilt={2} />
          <path d="M48 45.5 Q51.5 48.5 55 45.5" className="sage-line" />
          <path d="M65 45.5 Q68.5 48.5 72 45.5" className="sage-line" />
          <text x="88" y="30" className="sage-z">z</text>
          <text x="98" y="18" className="sage-z sage-z--small">z</text>
        </g>
      );
    default:
      return (
        <g>
          <Brows />
          {openEyes()}
          {cheeks}
          <path d="M55.5 62.5 Q60 67 64.5 62.5" className="sage-line" />
        </g>
      );
  }
}
