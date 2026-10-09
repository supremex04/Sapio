package com.ved.document;

/**
 * Pipeline: PENDING -> EXTRACTING -> ANALYZING -> READY, or FAILED from either working state.
 */
public enum DocumentStatus {
    PENDING,
    /** Reading text: native PDF text or OCR. */
    EXTRACTING,
    /** Text available; building the study material (topics) with the LLM. */
    ANALYZING,
    READY,
    FAILED;

    public boolean isTerminal() {
        return this == READY || this == FAILED;
    }
}
