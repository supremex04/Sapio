package com.sapio.knowledge;

import java.util.UUID;

/**
 * Some of a document's vectors were made by an embedding model other than the current one (the model was
 * switched in the inference service's configuration), or some text has no vector yet ({@code model} null). They
 * are (re-)embedded in the background; until then, search uses only the vectors that match the query's model,
 * plus keyword search.
 */
public record StaleEmbeddingsDetected(UUID documentId, String model) {
}
