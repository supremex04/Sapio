-- Quality signals on shared documents: one vote per learner (up or down) and a comment thread.
-- Counters on document_shares are kept in step inside the same transaction (the share row is locked first),
-- so Explore can sort by them without aggregating.

CREATE TABLE share_votes
(
    share_id   UUID        NOT NULL REFERENCES document_shares (id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    value      SMALLINT    NOT NULL CHECK (value IN (-1, 1)),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (share_id, user_id)
);
CREATE INDEX ix_share_votes_user ON share_votes (user_id);

CREATE TABLE share_comments
(
    id         UUID PRIMARY KEY,
    share_id   UUID          NOT NULL REFERENCES document_shares (id) ON DELETE CASCADE,
    author_id  UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    body       VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL
);
CREATE INDEX ix_share_comments_share_created ON share_comments (share_id, created_at DESC);

ALTER TABLE document_shares
    ADD COLUMN upvotes       INT NOT NULL DEFAULT 0,
    ADD COLUMN downvotes     INT NOT NULL DEFAULT 0,
    ADD COLUMN comment_count INT NOT NULL DEFAULT 0;

-- "Top rated" order: lower bound of the Wilson score interval (95%) of the share of upvotes. Unlike up minus
-- down, it ranks 45 up / 5 down above 1 up / 0 down, and a few early votes don't dominate.
ALTER TABLE document_shares ADD COLUMN quality DOUBLE PRECISION GENERATED ALWAYS AS (
    CASE WHEN upvotes + downvotes = 0 THEN 0 ELSE
        ((upvotes + 1.9208) / (upvotes + downvotes)
            - 1.96 * sqrt((upvotes::float8 * downvotes) / (upvotes + downvotes) + 0.9604) / (upvotes + downvotes))
        / (1 + 3.8416 / (upvotes + downvotes))
    END) STORED;
CREATE INDEX ix_document_shares_quality ON document_shares (quality DESC, created_at DESC);

-- The "Other" category's illustration was replaced in the frontend icon set.
UPDATE share_categories SET emoji = 'ledger' WHERE emoji = 'books';
