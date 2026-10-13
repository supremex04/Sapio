const key = (userId: string) => `sapio-onboarded-${userId}`;

/** Whether the one-time "daily goal" onboarding was shown to this user on this device. */
export function isOnboarded(userId: string): boolean {
  try {
    return localStorage.getItem(key(userId)) === "1";
  } catch {
    return true; // storage blocked: never nag
  }
}

export function markOnboarded(userId: string): void {
  try {
    localStorage.setItem(key(userId), "1");
  } catch {
    // ignore
  }
}
