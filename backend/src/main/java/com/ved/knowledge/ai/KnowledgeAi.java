package com.ved.knowledge.ai;

import java.util.List;

/**
 * Port to the AI that indexes documents (Python inference service): figure descriptions, section
 * summaries and multimodal embeddings. Methods throw {@link KnowledgeAiException} on failure.
 */
public interface KnowledgeAi {

    List<FigureDescription> describeFigures(String language, List<FigureInput> figures);

    List<NodeSummary> summarize(String language, List<SummaryInput> nodes);

    /** One vector per item, same order, tagged with the model that produced them. */
    Embeddings embed(EmbedTask task, List<EmbedInput> items);

    enum EmbedTask {
        RETRIEVAL_DOCUMENT,
        RETRIEVAL_QUERY
    }

    record FigureInput(String key, byte[] image, String mime, String caption, String context) {
    }

    /**
     * How a figure is used. VISUAL: shown to students. TEXTUAL: its text carries the meaning (terminal output,
     * code), so it is kept as an annotated {@code transcript} in the section text instead of an image.
     * DECORATIVE: dropped.
     */
    enum FigureRole {
        VISUAL,
        TEXTUAL,
        DECORATIVE
    }

    record FigureDescription(String key, String kind, FigureRole role, String description, String transcript) {
    }

    record SummaryInput(String key, String path, String text, List<String> childSummaries) {
    }

    record NodeSummary(String key, String summary, List<String> keyTerms) {
    }

    /**
     * @param model stable identity of the embedding model ("provider:model"); vectors from different models are
     *              never compared
     */
    record Embeddings(String model, List<float[]> vectors) {
    }

    /** Text, an image, or both. */
    record EmbedInput(String text, byte[] image, String mime) {
        public static EmbedInput text(String text) {
            return new EmbedInput(text, null, null);
        }
    }
}
