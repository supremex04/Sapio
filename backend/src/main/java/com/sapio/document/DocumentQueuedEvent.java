package com.sapio.document;

import java.util.UUID;

/** A document was queued for processing (upload or retry); processing starts after the transaction commits. */
public record DocumentQueuedEvent(UUID documentId) {
}
