package com.ved.study.ai;

import com.ved.common.error.ApplicationException;
import com.ved.common.error.ErrorCode;

/**
 * AI generation failed. Rendered as 503 {@code AI_UNAVAILABLE} when it escapes to a request; the
 * message is safe to show to users. {@link #isInputRejected()} marks failures caused by the input
 * (e.g. a document without enough text), which retrying will not fix.
 */
public class StudyAiException extends ApplicationException {

    private final boolean inputRejected;

    public StudyAiException(String message, boolean inputRejected) {
        super(ErrorCode.AI_UNAVAILABLE, message);
        this.inputRejected = inputRejected;
    }

    public boolean isInputRejected() {
        return inputRejected;
    }
}
