import type { CSSProperties } from "react";

export function Skeleton({ width = "100%", height = "1rem", radius, style }: { width?: string | number; height?: string | number; radius?: string; style?: CSSProperties }) {
  return <div className="skeleton" aria-hidden style={{ width, height, borderRadius: radius, ...style }} />;
}

export function SkeletonText({ lines = 3 }: { lines?: number }) {
  return (
    <div className="stack-sm" aria-hidden>
      {Array.from({ length: lines }, (_, i) => (
        <Skeleton key={i} width={i === lines - 1 ? "60%" : "100%"} />
      ))}
    </div>
  );
}
