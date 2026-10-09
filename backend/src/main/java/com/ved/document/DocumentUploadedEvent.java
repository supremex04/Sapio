package com.ved.document;

import java.util.UUID;

/** Published once an uploaded document is persisted; processing starts after the transaction commits. */
public record DocumentUploadedEvent(UUID documentId) {
}
