-- Knowledge sharing: learners publish a ready document to Explore under a category; others import it as an
-- independent copy (their own document, tree, lessons, questions and notes), so nothing they do changes the original.

CREATE TABLE share_categories
(
    slug     VARCHAR(40)  PRIMARY KEY,
    name     VARCHAR(80)  NOT NULL,
    emoji    VARCHAR(40)  NOT NULL, -- an illustration name known to the frontend
    position INT          NOT NULL UNIQUE
);

INSERT INTO share_categories (slug, name, emoji, position) VALUES
    ('computer-science', 'Computer science', 'high-voltage', 1),
    ('mathematics', 'Mathematics', 'puzzle-piece', 2),
    ('physics', 'Physics', 'rocket', 3),
    ('chemistry', 'Chemistry', 'test-tube', 4),
    ('biology', 'Biology', 'seedling', 5),
    ('medicine', 'Medicine & health', 'stethoscope', 6),
    ('engineering', 'Engineering', 'gear', 7),
    ('business', 'Business & economics', 'chart-increasing', 8),
    ('humanities', 'History & humanities', 'scroll', 9),
    ('languages', 'Languages', 'speech-balloon', 10),
    ('law', 'Law', 'balance-scale', 11),
    ('arts', 'Arts & design', 'artist-palette', 12),
    ('other', 'Other', 'books', 99);

-- One share per document. Listing fields are a snapshot taken when sharing (refreshed on update), so Explore
-- reads one table and never the sharer's live study data.
CREATE TABLE document_shares
(
    id           UUID PRIMARY KEY,
    document_id  UUID         NOT NULL UNIQUE REFERENCES documents (id) ON DELETE CASCADE,
    owner_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category     VARCHAR(40)  NOT NULL REFERENCES share_categories (slug),
    description  VARCHAR(500) NOT NULL DEFAULT '',
    title        VARCHAR(300) NOT NULL,
    summary      TEXT         NOT NULL,
    kind         VARCHAR(20)  NOT NULL,
    language     VARCHAR(8)   NOT NULL,
    page_count   INT          NOT NULL,
    topic_titles TEXT[]       NOT NULL,
    import_count INT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_document_shares_category_created ON document_shares (category, created_at DESC);
CREATE INDEX ix_document_shares_created ON document_shares (created_at DESC);
CREATE INDEX ix_document_shares_imports ON document_shares (import_count DESC, created_at DESC);

-- Where an imported document came from. The name is a snapshot, so the tag survives the share being withdrawn.
ALTER TABLE documents
    ADD COLUMN origin_share_id   UUID REFERENCES document_shares (id) ON DELETE SET NULL,
    ADD COLUMN origin_owner_name VARCHAR(120),
    ADD COLUMN imported_at       TIMESTAMPTZ;
-- A learner imports a share once (deleting the copy allows importing it again).
CREATE UNIQUE INDEX ux_documents_owner_origin_share ON documents (owner_id, origin_share_id)
    WHERE origin_share_id IS NOT NULL;
