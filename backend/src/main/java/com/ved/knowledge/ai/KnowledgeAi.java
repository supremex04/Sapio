package com.ved.knowledge.ai;

import java.util.List;

/**
 * Port to the AI that indexes documents (Python inference service): figure descriptions, section
 * summaries and multimodal embeddings. Methods throw {@link KnowledgeAiException} on failure.
 */
public interface KnowledgeAi {

    List<FigureDescription> describeFigures(String language, List<FigureInput> figures);

    List<NodeSummary> summarize(String language, List<SummaryInput> nodes);

    /** One vector per item, same order. */
    List<float[]> embed(EmbedTask task, List<EmbedInput> items);

    enum EmbedTask {
        RETRIEVAL_DOCUMENT,
        RETRIEVAL_QUERY
    }

    record FigureInput(String key, byte[] image, String mime, String caption, String context) {
    }

    record FigureDescription(String key, String kind, boolean educational, String description) {
    }

    record SummaryInput(String key, String path, String text, List<String> childSummaries) {
    }

    record NodeSummary(String key, String summary, List<String> keyTerms) {
    }

    /** Text, an image, or both. */
    record EmbedInput(String text, byte[] image, String mime) {
        public static EmbedInput text(String text) {
            return new EmbedInput(text, null, null);
        }
    }
}
