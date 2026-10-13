package com.sapio.knowledge.store;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sapio.document.copy.DocumentCopy;
import com.sapio.document.copy.DocumentCopyStep;
import org.springframework.stereotype.Component;

import static com.sapio.document.copy.DocumentCopy.mapped;
import static com.sapio.document.copy.DocumentCopy.set;

/**
 * Copies a document's knowledge store: tree, chunks (with their embeddings, so the copy is searchable at once),
 * figures (with their own image files) and the term index.
 */
@Component
class KnowledgeCopyStep implements DocumentCopyStep {

    static final int ORDER = 100;

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void copy(DocumentCopy copy) {
        copy.mapIds("SELECT id FROM document_nodes WHERE document_id = :source");
        copy.copyRows("document_nodes", set("id", mapped("src.id"), "document_id", ":target",
                "parent_id", mapped("src.parent_id")), "src.document_id = :source");

        copy.mapIds("SELECT id FROM document_chunks WHERE document_id = :source");
        copy.copyRows("document_chunks", set("id", mapped("src.id"), "document_id", ":target",
                "node_id", mapped("src.node_id")), "src.document_id = :source");

        copy.mapIds("SELECT id FROM document_figures WHERE document_id = :source");
        copy.copyRows("document_figures", set("id", mapped("src.id"), "document_id", ":target",
                "node_id", mapped("src.node_id"), "chunk_id", mapped("src.chunk_id")), "src.document_id = :source");
        // Each copy owns its image files, so deleting either document never breaks the other.
        List<Map<String, Object>> files = copy.jdbc().queryForList(
                "SELECT id, storage_key FROM document_figures WHERE document_id = ?", copy.targetId());
        copy.jdbc().batchUpdate("UPDATE document_figures SET storage_key = ? WHERE id = ?", files.stream()
                .map(f -> new Object[] {copy.copyFile((String) f.get("storage_key")), (UUID) f.get("id")})
                .toList());

        copy.copyRows("document_terms", set("document_id", ":target", "node_id", mapped("src.node_id")),
                "src.document_id = :source");
    }
}
