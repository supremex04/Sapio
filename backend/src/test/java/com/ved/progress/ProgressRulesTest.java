package com.ved.progress;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressRulesTest {

    @Test
    void levelCurve() {
        assertThat(LevelCurve.levelFor(0)).isEqualTo(1);
        assertThat(LevelCurve.levelFor(99)).isEqualTo(1);
        assertThat(LevelCurve.levelFor(100)).isEqualTo(2);
        assertThat(LevelCurve.levelFor(299)).isEqualTo(2);
        assertThat(LevelCurve.levelFor(1000)).isEqualTo(5);
        assertThat(LevelCurve.xpToReach(4)).isEqualTo(600);
    }

    @Test
    void streakIsForgivingAboutToday() {
        LocalDate today = LocalDate.of(2026, 10, 9);
        List<LocalDate> days = List.of(today.minusDays(1), today.minusDays(2), today.minusDays(5),
                today.minusDays(6), today.minusDays(7), today.minusDays(8));

        Streaks.Result notYetToday = Streaks.compute(days, today);
        assertThat(notYetToday.current()).isEqualTo(2); // yesterday's streak is still alive
        assertThat(notYetToday.activeToday()).isFalse();
        assertThat(notYetToday.longest()).isEqualTo(4);

        Streaks.Result withToday = Streaks.compute(
                java.util.stream.Stream.concat(days.stream(), java.util.stream.Stream.of(today)).toList(), today);
        assertThat(withToday.current()).isEqualTo(3);
        assertThat(Streaks.compute(List.of(today.minusDays(3)), today).current()).isZero();
    }
}
