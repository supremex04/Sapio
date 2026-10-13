import { cx } from "../ui/cx";
import "./sage.css";

/**
 * Sapi: Sapio's mascot, a small, kindly sage (white topknot and beard, purple robe) who carries the diya,
 * the lamp of knowledge. The flame is the mood barometer, the face does the rest.
 * An original character, hand-made SVG (ours). Colours come from tokens (--skin-*, --robe-*, --clay-*, --amber-*),
 * so it follows the theme. Use at most once per screen, at moments that matter. See docs/specs/design-system.md.
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

const FLAME_SCALE: Record<SageMood, number> = {
  happy: 1,
  cheer: 1.2,
  thinking: 0.95,
  encourage: 1.05,
  oops: 0.8,
  sleepy: 0.5,
};

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
      <ellipse cx="58" cy="118" rx="36" ry="4.5" className="sage-shadow" />

      {/* Robe and sash */}
      <path d="M22 117 C22 95 32 79 58 79 C84 79 94 95 94 117 Z" className="sage-robe" />
      <path d="M22 109 C40 114 76 114 94 109 L94 117 L22 117 Z" className="sage-robe-edge" />
      <path d="M36 86 L44 82 L76 116 L64 117 Z" className="sage-sash" />

      {/* Head */}
      <circle cx="31" cy="52" r="5.5" className="sage-skin-edge" />
      <circle cx="85" cy="52" r="5.5" className="sage-skin-edge" />
      <circle cx="58" cy="50" r="27" className="sage-skin" />

      {/* Hair: cap and topknot */}
      <circle cx="58" cy="17" r="9" className="sage-hair" />
      <path d="M31 46 C30 30 42 23 58 23 C74 23 86 30 85 46 C79 36 70 32 58 32 C46 32 37 36 31 46 Z" className="sage-hair" />
      <rect x="51.5" y="21" width="13" height="4.5" rx="2.25" className="sage-band" />

      {/* Beard and moustache */}
      <path d="M33 57 C38 64 48 67 58 67 C68 67 78 64 83 57 C86 76 74 94 58 99 C42 94 30 76 33 57 Z" className="sage-hair" />
      <path d="M58 62 C53 57.5 45 58 42 63 C49 67 54 66.5 58 62 C62 66.5 67 67 74 63 C71 58 63 57.5 58 62 Z" className="sage-hair sage-moustache" />
      <ellipse cx="58" cy="56.5" rx="3.6" ry="2.8" className="sage-nose" />

      {/* The lamp, held out in one hand */}
      <g className="sage-flame-wrap" style={{ transform: `scale(${FLAME_SCALE[mood]})` }}>
        <g className="sage-flame">
          <path d="M100 52 C108 61 111 68 107.5 75 C105 79.5 95 79.5 92.5 75 C89 68 92 61 100 52 Z" className="sage-flame-outer" />
          <path d="M100 63 C104 68 105 72 103 75.5 C101.5 77.5 98.5 77.5 97 75.5 C95 72 96 68 100 63 Z" className="sage-flame-inner" />
          <ellipse cx="100" cy="74.5" rx="1.8" ry="2.6" className="sage-flame-core" />
        </g>
      </g>
      <rect x="99" y="76" width="2.4" height="7" rx="1.2" className="sage-wick" />
      <path d="M85 84 C85 95 92 100 100 100 C108 100 115 95 115 84 Z" className="sage-lamp" />
      <ellipse cx="100" cy="84" rx="15" ry="3.8" className="sage-lamp-edge" />
      <ellipse cx="100" cy="83.6" rx="11.5" ry="2.2" className="sage-oil" />
      <circle cx="94" cy="100" r="6.5" className="sage-skin" />

      <Face mood={mood} />
    </svg>
  );
}

/** Eyebrows: fluffy and white, they carry half the expression. */
function Brows({ tilt = 0, raise = 0 }: { tilt?: number; raise?: number }) {
  return (
    <g className="sage-brows">
      <path d={`M39 ${41 + tilt} Q46 ${35 - tilt} 53 ${40 - tilt}`} className="sage-brow" />
      <path d={`M63 ${40 - tilt - raise} Q70 ${35 - tilt - raise} 77 ${41 + tilt - raise}`} className="sage-brow" />
    </g>
  );
}

function Face({ mood }: { mood: SageMood }) {
  const cheeks = (
    <>
      <ellipse cx="40" cy="57" rx="5" ry="3" className="sage-cheek" />
      <ellipse cx="76" cy="57" rx="5" ry="3" className="sage-cheek" />
    </>
  );
  const openEyes = (dx = 0, dy = 0) => (
    <g className="sage-eyes">
      <ellipse cx={47 + dx} cy={49 + dy} rx="3.6" ry="4.8" className="sage-ink" />
      <ellipse cx={69 + dx} cy={49 + dy} rx="3.6" ry="4.8" className="sage-ink" />
      <circle cx={48.3 + dx} cy={47.2 + dy} r="1.4" className="sage-shine" />
      <circle cx={70.3 + dx} cy={47.2 + dy} r="1.4" className="sage-shine" />
    </g>
  );
  switch (mood) {
    case "cheer":
      return (
        <g>
          <Brows tilt={-1} />
          <path d="M42 50 Q47 44 52 50" className="sage-line" />
          <path d="M64 50 Q69 44 74 50" className="sage-line" />
          {cheeks}
          <path d="M50 68 Q58 81 66 68 Z" className="sage-ink" />
          <path d="M53.5 74 Q58 78 62.5 74 Q58 71.5 53.5 74 Z" className="sage-tongue" />
          <path d="M12 30 l3 -7 l3 7 l7 3 l-7 3 l-3 7 l-3 -7 l-7 -3 Z" className="sage-spark" />
          <path d="M26 10 l2.4 -5.6 l2.4 5.6 l5.6 2.4 l-5.6 2.4 l-2.4 5.6 l-2.4 -5.6 l-5.6 -2.4 Z" className="sage-spark" />
        </g>
      );
    case "thinking":
      return (
        <g>
          <Brows raise={3} />
          {openEyes(2, -1.5)}
          <circle cx="62" cy="71" r="2.8" className="sage-ink" />
          {cheeks}
        </g>
      );
    case "encourage":
      return (
        <g>
          <Brows />
          <path d="M42 50 Q47 45 52 50" className="sage-line" />
          <g className="sage-eyes">
            <ellipse cx="69" cy="49" rx="3.6" ry="4.8" className="sage-ink" />
            <circle cx="70.3" cy="47.2" r="1.4" className="sage-shine" />
          </g>
          {cheeks}
          <path d="M51 69 Q58 76 65 69" className="sage-line" />
        </g>
      );
    case "oops":
      return (
        <g>
          <Brows tilt={3} />
          {openEyes()}
          <path d="M51 72 Q54.5 69 58 72 Q61.5 75 65 72" className="sage-line" />
          <path d="M84 30 C87 35 87 39 84 40 C81 39 81 35 84 30 Z" className="sage-drop" />
        </g>
      );
    case "sleepy":
      return (
        <g>
          <Brows tilt={2} />
          <path d="M42 50 Q47 54 52 50" className="sage-line" />
          <path d="M64 50 Q69 54 74 50" className="sage-line" />
          <ellipse cx="58" cy="71" rx="2.6" ry="2" className="sage-ink" />
          {cheeks}
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
          <path d="M51 69 Q58 77 65 69" className="sage-line" />
        </g>
      );
  }
}
