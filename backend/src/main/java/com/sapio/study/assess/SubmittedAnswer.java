package com.sapio.study.assess;

import com.sapio.study.question.Confidence;
import com.sapio.study.question.QuestionAttempt;

/**
 * What the learner answered on a test item, without the verdict: tests reveal results only in the report, so
 * nothing about correctness leaves the server while the test is open.
 */
public record SubmittedAnswer(Integer selectedOptionIndex, String answerText, Confidence confidence) {

    static SubmittedAnswer of(QuestionAttempt attempt) {
        return new SubmittedAnswer(attempt.getSelectedOption(), attempt.getAnswerText(), attempt.getConfidence());
    }
}
