package com.ved.knowledge.ai;

import com.ved.common.inference.InferenceClient;
import com.ved.common.inference.InferenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Startup check that the inference service's embedding size fits the database's vector columns, so a
 * misconfigured model switch shows up in the logs at once instead of as failing indexing later. Non-blocking:
 * the inference service may start after the backend.
 */
@Component
class EmbeddingCompatibilityCheck {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingCompatibilityCheck.class);
    /** Fixed by the schema (V4: vector(768)); a different size needs a migration. */
    static final int DATABASE_DIMENSIONS = 768;

    private final InferenceClient inference;

    EmbeddingCompatibilityCheck(InferenceClient inference) {
        this.inference = inference;
    }

    record Health(String embeddingModel, Integer embeddingDimensions) {
    }

    @EventListener(ApplicationReadyEvent.class)
    void check() {
        Thread.ofVirtual().name("embedding-check").start(() -> {
            try {
                Health health = inference.getJson("/health", Health.class);
                if (health.embeddingDimensions() != null && health.embeddingDimensions() != DATABASE_DIMENSIONS) {
                    log.error("Embedding model {} produces {} dimensions but the database stores {}: set "
                                    + "embeddings.dimensions to {} in the inference settings (or migrate the vector columns)",
                            health.embeddingModel(), health.embeddingDimensions(), DATABASE_DIMENSIONS,
                            DATABASE_DIMENSIONS);
                } else {
                    log.info("Embedding model: {} ({} dimensions)", health.embeddingModel(),
                            health.embeddingDimensions());
                }
            } catch (InferenceException e) {
                log.info("Inference service not reachable yet; embedding compatibility not checked");
            }
        });
    }
}
