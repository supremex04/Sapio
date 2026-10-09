import { apiJson, apiRequest } from "../../shared/api/http";
import type { ActivityItem, DailyActivity, GoalMinutes, ProgressSummary } from "./types";

export const progressApi = {
  summary: () => apiJson<ProgressSummary>("/progress/summary"),
  daily: (days = 14) => apiJson<{ days: DailyActivity[] }>(`/progress/daily?days=${days}`),
  activity: (limit = 20) => apiJson<{ items: ActivityItem[] }>(`/progress/activity?limit=${limit}`),
  setGoal: (dailyMinutes: GoalMinutes) => apiJson<{ dailyMinutes: number }>("/progress/goal", { method: "PUT", body: { dailyMinutes } }),
  heartbeat: (timeZone: string | undefined) => apiRequest("/progress/heartbeat", { method: "POST", body: { timeZone } }),
};
