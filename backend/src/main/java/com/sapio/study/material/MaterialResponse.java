package com.sapio.study.material;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.sapio.study.learn.TopicStatus;

public record MaterialResponse(UUID documentId, String title, String summary, String language, MaterialKind kind,
                               List<TopicView> topics, Stats stats) {

    public record TopicView(UUID id, int position, String title, String summary, List<String> keyConcepts,
                            TopicDifficulty difficulty, TopicStatus status, Instant completedAt, Double mastery,
                            boolean recommended) {
    }

    public record Stats(int topicCount, int completedTopics, long assessmentsCompleted, Double bestScore,
                        long reviewsDue) {
    }
}
