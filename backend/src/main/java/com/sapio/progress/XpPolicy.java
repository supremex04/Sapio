package com.sapio.progress;

import com.sapio.study.LearningActivityEvent;
import com.sapio.study.question.Verdict;

/**
 * XP rules (docs/specs/progress.md). Deliberately modest and tied to effortful learning actions,
 * never to merely opening the app (avoids the overjustification effect).
 */
public final class XpPolicy {

    public static final int DAILY_GOAL_REACHED = 20;

    private XpPolicy() {
    }

    public static int xpFor(LearningActivityEvent e) {
        return switch (e.type()) {
            case CHECK_ANSWERED -> e.firstCorrect() ? 10 : 0;
            case ASSESSMENT_ANSWERED -> e.verdict() == Verdict.CORRECT ? 10 : e.verdict() == Verdict.PARTIAL ? 5 : 0;
            case REVIEW_ANSWERED -> e.verdict() == Verdict.CORRECT ? 5 : e.verdict() == Verdict.PARTIAL ? 2 : 0;
            case TOPIC_COMPLETED -> 50;
            case ASSESSMENT_COMPLETED -> 25 + (e.score() != null && e.score() >= 0.8 ? 25 : 0);
            case LESSON_STARTED, TUTOR_QUESTION, ASSESSMENT_STARTED -> 0;
        };
    }
}
