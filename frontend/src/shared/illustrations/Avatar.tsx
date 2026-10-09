import { useEffect, useState } from "react";

const BACKGROUNDS = ["c6e4ff", "d5f5dc", "ffe7c2", "ecdcff", "cdf3ef", "ffd9e8"];
const cache = new Map<string, string>();

/** The avatar library (~0.5 MB of artwork) loads on first use, outside the main bundle. */
async function render(seed: string): Promise<string> {
  const hit = cache.get(seed);
  if (hit) return hit;
  const [{ createAvatar }, notionists] = await Promise.all([import("@dicebear/core"), import("@dicebear/notionists")]);
  const uri = createAvatar(notionists, { seed, backgroundColor: BACKGROUNDS, radius: 50, scale: 110 }).toDataUri();
  cache.set(seed, uri);
  return uri;
}

/** Friendly generated avatar (DiceBear "notionists", CC0), stable per seed (use the user id). */
export function Avatar({ seed, size = 36, label }: { seed: string; size?: number; label?: string }) {
  const [uri, setUri] = useState(() => cache.get(seed) ?? null);
  useEffect(() => {
    let alive = true;
    render(seed).then(
      (u) => alive && setUri(u),
      () => undefined,
    );
    return () => {
      alive = false;
    };
  }, [seed]);
  const a11y = label ? { role: "img", "aria-label": label } : { "aria-hidden": true };
  return uri ? (
    <img src={uri} width={size} height={size} alt={label ?? ""} aria-hidden={label ? undefined : true} className="avatar" draggable={false} />
  ) : (
    <span className="avatar" style={{ display: "inline-block", width: size, height: size }} {...a11y} />
  );
}
