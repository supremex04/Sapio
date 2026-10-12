package com.ved.study.ai;

import java.time.Duration;

import com.ved.common.ai.AiException;

/** {@link StudyAi} failed. See {@link AiException} for how it is classified and rendered. */
public class StudyAiException extends AiException {

    public StudyAiException(String message, boolean inputRejected, boolean retryable, Duration retryAfter) {
        super(message, inputRejected, retryable, retryAfter);
    }

    /** A transient failure (busy, unavailable), or a rejected input. */
    public StudyAiException(String message, boolean inputRejected) {
        this(message, inputRejected, !inputRejected, null);
    }
}
