/** Sapio mark: a flat diya with its flame (the lamp of knowledge, which Sapi, the mascot, carries). */
export function Logo({ size = 32 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden className="logo-mark">
      <path d="M16 2.5c4.2 4.6 6 8.3 4 11.4-1.4 2.1-6.6 2.1-8 0-2-3.1-.2-6.8 4-11.4z" fill="var(--amber-main)" />
      <path d="M16 8.5c1.9 2.3 2.5 4.3 1.6 5.8-.6 1-2.6 1-3.2 0-.9-1.5-.3-3.5 1.6-5.8z" fill="#ffe066" />
      <path d="M3 16.5h26c0 7-5.8 12-13 12S3 23.5 3 16.5z" fill="var(--clay-main)" />
      <path d="M5.2 21.5c2.4 4 6.3 6 10.8 6s8.4-2 10.8-6c-2.9 2.4-6.6 3.5-10.8 3.5S8.1 23.9 5.2 21.5z" fill="var(--clay-edge)" />
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
