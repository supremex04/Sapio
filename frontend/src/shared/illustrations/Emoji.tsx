import type { CSSProperties } from "react";
import type { EmojiName } from "./emojiNames";
import { Glyph, isGlyph, type GlyphName } from "./glyphs";

// Each SVG is its own small file, so only the emoji a screen uses are downloaded (and then cached).
const files = import.meta.glob<string>("./emoji/*.svg", { eager: true, query: "?url", import: "default" });
const URLS: Record<string, string> = Object.fromEntries(
  Object.entries(files).map(([path, url]) => [path.slice("./emoji/".length, -".svg".length), url]),
);

/** A Fluent emoji, or one of Ved's own glyphs (see glyphs.tsx). */
export type IconName = EmojiName | GlyphName;

interface Props {
  name: IconName;
  size?: number;
  /** Accessible label. Omit when the emoji is decorative (next to text that says the same). */
  label?: string;
  className?: string;
  style?: CSSProperties;
}

/** Fluent Emoji (Microsoft, MIT) for places, rewards and feelings; Ved's own glyphs for core concepts. */
export function Emoji({ name, size = 24, label, className, style }: Props) {
  if (isGlyph(name)) {
    return (
      <span className={className ? `emoji ${className}` : "emoji"} style={{ display: "inline-flex", ...style }}>
        <Glyph name={name} size={size} label={label} />
      </span>
    );
  }
  return (
    <img
      src={URLS[name]}
      width={size}
      height={size}
      alt={label ?? ""}
      aria-hidden={label ? undefined : true}
      draggable={false}
      className={className ? `emoji ${className}` : "emoji"}
      style={style}
    />
  );
}
