package com.ved.study.assess;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.ved.security.CurrentUserId;
import com.ved.study.question.AnswerRequest;
import com.ved.study.question.AnswerResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AssessmentController {

    private final AssessmentService assessments;

    AssessmentController(AssessmentService assessments) {
        this.assessments = assessments;
    }

    @PostMapping("/api/documents/{documentId}/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    AssessmentResponse create(@CurrentUserId UUID userId, @PathVariable UUID documentId,
                              @Valid @RequestBody CreateAssessmentRequest body) {
        return assessments.create(userId, documentId, body);
    }

    @GetMapping("/api/documents/{documentId}/assessments")
    List<AssessmentSummary> list(@CurrentUserId UUID userId, @PathVariable UUID documentId) {
        return assessments.list(userId, documentId);
    }

    @GetMapping("/api/assessments/{id}")
    AssessmentResponse get(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return assessments.get(userId, id);
    }

    @PostMapping("/api/assessments/{id}/items/{position}/answer")
    AnswerResult answer(@CurrentUserId UUID userId, @PathVariable UUID id, @PathVariable int position,
                        @Valid @RequestBody AnswerRequest body) {
        return assessments.answer(userId, id, position, body);
    }

    @PostMapping("/api/assessments/{id}/complete")
    AssessmentResponse complete(@CurrentUserId UUID userId, @PathVariable UUID id) {
        return assessments.complete(userId, id);
    }
}
