-- Documents uploaded for OCR / text extraction, and their conversion results.

CREATE TABLE documents
(
    id                    UUID PRIMARY KEY,
    owner_id              UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    original_filename     VARCHAR(255)  NOT NULL,
    content_type          VARCHAR(100)  NOT NULL,
    size_bytes            BIGINT        NOT NULL,
    storage_key           VARCHAR(512)  NOT NULL,
    status                VARCHAR(20)   NOT NULL,
    engine                VARCHAR(64),
    page_count            INT,
    result_markdown       TEXT,
    error_message         VARCHAR(1000),
    created_at            TIMESTAMPTZ   NOT NULL,
    updated_at            TIMESTAMPTZ   NOT NULL,
    processing_started_at TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ
);

CREATE INDEX ix_documents_owner_created ON documents (owner_id, created_at DESC);
CREATE INDEX ix_documents_status ON documents (status);
