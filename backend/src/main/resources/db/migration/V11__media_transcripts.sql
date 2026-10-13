-- Audio and video uploads: the recording is transcribed once (slow; kept so a failed notes step never redoes it),
-- then turned into study notes that become the document's tree. For media, "page" means minute of the recording.
ALTER TABLE documents ADD COLUMN duration_seconds INT;

CREATE TABLE document_transcripts
(
    document_id      UUID PRIMARY KEY REFERENCES documents (id) ON DELETE CASCADE,
    language         VARCHAR(8)       NOT NULL,
    duration_seconds DOUBLE PRECISION NOT NULL,
    engine           VARCHAR(100)     NOT NULL,
    segments_json    TEXT             NOT NULL, -- [{"start": s, "end": s, "text": "..."}]
    created_at       TIMESTAMPTZ      NOT NULL
);
