package com.sapio.knowledge;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Brings a subtree's section summaries up to date with the learner's edits, on request (summaries cost an AI
 * call, so saving never rewrites them). Study features call this before building on a subtree's summaries.
 */
@Component
public class SummaryRefresher {

    private final KnowledgeIndexer indexer;

    SummaryRefresher(KnowledgeIndexer indexer) {
        this.indexer = indexer;
    }

    /** @param path the subtree's ltree path */
    public void refresh(UUID documentId, String path) {
        indexer.refreshSummaries(documentId, path);
    }
}
