package com.ved.study.question;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Leitner spaced-repetition schedule: boxes 1..5 with intervals 1, 3, 7, 16 and 35 days.
 * Correct moves a card up a box, partial keeps it, incorrect sends it back to box 1, due again in
 * 10 minutes (re-test soon after an error). See docs/architecture/learning-science.md.
 */
@Component
public class ReviewSchedule {

    static final List<Duration> INTERVALS = List.of(
            Duration.ofDays(1), Duration.ofDays(3), Duration.ofDays(7), Duration.ofDays(16), Duration.ofDays(35));
    static final Duration RELEARN_DELAY = Duration.ofMinutes(10);

    public record Slot(int box, Instant dueAt) {
    }

    /** Placement after the first time a learner answers a question. */
    public Slot first(Verdict verdict, Instant now) {
        return verdict == Verdict.INCORRECT ? new Slot(1, now.plus(RELEARN_DELAY)) : new Slot(1, due(1, now));
    }

    /** Placement after a spaced review of a card currently in {@code box}. */
    public Slot next(int box, Verdict verdict, Instant now) {
        return switch (verdict) {
            case CORRECT -> {
                int up = Math.min(box + 1, INTERVALS.size());
                yield new Slot(up, due(up, now));
            }
            case PARTIAL -> new Slot(box, due(box, now));
            case INCORRECT -> new Slot(1, now.plus(RELEARN_DELAY));
        };
    }

    private static Instant due(int box, Instant now) {
        return now.plus(INTERVALS.get(box - 1));
    }
}
