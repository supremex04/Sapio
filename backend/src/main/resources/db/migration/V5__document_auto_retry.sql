-- Transient pipeline failures (AI provider busy/rate-limited) are retried automatically with backoff.
ALTER TABLE documents
    ADD COLUMN auto_retries INT NOT NULL DEFAULT 0,
    ADD COLUMN retry_at     TIMESTAMPTZ;
CREATE INDEX ix_documents_retry_at ON documents (retry_at) WHERE retry_at IS NOT NULL;
