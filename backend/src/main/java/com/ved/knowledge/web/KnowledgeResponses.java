package com.ved.knowledge.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ved.knowledge.figure.FigureView;

/** Response shapes of the navigation API (docs/specs/knowledge.md). */
public final class KnowledgeResponses {

    private KnowledgeResponses() {
    }

    public record NodeSummary(UUID id, UUID parentId, String path, String label, String title, int level, int pageFrom,
                              int pageTo, int figureCount) {
    }

    public record TreeResponse(UUID documentId, List<NodeSummary> nodes) {
    }

    public record Crumb(UUID id, String label, String title) {
    }

    /** {@code type} is "text" (markdown) or "figure" (figureId), in reading order. */
    public record Block(String type, String markdown, UUID figureId, int page) {
    }

    public record NodeDetail(UUID id, String path, String label, String title, int level, int pageFrom, int pageTo,
                             String summary, List<String> keyTerms) {
    }

    public record NodeResponse(NodeDetail node, List<Crumb> breadcrumb, List<Crumb> children, Crumb previous, Crumb next,
                               List<Block> blocks, Map<String, FigureView> figures) {
    }

    public record SearchHit(UUID chunkId, UUID nodeId, String label, String title, String breadcrumb, String snippet,
                            double score) {
    }

    public record SearchResponse(String query, List<SearchHit> hits, List<FigureView> figures) {
    }

    public record TermEntry(String term, List<Crumb> nodes) {
    }

    public record TermsResponse(List<TermEntry> terms) {
    }
}
