import { useEffect, useState } from "react";
import { useLocation } from "react-router";
import { studyApi } from "../features/study/api";
import type { ReviewSummary } from "../features/study/types";

/** Due-review counts for the shell (refetched on navigation, so they stay current after answering). */
export function useReviewSummary(): ReviewSummary | null {
  const [summary, setSummary] = useState<ReviewSummary | null>(null);
  const { pathname } = useLocation();
  useEffect(() => {
    let alive = true;
    studyApi.reviewSummary().then(
      (s) => alive && setSummary(s),
      () => undefined,
    );
    return () => {
      alive = false;
    };
  }, [pathname]);
  return summary;
}
