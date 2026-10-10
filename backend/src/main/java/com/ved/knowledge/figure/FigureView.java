package com.ved.knowledge.figure;

import java.util.UUID;

/** A figure as sent to clients: markdown references it as {@code figure:<id>}, clients load {@code url}. */
public record FigureView(UUID id, String url, String caption, String description, String kind, int width, int height,
                         int page, UUID nodeId) {
}
