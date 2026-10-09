/** Ved mark: an open book with a small "spark" of insight. */
export function Logo({ size = 30 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" aria-hidden className="logo-mark">
      <defs>
        <linearGradient id="ved-logo-g" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#7c6cf0" />
          <stop offset="1" stopColor="#4b3bc9" />
        </linearGradient>
      </defs>
      <rect width="32" height="32" rx="9" fill="url(#ved-logo-g)" />
      <path d="M9 10.5c2.6-.9 5-.6 7 1v11c-2-1.6-4.4-1.9-7-1z" fill="#fff" opacity=".95" />
      <path d="M23 10.5c-2.6-.9-5-.6-7 1v11c2-1.6 4.4-1.9 7-1z" fill="#fff" opacity=".7" />
      <circle cx="23.5" cy="8.5" r="2.5" fill="#ffb938" />
    </svg>
  );
}
