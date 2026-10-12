-- Processing lease: the instance working on a document refreshes heartbeat_at; documents whose heartbeat stops
-- (crash, scale-down, restart) are requeued by any instance. Makes the pipeline safe with several backends.
ALTER TABLE documents ADD COLUMN heartbeat_at TIMESTAMPTZ;
CREATE INDEX ix_documents_in_flight ON documents (status, heartbeat_at)
    WHERE status IN ('PENDING', 'EXTRACTING', 'INDEXING', 'ANALYZING');
