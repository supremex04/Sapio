package com.ved.study.overview;

import java.util.UUID;

public record NextStep(Kind kind, UUID documentId, UUID topicId, String title, String reason, int estimatedMinutes) {

    public enum Kind {
        REVIEW,
        CONTINUE_TOPIC,
        START_TOPIC,
        TAKE_TEST,
        UPLOAD
    }
}
