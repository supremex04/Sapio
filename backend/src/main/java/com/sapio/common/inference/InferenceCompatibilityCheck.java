package com.sapio.common.inference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Startup check that the inference service speaks the contract version this backend was built against, and that
 * its embedding size fits the database's vector columns. A mismatch shows up in the logs at once instead of as
 * failing requests later. Non-blocking: the inference service may start after the backend.
 */
@Component
class InferenceCompatibilityCheck {

    private static final Logger log = LoggerFactory.getLogger(InferenceCompatibilityCheck.class);
    /** Major version of contracts/inference-openapi.json this backend implements. */
    static final String CONTRACT_MAJOR = "1";
    /** Fixed by the schema (V4: vector(768)); a different size needs a migration. */
    static final int DATABASE_DIMENSIONS = 768;

    private final InferenceClient inference;

    InferenceCompatibilityCheck(InferenceClient inference) {
        this.inference = inference;
    }

    record Health(String contractVersion, String embeddingModel, Integer embeddingDimensions) {
    }

    @EventListener(ApplicationReadyEvent.class)
    void check() {
        Thread.ofVirtual().name("inference-check").start(() -> {
            try {
                check(inference.getJson("/health", Health.class));
            } catch (InferenceException e) {
                log.info("Inference service not reachable yet; compatibility not checked");
            }
        });
    }

    /** @return whether the service is compatible */
    boolean check(Health health) {
        boolean ok = true;
        String version = health.contractVersion();
        if (version == null || !version.split("\\.")[0].equals(CONTRACT_MAJOR)) {
            log.error("Inference service speaks contract {} but this backend implements {}.x: deploy matching "
                    + "versions of both services", version, CONTRACT_MAJOR);
            ok = false;
        }
        if (health.embeddingDimensions() != null && health.embeddingDimensions() != DATABASE_DIMENSIONS) {
            log.error("Embedding model {} produces {} dimensions but the database stores {}: set "
                            + "embeddings.dimensions to {} in the inference settings (or migrate the vector columns)",
                    health.embeddingModel(), health.embeddingDimensions(), DATABASE_DIMENSIONS, DATABASE_DIMENSIONS);
            ok = false;
        }
        if (ok) {
            log.info("Inference service: contract {}, embeddings {} ({} dimensions)", version,
                    health.embeddingModel(), health.embeddingDimensions());
        }
        return ok;
    }
}
