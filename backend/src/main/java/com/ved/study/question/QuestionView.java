package com.ved.study.question;

import java.util.List;
import java.util.UUID;

/** A question as shown before answering: no answer key. */
public record QuestionView(UUID id, QuestionType type, String prompt, List<String> options, UUID topicId,
                           String topicTitle) {

    public static QuestionView of(StudyQuestion q, String topicTitle) {
        return new QuestionView(q.getId(), q.getType(), q.getPrompt(), q.getOptions(), q.getTopicId(), topicTitle);
    }
}
