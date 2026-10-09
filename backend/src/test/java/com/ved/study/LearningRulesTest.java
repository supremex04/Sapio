package com.ved.study;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ved.study.learn.TopicStatus;
import com.ved.study.material.Mastery;
import com.ved.study.material.StudyTopic;
import com.ved.study.material.TopicDifficulty;
import com.ved.study.material.TopicRecommender;
import com.ved.study.question.ReviewSchedule;
import com.ved.study.question.TopicScore;
import com.ved.study.question.Verdict;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class LearningRulesTest {

    private final ReviewSchedule schedule = new ReviewSchedule();
    private final Instant now = Instant.parse("2026-10-09T10:00:00Z");

    @Test
    void leitnerScheduleSpacesCorrectAnswersAndRelearnsMistakesSoon() {
        assertThat(schedule.first(Verdict.CORRECT, now)).isEqualTo(new ReviewSchedule.Slot(1, now.plus(Duration.ofDays(1))));
        assertThat(schedule.first(Verdict.INCORRECT, now).dueAt()).isEqualTo(now.plus(Duration.ofMinutes(10)));
        assertThat(schedule.next(1, Verdict.CORRECT, now)).isEqualTo(new ReviewSchedule.Slot(2, now.plus(Duration.ofDays(3))));
        assertThat(schedule.next(4, Verdict.CORRECT, now)).isEqualTo(new ReviewSchedule.Slot(5, now.plus(Duration.ofDays(35))));
        assertThat(schedule.next(5, Verdict.CORRECT, now).box()).isEqualTo(5);
        assertThat(schedule.next(3, Verdict.PARTIAL, now)).isEqualTo(new ReviewSchedule.Slot(3, now.plus(Duration.ofDays(7))));
        assertThat(schedule.next(4, Verdict.INCORRECT, now).box()).isEqualTo(1);
    }

    @Test
    void masteryWeightsRecentAttemptsMore() {
        UUID topic = UUID.randomUUID();
        double improving = Mastery.byTopic(List.of(new TopicScore(topic, 1), new TopicScore(topic, 0))).get(topic);
        double slipping = Mastery.byTopic(List.of(new TopicScore(topic, 0), new TopicScore(topic, 1))).get(topic);
        assertThat(improving).isGreaterThan(0.5).isGreaterThan(slipping);
    }

    @Test
    void recommenderPrefersUnfinishedThenNewThenWeakest() {
        StudyTopic a = topic(1);
        StudyTopic b = topic(2);
        StudyTopic c = topic(3);
        List<StudyTopic> path = List.of(a, b, c);

        assertThat(TopicRecommender.recommend(path, Map.of(), Map.of())).contains(a.getId());
        assertThat(TopicRecommender.recommend(path, Map.of(a.getId(), TopicStatus.COMPLETED,
                c.getId(), TopicStatus.IN_PROGRESS), Map.of())).contains(c.getId());
        Map<UUID, TopicStatus> allDone = Map.of(a.getId(), TopicStatus.COMPLETED, b.getId(), TopicStatus.COMPLETED,
                c.getId(), TopicStatus.COMPLETED);
        assertThat(TopicRecommender.recommend(path, allDone, Map.of(a.getId(), 0.9, b.getId(), 0.4, c.getId(), 0.6)))
                .contains(b.getId());
        assertThat(TopicRecommender.recommend(path, allDone, Map.of(a.getId(), 0.9, b.getId(), 0.85, c.getId(), 0.95)))
                .isEmpty();
    }

    private static StudyTopic topic(int position) {
        StudyTopic t = new StudyTopic(UUID.randomUUID(), position, "T" + position, "s", List.of(),
                TopicDifficulty.CORE, List.of(1));
        ReflectionTestUtils.setField(t, "id", UUID.randomUUID());
        return t;
    }
}
