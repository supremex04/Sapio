import { useAuth } from "../auth/AuthContext";

/** Teachers get the classroom (students, class tests, insights) instead of the learner's XP, recall and streaks. */
export function useIsTeacher(): boolean {
  return useAuth().user?.role === "TEACHER";
}
