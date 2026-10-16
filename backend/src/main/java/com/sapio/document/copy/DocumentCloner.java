package com.sapio.document.copy;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sapio.common.error.ApplicationException;
import com.sapio.common.error.ErrorCode;
import com.sapio.document.Document;
import com.sapio.document.DocumentRepository;
import com.sapio.document.DocumentStatus;
import com.sapio.document.storage.DocumentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes an independent copy of a ready document for another owner: the document row and file, then every
 * module's per-document data through the {@link DocumentCopyStep}s (tree, chunks with their embeddings, figures
 * and their files, learning path, lessons, questions, notes). Learner state (progress, attempts, reviews) is never
 * copied. No AI calls: the copy is ready at once, and nothing done to it changes the original, or vice versa.
 */
@Service
public class DocumentCloner {

    private static final Logger log = LoggerFactory.getLogger(DocumentCloner.class);

    private final DocumentRepository documents;
    private final DocumentStorage storage;
    private final JdbcTemplate jdbc;
    private final List<DocumentCopyStep> steps;
    private final Clock clock;

    DocumentCloner(DocumentRepository documents, DocumentStorage storage, JdbcTemplate jdbc,
                   List<DocumentCopyStep> steps, Clock clock) {
        this.documents = documents;
        this.storage = storage;
        this.jdbc = jdbc;
        this.steps = steps.stream().sorted(Comparator.comparingInt(DocumentCopyStep::order)).toList();
        this.clock = clock;
    }

    /** Where a copy came from; shown on it ("Imported" from Explore, or "From your teacher"). */
    public record Origin(UUID shareId, String ownerName, Kind kind) {

        public enum Kind { EXPLORE, CLASS }

        public Origin(UUID shareId, String ownerName) {
            this(shareId, ownerName, Kind.EXPLORE);
        }
    }

    /**
     * Copies in one transaction with a consistent snapshot of the source (REPEATABLE READ), so a copy never mixes
     * states, e.g. a note generated on the original halfway through.
     *
     * @return the copy's document id
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public UUID copy(UUID sourceId, UUID targetOwnerId, Origin origin) {
        Document source = documents.findById(sourceId)
                .orElseThrow(() -> ApplicationException.notFound("Document"));
        if (source.getStatus() != DocumentStatus.READY) {
            throw new ApplicationException(ErrorCode.INVALID_STATE, "Only ready documents can be copied");
        }
        UUID targetId = UUID.randomUUID();
        Instant now = clock.instant();
        DocumentCopy copy = new DocumentCopy(sourceId, targetId, targetOwnerId, jdbc, storage);
        jdbc.execute("CREATE TEMP TABLE IF NOT EXISTS " + DocumentCopy.ID_MAP
                + " (old_id UUID PRIMARY KEY, new_id UUID NOT NULL) ON COMMIT DROP");
        jdbc.update("INSERT INTO " + DocumentCopy.ID_MAP + " (old_id, new_id) VALUES (?, ?)", sourceId, targetId);

        Map<String, Object> params = new HashMap<>();
        params.put("file", copy.copyFile(source.getStorageKey()));
        params.put("now", Timestamp.from(now));
        params.put("share", origin.shareId());
        params.put("from", origin.ownerName());
        params.put("kind", origin.kind().name());
        copy.copyRows("documents", DocumentCopy.set(
                        "id", ":target", "owner_id", ":owner", "storage_key", ":file",
                        "created_at", ":now", "updated_at", ":now", "completed_at", ":now",
                        "processing_started_at", "NULL", "heartbeat_at", "NULL", "error_message", "NULL",
                        "retry_at", "NULL", "auto_retries", "0",
                        "origin_share_id", "CAST(:share AS uuid)", "origin_owner_name", ":from", "imported_at", ":now",
                        "origin_kind", ":kind"),
                "src.id = :source", params);
        copy.copyRows("document_segments", DocumentCopy.set("id", "gen_random_uuid()", "document_id", ":target"),
                "src.document_id = :source");
        copy.copyRows("document_transcripts", DocumentCopy.set("document_id", ":target"), "src.document_id = :source");

        for (DocumentCopyStep step : steps) {
            step.copy(copy);
        }
        log.info("Copied document {} to {} for user {}", sourceId, targetId, targetOwnerId);
        return targetId;
    }
}
