package com.ved.knowledge.ai;

import com.ved.common.error.ApplicationException;
import com.ved.common.error.ErrorCode;

/** Indexing/embedding AI call failed. Rendered as 503 AI_UNAVAILABLE if it escapes to a request. */
public class KnowledgeAiException extends ApplicationException {

    public KnowledgeAiException(String message) {
        super(ErrorCode.AI_UNAVAILABLE, message);
    }
}
