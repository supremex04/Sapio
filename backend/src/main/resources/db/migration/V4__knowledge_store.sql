-- M3: knowledge store: document tree, leaf chunks, figures, hybrid (keyword + vector) index,
-- term index and cached notes. See docs/specs/knowledge.md and ADR-0009.

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS ltree;

-- ---------------------------------------------------------------- rebuild M2 materials
-- M2 study materials were built from flat chunks without structure or figures. Pre-production data:
-- drop them (cascades to topics, lessons, questions, attempts, assessments, review cards, progress)
-- and re-run every document through the structural pipeline. Learning events / XP are kept.
DELETE FROM study_materials;
DELETE FROM document_segments;
UPDATE documents
SET status = 'PENDING', result_markdown = NULL, engine = NULL, page_count = NULL, text_pages = NULL,
    ocr_pages = NULL, title = NULL, error_message = NULL, completed_at = NULL, processing_started_at = NULL;

ALTER TABLE documents ADD COLUMN language VARCHAR(8);

DROP TABLE study_chunks;
ALTER TABLE study_topics DROP COLUMN chunk_positions;
ALTER TABLE study_topics ADD COLUMN node_ids UUID[] NOT NULL DEFAULT '{}';
ALTER TABLE study_questions DROP COLUMN source_chunk_positions;
ALTER TABLE study_questions ADD COLUMN source_node_ids UUID[] NOT NULL DEFAULT '{}';
ALTER TABLE study_lessons ADD COLUMN figure_ids UUID[] NOT NULL DEFAULT '{}';

-- ---------------------------------------------------------------- the tree
CREATE TABLE document_nodes
(
    id          UUID PRIMARY KEY,
    document_id UUID         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    parent_id   UUID REFERENCES document_nodes (id) ON DELETE CASCADE,
    path        LTREE        NOT NULL, -- ordinal path "2.3.1": subtree queries with <@
    label       VARCHAR(120) NOT NULL, -- the document's own numbering ("Unit 4", "2.3") or the path
    title       VARCHAR(400) NOT NULL,
    level       INT          NOT NULL,
    ordinal     INT          NOT NULL, -- pre-order position
    page_from   INT          NOT NULL,
    page_to     INT          NOT NULL,
    summary     TEXT,
    key_terms   TEXT[]       NOT NULL DEFAULT '{}',
    UNIQUE (document_id, path),
    UNIQUE (document_id, ordinal)
);
CREATE INDEX ix_document_nodes_path ON document_nodes USING GIST (path);
CREATE INDEX ix_document_nodes_parent ON document_nodes (parent_id);

-- ---------------------------------------------------------------- leaf chunks: inverted + dense index
CREATE TABLE document_chunks
(
    id            UUID PRIMARY KEY,
    document_id   UUID      NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    node_id       UUID      NOT NULL REFERENCES document_nodes (id) ON DELETE CASCADE,
    ordinal       INT       NOT NULL,
    content       TEXT      NOT NULL,
    page_from     INT       NOT NULL,
    page_to       INT       NOT NULL,
    search_config REGCONFIG NOT NULL DEFAULT 'simple',
    tsv           TSVECTOR GENERATED ALWAYS AS (to_tsvector(search_config, content)) STORED,
    embedding     VECTOR(768),
    UNIQUE (document_id, ordinal)
);
CREATE INDEX ix_document_chunks_tsv ON document_chunks USING GIN (tsv);
CREATE INDEX ix_document_chunks_embedding ON document_chunks USING hnsw (embedding vector_cosine_ops);
CREATE INDEX ix_document_chunks_node ON document_chunks (node_id, ordinal);

-- ---------------------------------------------------------------- figures (files live in DocumentStorage)
CREATE TABLE document_figures
(
    id            UUID PRIMARY KEY,
    document_id   UUID         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    node_id       UUID         NOT NULL REFERENCES document_nodes (id) ON DELETE CASCADE,
    chunk_id      UUID REFERENCES document_chunks (id) ON DELETE SET NULL,
    ordinal       INT          NOT NULL,
    page          INT          NOT NULL,
    bbox          REAL[],
    storage_key   VARCHAR(512) NOT NULL,
    mime          VARCHAR(40)  NOT NULL,
    width         INT          NOT NULL,
    height        INT          NOT NULL,
    caption       TEXT         NOT NULL DEFAULT '',
    description   TEXT,
    kind          VARCHAR(20),
    search_config REGCONFIG    NOT NULL DEFAULT 'simple',
    tsv           TSVECTOR GENERATED ALWAYS AS
                      (to_tsvector(search_config, caption || ' ' || coalesce(description, ''))) STORED,
    embedding     VECTOR(768),
    UNIQUE (document_id, ordinal)
);
CREATE INDEX ix_document_figures_tsv ON document_figures USING GIN (tsv);
CREATE INDEX ix_document_figures_embedding ON document_figures USING hnsw (embedding vector_cosine_ops);
CREATE INDEX ix_document_figures_node ON document_figures (node_id, ordinal);

-- ---------------------------------------------------------------- back-of-book term index
CREATE TABLE document_terms
(
    document_id UUID         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    term        VARCHAR(200) NOT NULL,
    node_id     UUID         NOT NULL REFERENCES document_nodes (id) ON DELETE CASCADE,
    PRIMARY KEY (document_id, term, node_id)
);
CREATE INDEX ix_document_terms_lookup ON document_terms (document_id, lower(term));

-- ---------------------------------------------------------------- cached notes per node
CREATE TABLE study_notes
(
    id              UUID PRIMARY KEY,
    node_id         UUID        NOT NULL UNIQUE REFERENCES document_nodes (id) ON DELETE CASCADE,
    markdown        TEXT        NOT NULL,
    figure_ids      UUID[]      NOT NULL DEFAULT '{}',
    source_node_ids UUID[]      NOT NULL DEFAULT '{}',
    model           VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL
);
