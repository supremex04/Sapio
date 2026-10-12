package com.ved.document.copy;

/**
 * One module's part of copying a document: it duplicates the rows (and files) it owns for the source document so
 * the copy is fully independent. Implemented by every module that stores per-document data (knowledge store,
 * study content), so {@code document} does not depend on them. Steps run in ascending {@link #order()} inside one
 * transaction and see the id mappings of the steps before them.
 */
public interface DocumentCopyStep {

    int order();

    void copy(DocumentCopy copy);
}
