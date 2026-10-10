package com.ved.knowledge.ai;

import com.ved.common.error.ApplicationException;
import com.ved.common.error.ErrorCode;

/**
 * Indexing/embedding AI call failed. Rendered as 503 AI_UNAVAILABLE if it escapes to a request.
 * {@link #isRejected()}: the inference service refused the request (4xx: bad input or misconfiguration), so
 * retrying will not help; otherwise it was busy or unavailable (transient).
 */
public class KnowledgeAiException extends ApplicationException {

    private final boolean rejected;

    public KnowledgeAiException(String message) {
        this(message, false);
    }

    public KnowledgeAiException(String message, boolean rejected) {
        super(ErrorCode.AI_UNAVAILABLE, message);
        this.rejected = rejected;
    }

    public boolean isRejected() {
        return rejected;
    }
}
