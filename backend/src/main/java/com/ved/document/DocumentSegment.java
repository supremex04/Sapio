package com.ved.document;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Extracted text of one page, or of a run of pages OCR'd together. */
@Entity
@Table(name = "document_segments")
public class DocumentSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(nullable = false)
    private int position;

    @Column(name = "page_from", nullable = false)
    private int pageFrom;

    @Column(name = "page_to", nullable = false)
    private int pageTo;

    /** "text" (native PDF text layer) or "ocr". */
    @Column(nullable = false)
    private String method;

    @Column(nullable = false)
    private String content;

    protected DocumentSegment() {
        // for JPA
    }

    public DocumentSegment(UUID documentId, int position, int pageFrom, int pageTo, String method, String content) {
        this.documentId = documentId;
        this.position = position;
        this.pageFrom = pageFrom;
        this.pageTo = pageTo;
        this.method = method;
        this.content = content;
    }

    public int getPosition() {
        return position;
    }

    public int getPageFrom() {
        return pageFrom;
    }

    public int getPageTo() {
        return pageTo;
    }

    public String getMethod() {
        return method;
    }

    public String getContent() {
        return content;
    }
}
