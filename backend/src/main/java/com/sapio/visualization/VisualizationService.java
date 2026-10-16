package com.sapio.visualization;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.sapio.common.error.ApplicationException;
import com.sapio.common.error.ErrorCode;
import com.sapio.document.DocumentService;
import com.sapio.document.storage.DocumentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * A learner's visualizations (docs/specs/visualizations.md): ask for one, follow it while it is made, watch it,
 * retry or delete it. Asking again for the same thing reuses the finished (or in-progress) one, and a daily limit
 * per learner caps rendering costs.
 */
@Service
public class VisualizationService {

    private static final Logger log = LoggerFactory.getLogger(VisualizationService.class);
    static final int MAX_REQUEST = 2000;
    static final int MAX_CONTEXT = 6000;
    private static final int LIST_LIMIT = 100;

    public record View(UUID id, String request, UUID documentId, VisualizationStore.Status status, String title,
                       String kind, List<VisualizeAi.Beat> beats, String code, int attempts, String error,
                       Double durationSeconds, String videoUrl, String thumbnailUrl, Instant createdAt,
                       Instant completedAt) {
    }

    private final VisualizationStore store;
    private final VisualizationJob job;
    private final VisualizationUrlSigner signer;
    private final VisualizationProperties properties;
    private final DocumentService documents;
    private final DocumentStorage storage;
    private final JsonMapper json;
    private final Clock clock;

    VisualizationService(VisualizationStore store, VisualizationJob job, VisualizationUrlSigner signer,
                         VisualizationProperties properties, DocumentService documents, DocumentStorage storage,
                         JsonMapper json, Clock clock) {
        this.store = store;
        this.job = job;
        this.signer = signer;
        this.properties = properties;
        this.documents = documents;
        this.storage = storage;
        this.json = json;
        this.clock = clock;
    }

    public View create(UUID userId, String request, UUID documentId, String context) {
        String text = request == null ? "" : request.strip();
        if (text.isEmpty() || text.length() > MAX_REQUEST) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED,
                    "Describe what to visualize in at most " + MAX_REQUEST + " characters");
        }
        if (documentId != null) {
            documents.get(userId, documentId); // must be the learner's own (404 otherwise)
        }
        String trimmedContext = context == null || context.isBlank() ? null
                : context.strip().substring(0, Math.min(context.strip().length(), MAX_CONTEXT));
        String hash = hash(text, trimmedContext);
        var existing = store.reusable(userId, hash);
        if (existing.isPresent()) {
            return view(existing.get());
        }
        Instant now = clock.instant();
        if (store.countSince(userId, now.minus(Duration.ofDays(1))) >= properties.dailyLimit()) {
            throw new ApplicationException(ErrorCode.RATE_LIMITED, "You have made " + properties.dailyLimit()
                    + " visualizations today. More tomorrow!");
        }
        UUID id = UUID.randomUUID();
        store.insert(id, userId, text, hash, documentId, trimmedContext, now);
        job.run(id);
        return get(userId, id);
    }

    public List<View> list(UUID userId) {
        return store.list(userId, LIST_LIMIT).stream().map(this::view).toList();
    }

    public View get(UUID userId, UUID id) {
        return view(require(userId, id));
    }

    /** Makes a failed one again from the start; does not count toward the daily limit. */
    public View retry(UUID userId, UUID id) {
        VisualizationStore.Row row = require(userId, id);
        if (row.status() != VisualizationStore.Status.FAILED) {
            throw new ApplicationException(ErrorCode.INVALID_STATE, "Only a failed visualization can be retried");
        }
        store.restart(id, clock.instant());
        job.run(id);
        return get(userId, id);
    }

    public void delete(UUID userId, UUID id) {
        VisualizationStore.Row row = require(userId, id);
        store.delete(id);
        removeFile(row.videoKey());
        removeFile(row.thumbnailKey());
    }

    /** The video or thumbnail behind a signed URL (404 when the signature is wrong or expired). */
    Resource asset(UUID id, VisualizationUrlSigner.Asset asset, long exp, String sig) {
        if (!signer.verify(id, asset, exp, sig)) {
            throw ApplicationException.notFound("Visualization");
        }
        VisualizationStore.Row row = store.find(id).orElseThrow(() -> ApplicationException.notFound("Visualization"));
        String key = asset == VisualizationUrlSigner.Asset.video ? row.videoKey() : row.thumbnailKey();
        if (key == null) {
            throw ApplicationException.notFound("Visualization");
        }
        return storage.load(key);
    }

    /** Picks up work a restart interrupted (rows that have not moved for a while). */
    @EventListener(ApplicationReadyEvent.class)
    void resumeInterrupted() {
        Instant stale = clock.instant().minus(properties.renderTimeout());
        for (UUID id : store.working()) {
            store.find(id).filter(r -> r.updatedAt().isBefore(stale)).ifPresent(r -> {
                log.info("Resuming visualization {}", id);
                job.run(id);
            });
        }
    }

    private VisualizationStore.Row require(UUID userId, UUID id) {
        return store.find(userId, id).orElseThrow(() -> ApplicationException.notFound("Visualization"));
    }

    private View view(VisualizationStore.Row r) {
        List<VisualizeAi.Beat> beats = r.storyboardJson() == null ? List.of()
                : json.readValue(r.storyboardJson(), new TypeReference<List<VisualizeAi.Beat>>() { });
        boolean ready = r.status() == VisualizationStore.Status.READY;
        return new View(r.id(), r.request(), r.documentId(), r.status(), r.title(), r.kind(), beats, r.code(),
                r.attempts(), r.status() == VisualizationStore.Status.FAILED ? r.error() : null, r.durationSeconds(),
                ready && r.videoKey() != null ? signer.url(r.id(), VisualizationUrlSigner.Asset.video) : null,
                ready && r.thumbnailKey() != null ? signer.url(r.id(), VisualizationUrlSigner.Asset.thumbnail) : null,
                r.createdAt(), r.completedAt());
    }

    private void removeFile(String key) {
        if (key == null) {
            return;
        }
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete visualization file {}: {}", key, e.getMessage());
        }
    }

    static String hash(String request, String context) {
        String normal = request.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip() + "\n"
                + (context == null ? "" : context.replaceAll("\\s+", " ").strip());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(normal.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
