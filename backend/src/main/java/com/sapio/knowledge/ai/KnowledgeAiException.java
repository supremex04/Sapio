package com.sapio.knowledge.ai;

import java.time.Duration;

import com.sapio.common.ai.AiException;

/** {@link KnowledgeAi} failed. See {@link AiException} for how it is classified and rendered. */
public class KnowledgeAiException extends AiException {

    public KnowledgeAiException(String message, boolean inputRejected, boolean retryable, Duration retryAfter) {
        super(message, inputRejected, retryable, retryAfter);
    }

    /** A transient failure: busy or unavailable. */
    public KnowledgeAiException(String message) {
        this(message, false, true, null);
    }
}
