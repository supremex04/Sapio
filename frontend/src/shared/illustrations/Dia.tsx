import { cx } from "../ui/cx";
import "./dia.css";

/**
 * Dia: Ved's mascot, a little clay diya (the lamp of knowledge) whose flame is its mood.
 * Hand-made SVG (ours). Colours come from tokens (--clay-*, --amber-*), so it follows the theme.
 * Use at most once per screen, at moments that matter. See docs/specs/design-system.md.
 */
export type DiaMood = "happy" | "cheer" | "thinking" | "encourage" | "oops" | "sleepy";

interface Props {
  mood?: DiaMood;
  size?: number;
  /** Accessible description; omit when decorative. */
  label?: string;
  className?: string;
  /** Disable idle animation (e.g. in dense lists). */
  still?: boolean;
}

const FLAME_SCALE: Record<DiaMood, number> = {
  happy: 1,
  cheer: 1.18,
  thinking: 0.95,
  encourage: 1.05,
  oops: 0.82,
  sleepy: 0.55,
};

export function Dia({ mood = "happy", size = 96, label, className, still }: Props) {
  return (
    <svg
      viewBox="0 0 120 124"
      width={size}
      height={size * (124 / 120)}
      className={cx("dia", `dia--${mood}`, still && "dia--still", className)}
      role={label ? "img" : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    >
      <ellipse cx="60" cy="118" rx="32" ry="4.5" className="dia-shadow" />

      {/* Flame: the mood barometer */}
      <g className="dia-flame-wrap" style={{ transform: `scale(${FLAME_SCALE[mood]})` }}>
        <g className="dia-flame">
          <path d="M60 6 C75 25 83 39 75 52 C70 60 50 60 45 52 C37 39 45 25 60 6 Z" className="dia-flame-outer" />
          <path d="M60 25 C68 36 70 45 66 52 C63 56 57 56 54 52 C50 45 52 36 60 25 Z" className="dia-flame-inner" />
          <ellipse cx="60" cy="49" rx="3.2" ry="5" className="dia-flame-core" />
        </g>
      </g>
      <rect x="58" y="52" width="4" height="8" rx="2" className="dia-wick" />

      {/* Lamp body */}
      <ellipse cx="44" cy="111" rx="9" ry="4.5" className="dia-edge" />
      <ellipse cx="76" cy="111" rx="9" ry="4.5" className="dia-edge" />
      <path d="M10 62 C10 95 35 109 60 109 C85 109 110 95 110 62 Z" className="dia-body" />
      <path d="M14 80 C22 100 42 109 60 109 C78 109 98 100 106 80 C94 96 77 101 60 101 C43 101 26 96 14 80 Z" className="dia-edge" />
      <ellipse cx="60" cy="62" rx="50" ry="11" className="dia-edge" />
      <ellipse cx="60" cy="60.5" rx="42" ry="7" className="dia-oil" />
      {/* rangoli dots */}
      <circle cx="22" cy="72" r="2.2" className="dia-dot" />
      <circle cx="30" cy="76" r="1.6" className="dia-dot" />
      <circle cx="98" cy="72" r="2.2" className="dia-dot" />
      <circle cx="90" cy="76" r="1.6" className="dia-dot" />

      <Face mood={mood} />
    </svg>
  );
}

function Face({ mood }: { mood: DiaMood }) {
  const cheeks = (
    <>
      <ellipse cx="36" cy="90" rx="6" ry="3.5" className="dia-cheek" />
      <ellipse cx="84" cy="90" rx="6" ry="3.5" className="dia-cheek" />
    </>
  );
  const openEyes = (dx = 0, dy = 0) => (
    <g className="dia-eyes">
      <ellipse cx={46 + dx} cy={81 + dy} rx="4.6" ry="6" className="dia-ink" />
      <ellipse cx={74 + dx} cy={81 + dy} rx="4.6" ry="6" className="dia-ink" />
      <circle cx={47.6 + dx} cy={78.6 + dy} r="1.7" className="dia-shine" />
      <circle cx={75.6 + dx} cy={78.6 + dy} r="1.7" className="dia-shine" />
    </g>
  );
  switch (mood) {
    case "cheer":
      return (
        <g>
          <path d="M40 82 Q46 74 52 82" className="dia-line" />
          <path d="M68 82 Q74 74 80 82" className="dia-line" />
          {cheeks}
          <path d="M49 88 Q60 103 71 88 Z" className="dia-ink" />
          <path d="M54 95 Q60 100 66 95 Q60 92 54 95 Z" className="dia-tongue" />
          <path d="M14 30 l3 -7 l3 7 l7 3 l-7 3 l-3 7 l-3 -7 l-7 -3 Z" className="dia-spark" />
          <path d="M100 22 l2.4 -5.6 l2.4 5.6 l5.6 2.4 l-5.6 2.4 l-2.4 5.6 l-2.4 -5.6 l-5.6 -2.4 Z" className="dia-spark" />
        </g>
      );
    case "thinking":
      return (
        <g>
          {openEyes(2, -2)}
          <path d="M66 70 Q73 66 80 69" className="dia-line dia-line--thin" />
          <circle cx="66" cy="93" r="3.4" className="dia-ink" />
          {cheeks}
        </g>
      );
    case "encourage":
      return (
        <g>
          <path d="M40 82 Q46 76 52 82" className="dia-line" />
          <g className="dia-eyes">
            <ellipse cx="74" cy="81" rx="4.6" ry="6" className="dia-ink" />
            <circle cx="75.6" cy="78.6" r="1.7" className="dia-shine" />
          </g>
          {cheeks}
          <path d="M50 89 Q60 98 70 89" className="dia-line" />
        </g>
      );
    case "oops":
      return (
        <g>
          {openEyes()}
          <path d="M39 72 L51 69" className="dia-line dia-line--thin" />
          <path d="M81 72 L69 69" className="dia-line dia-line--thin" />
          <path d="M51 93 Q55.5 89 60 93 Q64.5 97 69 93" className="dia-line" />
          <path d="M101 70 C104 75 104 79 101 80 C98 79 98 75 101 70 Z" className="dia-drop" />
        </g>
      );
    case "sleepy":
      return (
        <g>
          <path d="M40 81 Q46 85 52 81" className="dia-line" />
          <path d="M68 81 Q74 85 80 81" className="dia-line" />
          <ellipse cx="60" cy="93" rx="3" ry="2.4" className="dia-ink" />
          {cheeks}
          <text x="88" y="40" className="dia-z">z</text>
          <text x="98" y="28" className="dia-z dia-z--small">z</text>
        </g>
      );
    default:
      return (
        <g>
          {openEyes()}
          {cheeks}
          <path d="M50 89 Q60 99 70 89" className="dia-line" />
        </g>
      );
  }
}
