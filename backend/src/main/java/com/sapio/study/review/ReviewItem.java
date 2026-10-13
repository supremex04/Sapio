package com.sapio.study.review;

import java.time.Instant;
import java.util.UUID;

import com.sapio.study.question.QuestionView;

public record ReviewItem(QuestionView question, UUID documentId, String documentTitle, int box, Instant dueAt) {
}
