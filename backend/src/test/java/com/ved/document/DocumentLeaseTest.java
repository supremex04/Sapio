package com.ved.document;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Every transition into the queue starts a fresh lease, so recovery never mistakes it for an abandoned job. */
class DocumentLeaseTest {

    private static final Instant T0 = Instant.parse("2026-10-12T10:00:00Z");

    @Test
    void resumingAFailedDocumentRenewsItsLease() {
        Document doc = new Document(UUID.randomUUID(), "a.pdf", "application/pdf", 10, "key");
        doc.startProcessing(T0);
        doc.markFailed("busy", T0.plusSeconds(5), T0.plus(Duration.ofMinutes(15)));

        Instant later = T0.plus(Duration.ofMinutes(15));
        doc.resume(later);

        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(doc.getHeartbeatAt()).isEqualTo(later);
    }

    @Test
    void claimingAndRequeueingRenewTheLease() {
        Document doc = new Document(UUID.randomUUID(), "a.pdf", "application/pdf", 10, "key");
        doc.startProcessing(T0);
        assertThat(doc.getHeartbeatAt()).isEqualTo(T0);

        doc.requeue(T0.plusSeconds(300));
        assertThat(doc.getHeartbeatAt()).isEqualTo(T0.plusSeconds(300));
    }
}
