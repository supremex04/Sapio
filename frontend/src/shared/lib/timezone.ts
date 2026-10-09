/** The browser's IANA time zone (e.g. "Asia/Kathmandu"), or undefined if unavailable. */
export function browserTimeZone(): string | undefined {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || undefined;
  } catch {
    return undefined;
  }
}
