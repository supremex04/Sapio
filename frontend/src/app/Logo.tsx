/** Sapio mark: Sapi, the sage mascot (topknot, face, flowing beard) on a clay tile. */
export function Logo({ size = 32 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden className="logo-mark">
      <rect x="1" y="2.5" width="30" height="28.5" rx="8" fill="var(--clay-edge)" />
      <rect x="1" y="1" width="30" height="28" rx="8" fill="var(--clay-main)" />
      <circle cx="16" cy="6.4" r="2.4" fill="#fff" />
      <ellipse cx="16" cy="12.8" rx="5.6" ry="5.4" fill="var(--skin-main)" />
      <path d="M10.4 11.8C10.4 8.8 12.8 7.2 16 7.2s5.6 1.6 5.6 4.6C20 10 18.2 9.4 16 9.4s-4 .6-5.6 2.4z" fill="#fff" />
      <path d="M10.6 13.6c-1.6 4.2-1.2 9 1.8 12 1.2 1.2 2.4 1.9 3.6 2.6 1.2-.7 2.4-1.4 3.6-2.6 3-3 3.4-7.8 1.8-12-1.4 2.2-3.2 3.2-5.4 3.2s-4-1-5.4-3.2z" fill="#fff" />
      <circle cx="14" cy="12.6" r=".9" fill="#2a1a10" />
      <circle cx="18" cy="12.6" r=".9" fill="#2a1a10" />
    </svg>
  );
}

export function Wordmark() {
  return (
    <span className="brand">
      <Logo />
      <span className="brand-name">sapio</span>
    </span>
  );
}
