package com.sapio.knowledge.web;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.sapio.common.error.ApplicationException;
import com.sapio.common.error.ErrorCode;
import com.sapio.document.Document;
import com.sapio.document.DocumentStatus;
import com.sapio.document.storage.DocumentStorage;
import com.sapio.knowledge.SectionEdited;
import com.sapio.knowledge.edit.SectionMarkdown;
import com.sapio.knowledge.store.KnowledgeStore;
import com.sapio.knowledge.store.KnowledgeStore.FigureRow;
import com.sapio.knowledge.store.KnowledgeStore.NodeRow;
import com.sapio.knowledge.store.SearchConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Learner edits to the reading view (ADR-0014). A section is edited as Markdown, the same text the reading view
 * renders, and saved whole, but changed minimally: unchanged chunks keep their vectors, only new ones are embedded
 * (in the background; keyword search sees them at once), figures are re-anchored or removed. Nothing AI-written
 * is regenerated on save: significant changes bump the section's content version, and summaries and notes
 * written from an older version are offered for a rewrite.
 */
@Service
public class SectionEditService {

    private static final Logger log = LoggerFactory.getLogger(SectionEditService.class);

    /** A change counts as significant (AI-written text built on it is behind) at this many characters... */
    static final int SIGNIFICANT_CHARS = 400;
    /** ...or this share of the section, whichever is larger. Smaller edits accumulate until they get there. */
    static final double SIGNIFICANT_SHARE = 0.15;

    private final KnowledgeAccess access;
    private final KnowledgeStore store;
    private final KnowledgeService knowledge;
    private final DocumentStorage storage;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    SectionEditService(KnowledgeAccess access, KnowledgeStore store, KnowledgeService knowledge,
                       DocumentStorage storage, ApplicationEventPublisher events, TransactionTemplate tx, Clock clock) {
        this.access = access;
        this.store = store;
        this.knowledge = knowledge;
        this.storage = storage;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    /** Saves a section and returns the document's reading view as it is now. */
    public KnowledgeResponses.ContentResponse edit(UUID userId, UUID documentId, UUID nodeId, SectionEditRequest request) {
        Document document = access.requireDocument(userId, documentId);
        NodeRow node = access.requireNode(userId, documentId, nodeId);
        if (document.getStatus() != DocumentStatus.READY) {
            throw new ApplicationException(ErrorCode.INVALID_STATE,
                    "You can edit this document once Sapio has finished preparing it");
        }
        String title = request.title().strip();
        if (title.isEmpty()) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, "A section needs a title");
        }

        // The figures the reading view shows for this section; hidden ones (decorative, not yet described) stay.
        Set<UUID> own = new HashSet<>();
        for (FigureRow f : store.figuresOfDocument(documentId)) {
            if (f.nodeId().equals(nodeId) && f.description() != null) {
                own.add(f.id());
            }
        }
        List<SectionMarkdown.Existing> existing = store.chunksOfNode(nodeId).stream()
                .map(c -> new SectionMarkdown.Existing(c.id(), c.content())).toList();
        SectionMarkdown.Parsed parsed = SectionMarkdown.parse(request.markdown(), own);
        List<SectionMarkdown.Planned> chunks = SectionMarkdown.plan(parsed.blocks(), existing);

        // How much changed decides whether AI-written summaries and notes count as behind (see recordEdit).
        List<String> before = existing.stream().flatMap(e -> SectionMarkdown.textBlocks(e.content()).stream()).toList();
        int changed = SectionMarkdown.changedChars(before, parsed.blocks())
                + SectionMarkdown.editedMiddle(node.title(), title);
        int size = Math.max(length(before), length(parsed.blocks()));
        int significant = Math.max(SIGNIFICANT_CHARS, (int) (size * SIGNIFICANT_SHARE));

        List<UUID> placed = new ArrayList<>(parsed.figures().keySet());
        List<UUID> anchors = new ArrayList<>();
        for (UUID figure : placed) {
            // Before any text: the section's first chunk, as extraction does; no text at all: end of section.
            int block = Math.max(0, parsed.figures().get(figure));
            anchors.add(chunks.stream().filter(c -> c.from() <= block && block <= c.to()).map(SectionMarkdown.Planned::id)
                    .findFirst().orElse(null));
        }
        Set<UUID> removed = new HashSet<>(own);
        placed.forEach(removed::remove);

        List<String> files = tx.execute(status -> {
            if (!store.recordEdit(nodeId, request.revision(), title, clock.instant(), changed, significant)) {
                throw new ApplicationException(ErrorCode.EDIT_CONFLICT,
                        "This section was changed somewhere else. Reload it to see the latest version.");
            }
            store.replaceChunks(documentId, nodeId, chunks, node.pageFrom(), node.pageTo(),
                    SearchConfig.forLanguage(document.getLanguage()));
            store.placeFigures(placed, anchors);
            List<String> deleted = store.deleteFigures(removed);
            events.publishEvent(new SectionEdited(documentId, nodeId));
            return deleted;
        });
        files.forEach(this::deleteQuietly);
        log.info("Document {}: section {} saved ({} chunks, {} new; {} chars changed; {} figures removed)", documentId,
                nodeId, chunks.size(), chunks.stream().filter(c -> !c.kept()).count(), changed, removed.size());
        return knowledge.content(userId, documentId);
    }

    private static int length(List<String> blocks) {
        return blocks.stream().mapToInt(String::length).sum();
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete figure file {}", key, e);
        }
    }
}
