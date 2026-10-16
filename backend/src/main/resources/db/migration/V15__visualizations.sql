-- Visualizations: a learner asks for a formula or idea to be animated (docs/adr/0016-visualizations.md).
-- The backend plans it with the AI (storyboard, then Manim code), renders it in the separate renderer service,
-- repairs failed code with the renderer's error, and stores the video like a figure.
CREATE TABLE visualizations
(
    id               UUID PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    request          TEXT         NOT NULL,
    request_hash     VARCHAR(64)  NOT NULL, -- normalised request: the same ask reuses a finished video
    document_id      UUID REFERENCES documents (id) ON DELETE SET NULL, -- where it was asked from, if anywhere
    context          TEXT,         -- surrounding material sent to the AI (e.g. the section of a formula)
    status           VARCHAR(16)  NOT NULL, -- PLANNING | CODING | RENDERING | READY | FAILED
    title            VARCHAR(300),
    kind             VARCHAR(20),
    storyboard_json  TEXT,
    code             TEXT,
    attempts         INT          NOT NULL DEFAULT 0, -- renders tried (each failure is repaired by the AI)
    error            TEXT,         -- user-facing reason when FAILED
    video_key        VARCHAR(512),
    thumbnail_key    VARCHAR(512),
    duration_seconds REAL,
    model            VARCHAR(100),
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    completed_at     TIMESTAMPTZ
);
CREATE INDEX ix_visualizations_user ON visualizations (user_id, created_at DESC);
CREATE INDEX ix_visualizations_hash ON visualizations (user_id, request_hash);
