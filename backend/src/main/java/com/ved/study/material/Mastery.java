package com.ved.study.material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ved.study.question.TopicScore;

/**
 * Mastery = recency-weighted mean of a learner's last {@value #WINDOW} scores on a topic
 * (weights 1, 0.85, 0.85², …): recent performance matters most, one slip doesn't erase progress.
 */
public final class Mastery {

    static final int WINDOW = 10;
    static final double DECAY = 0.85;
    public static final double MASTERED = 0.8;

    private Mastery() {
    }

    /** @param newestFirst scores ordered newest first */
    public static Map<UUID, Double> byTopic(List<TopicScore> newestFirst) {
        Map<UUID, List<Double>> recent = new HashMap<>();
        for (TopicScore s : newestFirst) {
            List<Double> scores = recent.computeIfAbsent(s.topicId(), k -> new ArrayList<>());
            if (scores.size() < WINDOW) {
                scores.add(s.score());
            }
        }
        Map<UUID, Double> mastery = new HashMap<>();
        recent.forEach((topic, scores) -> mastery.put(topic, weightedMean(scores)));
        return mastery;
    }

    static double weightedMean(List<Double> newestFirst) {
        double weight = 1;
        double sum = 0;
        double total = 0;
        for (double score : newestFirst) {
            sum += score * weight;
            total += weight;
            weight *= DECAY;
        }
        return total == 0 ? 0 : Math.round(sum / total * 1000) / 1000.0;
    }
}
