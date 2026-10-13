package com.sapio.study.learn;

import java.time.Instant;
import java.util.UUID;

public record TopicProgressResponse(UUID topicId, TopicStatus status, Instant completedAt) {
}
