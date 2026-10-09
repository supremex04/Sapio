package com.ved.document.extraction;

import java.util.List;

public record ExtractionResult(String markdown, String engine, int pageCount, int textPages, int ocrPages,
                               List<Segment> segments) {

    /** @param method "text" or "ocr" */
    public record Segment(int pageFrom, int pageTo, String method, String markdown) {
    }
}
