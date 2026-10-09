package com.ved.study.learn;

import java.util.UUID;

import jakarta.validation.Valid;

import com.ved.security.CurrentUserId;
import com.ved.study.question.AnswerRequest;
import com.ved.study.question.AnswerResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class LearnController {

    private final LessonService lessons;

    LearnController(LessonService lessons) {
        this.lessons = lessons;
    }

    @GetMapping("/api/topics/{topicId}/lesson")
    LessonResponse lesson(@CurrentUserId UUID userId, @PathVariable UUID topicId) {
        return lessons.getLesson(userId, topicId);
    }

    @PostMapping("/api/topics/{topicId}/complete")
    TopicProgressResponse complete(@CurrentUserId UUID userId, @PathVariable UUID topicId) {
        return lessons.complete(userId, topicId);
    }

    @PostMapping("/api/topics/{topicId}/ask")
    AskResponse ask(@CurrentUserId UUID userId, @PathVariable UUID topicId, @Valid @RequestBody AskRequest body) {
        return lessons.ask(userId, topicId, body);
    }

    @PostMapping("/api/questions/{questionId}/answer")
    AnswerResult answer(@CurrentUserId UUID userId, @PathVariable UUID questionId,
                        @Valid @RequestBody AnswerRequest body) {
        return lessons.answerCheck(userId, questionId, body);
    }
}
