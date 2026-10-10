package com.ved.document.extraction;

import java.util.List;

/**
 * Text and structure of a document, extracted once. {@code tree} is the navigable structure
 * (nodes → leaf chunks, figures with image bytes) handed to the {@link com.ved.document.DocumentIndexer}.
 */
public record ExtractionResult(String markdown, String engine, String language, int pageCount, int textPages,
                               int ocrPages, List<Segment> segments, Tree tree) {

    /** @param method "text" or "ocr" */
    public record Segment(int pageFrom, int pageTo, String method, String markdown) {
    }

    public record Tree(List<Node> nodes, List<Chunk> chunks, List<Figure> figures) {
    }

    /** Keys are local to one extraction ("n3", "c12", "f2"). */
    public record Node(String key, String parentKey, int level, String path, String label, String title, int ordinal,
                       int pageFrom, int pageTo) {
    }

    public record Chunk(String key, String nodeKey, int ordinal, String text, int pageFrom, int pageTo) {
    }

    public record Figure(String key, String nodeKey, String chunkKey, int ordinal, int page, List<Float> bbox,
                         String caption, String mime, int width, int height, byte[] image) {
    }
}
