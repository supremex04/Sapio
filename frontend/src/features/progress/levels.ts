/** XP needed in total to reach `level` (spec: 50·n·(n−1)). */
export function xpForLevel(level: number): number {
  return 50 * level * (level - 1);
}

/** Fraction (0..1) of the way from the current level's start to the next level. */
export function levelProgress(xp: { total: number; levelStartXp: number; nextLevelXp: number }): number {
  const span = xp.nextLevelXp - xp.levelStartXp;
  if (span <= 0) return 1;
  return Math.min(1, Math.max(0, (xp.total - xp.levelStartXp) / span));
}

/** Fraction of today's goal completed (0..1). */
export function goalProgress(goal: { dailyMinutes: number; todayMinutes: number }): number {
  if (goal.dailyMinutes <= 0) return 0;
  return Math.min(1, goal.todayMinutes / goal.dailyMinutes);
}

/** Encouraging, guilt-free streak copy (learning-science.md §2: loss aversion used carefully). */
export function streakMessage(streak: { current: number; activeToday: boolean }): string {
  if (streak.current === 0) return "Learn a little today to start a streak.";
  if (!streak.activeToday) return `${streak.current}-day streak. A few minutes today keeps it going.`;
  if (streak.current === 1) return "Day one. Every streak starts here.";
  return `${streak.current} days in a row. Nice rhythm!`;
}
