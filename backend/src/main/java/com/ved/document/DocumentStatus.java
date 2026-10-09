package com.ved.document;

/**
 * Lifecycle: PENDING -> PROCESSING -> COMPLETED | FAILED.
 */
public enum DocumentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
