package com.sapio.study.material;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.sapio.study.learn.TopicStatus;

/**
 * Picks the single most useful topic to study next (mastery learning, avoiding choice overload):
 * the first in-progress topic, else the first not started, else the weakest topic below mastery.
 */
public final class TopicRecommender {

    private TopicRecommender() {
    }

    public static Optional<UUID> recommend(List<StudyTopic> topicsInOrder, Map<UUID, TopicStatus> status,
                                           Map<UUID, Double> mastery) {
        for (TopicStatus wanted : List.of(TopicStatus.IN_PROGRESS, TopicStatus.NOT_STARTED)) {
            Optional<UUID> found = topicsInOrder.stream()
                    .filter(t -> status.getOrDefault(t.getId(), TopicStatus.NOT_STARTED) == wanted)
                    .map(StudyTopic::getId)
                    .findFirst();
            if (found.isPresent()) {
                return found;
            }
        }
        return topicsInOrder.stream()
                .filter(t -> mastery.getOrDefault(t.getId(), 0.0) < Mastery.MASTERED)
                .min((a, b) -> Double.compare(mastery.getOrDefault(a.getId(), 0.0),
                        mastery.getOrDefault(b.getId(), 0.0)))
                .map(StudyTopic::getId);
    }
}
