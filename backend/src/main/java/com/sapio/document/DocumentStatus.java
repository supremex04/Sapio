package com.sapio.document;

/**
 * Pipeline: PENDING -> EXTRACTING -> INDEXING -> ANALYZING -> READY, or FAILED from any working state.
 */
public enum DocumentStatus {
    PENDING,
    /** Reading text and structure (tree, chunks, figures): native PDF text or OCR. */
    EXTRACTING,
    /** Building the knowledge index: figure descriptions, summaries, term index, embeddings. */
    INDEXING,
    /** Text available; building the study material (topics) with the LLM. */
    ANALYZING,
    READY,
    FAILED;

    public boolean isTerminal() {
        return this == READY || this == FAILED;
    }
}
