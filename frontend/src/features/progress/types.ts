/** Mirrors docs/specs/progress.md. */

export interface DailyActivity {
  date: string; // yyyy-mm-dd, user's local day
  activeSeconds: number;
  logins: number;
  lessonsCompleted: number;
  questionsAnswered: number;
  correctAnswers: number;
  xpEarned: number;
}

export interface ProgressSummary {
  xp: { total: number; level: number; levelStartXp: number; nextLevelXp: number };
  streak: { current: number; longest: number; activeToday: boolean };
  goal: { dailyMinutes: number; todayMinutes: number; reached: boolean };
  today: DailyActivity;
  totals: {
    activeSeconds: number;
    lessonsCompleted: number;
    questionsAnswered: number;
    correctAnswers: number;
    assessmentsCompleted: number;
  };
}

export interface ActivityItem {
  type: string;
  documentId: string | null;
  documentTitle: string | null;
  topicTitle: string | null;
  xp: number;
  occurredAt: string;
}

export const GOAL_OPTIONS = [5, 10, 15, 20, 30] as const;
export type GoalMinutes = (typeof GOAL_OPTIONS)[number];
