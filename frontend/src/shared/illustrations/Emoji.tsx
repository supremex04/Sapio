import type { CSSProperties } from "react";
import type { EmojiName } from "./emojiNames";

// Each SVG is its own small file, so only the emoji a screen uses are downloaded (and then cached).
const files = import.meta.glob<string>("./emoji/*.svg", { eager: true, query: "?url", import: "default" });
const URLS: Record<string, string> = Object.fromEntries(
  Object.entries(files).map(([path, url]) => [path.slice("./emoji/".length, -".svg".length), url]),
);

interface Props {
  name: EmojiName;
  size?: number;
  /** Accessible label. Omit when the emoji is decorative (next to text that says the same). */
  label?: string;
  className?: string;
  style?: CSSProperties;
}

/** Fluent Emoji (Microsoft, MIT): Ved's icons for places, rewards and feelings. */
export function Emoji({ name, size = 24, label, className, style }: Props) {
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
