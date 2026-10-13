/** Sapio mark: Sapi, the sage mascot (topknot, face, round beard) on a clay tile. */
export function Logo({ size = 32 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden className="logo-mark">
      <rect x="1" y="2.5" width="30" height="28.5" rx="8" fill="var(--clay-edge)" />
      <rect x="1" y="1" width="30" height="28" rx="8" fill="var(--clay-main)" />
      <circle cx="16" cy="5.6" r="2.6" fill="#fff" />
      <ellipse cx="16" cy="15" rx="8.6" ry="8" fill="var(--skin-main)" />
      <path d="M7.4 14C7 9.6 10.8 7.6 16 7.6s9 2 8.6 6.4c-1.9-2.8-4.8-3.9-8.6-3.9S9.3 11.2 7.4 14z" fill="#fff" />
      <path d="M7.7 16.6c1.4 2.9 4.5 3.8 8.3 3.8s6.9-.9 8.3-3.8c.9 6.7-3.1 10.9-8.3 11.4-5.2-.5-9.2-4.7-8.3-11.4z" fill="#fff" />
      <circle cx="13" cy="14.6" r="1.2" fill="#2a1a10" />
      <circle cx="19" cy="14.6" r="1.2" fill="#2a1a10" />
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
