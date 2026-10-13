package com.sapio.study.review;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.sapio.security.CurrentUserId;
import com.sapio.study.question.AnswerRequest;
import com.sapio.study.question.AnswerResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
class ReviewController {

    private final ReviewService reviews;

    ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    record DueResponse(List<ReviewItem> items) {
    }

    @GetMapping("/summary")
    ReviewSummary summary(@CurrentUserId UUID userId) {
        return reviews.summary(userId);
    }

    @GetMapping("/due")
    DueResponse due(@CurrentUserId UUID userId, @RequestParam(defaultValue = "10") int limit,
                    @RequestParam(required = false) UUID documentId) {
        return new DueResponse(reviews.due(userId, limit, documentId));
    }

    @PostMapping("/{questionId}/answer")
    AnswerResult answer(@CurrentUserId UUID userId, @PathVariable UUID questionId,
                        @Valid @RequestBody AnswerRequest body) {
        return reviews.answer(userId, questionId, body);
    }
}
