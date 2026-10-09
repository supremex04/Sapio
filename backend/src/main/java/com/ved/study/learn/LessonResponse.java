package com.ved.study.learn;

import java.util.List;
import java.util.UUID;

import com.ved.study.question.AnswerResult;
import com.ved.study.question.QuestionType;

public record LessonResponse(UUID topicId, String title, List<LessonSection> sections, List<String> keyTakeaways,
                             List<Check> checks, TopicStatus status) {

    /** A comprehension check without its answer key; {@code lastAnswer} is the caller's latest attempt. */
    public record Check(UUID id, int afterSection, QuestionType type, String prompt, List<String> options,
                        AnswerResult lastAnswer) {
    }
}
