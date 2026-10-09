package com.ved.progress;

import java.time.LocalDate;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Streak = consecutive local days with learning activity. Forgiving by design: if today isn't active
 * yet, the streak still counts up to yesterday (no "you lost your streak" before the day is over).
 */
public final class Streaks {

    private Streaks() {
    }

    public record Result(int current, int longest, boolean activeToday) {
    }

    public static Result compute(List<LocalDate> activeDays, LocalDate today) {
        NavigableSet<LocalDate> days = new TreeSet<>(activeDays);
        boolean activeToday = days.contains(today);
        int current = 0;
        LocalDate cursor = activeToday ? today : today.minusDays(1);
        while (days.contains(cursor)) {
            current++;
            cursor = cursor.minusDays(1);
        }
        int longest = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate day : days) {
            run = previous != null && previous.plusDays(1).equals(day) ? run + 1 : 1;
            longest = Math.max(longest, run);
            previous = day;
        }
        return new Result(current, longest, activeToday);
    }
}
