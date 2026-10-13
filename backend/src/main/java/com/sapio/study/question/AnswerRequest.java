package com.sapio.study.question;

import jakarta.validation.constraints.Size;

/** Exactly one of optionIndex (choice questions) or text (short answer) is expected. */
public record AnswerRequest(Integer optionIndex, @Size(max = 4000) String text, Confidence confidence) {
}
