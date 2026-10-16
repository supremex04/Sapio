-- Learners edit sections of the reading view in place (docs/adr/0014-editing-the-reading-view.md).
--
-- (IF NOT EXISTS: some local databases got these columns before the migration was recorded.)
--
-- revision:        bumped by every save; a save names the revision it started from (optimistic concurrency).
-- edited_at:       when the learner last saved the section (null = as extracted).
-- content_version: bumped only by a significant change (small edits accumulate in pending_change until they
--                  add up). Everything AI-written from the text (summaries, notes) records the version it was
--                  written from and is offered for a rewrite when it is behind; nothing is regenerated eagerly.
-- summary_version: the content_version the summary was written from.
ALTER TABLE document_nodes ADD COLUMN IF NOT EXISTS revision INT NOT NULL DEFAULT 0;
ALTER TABLE document_nodes ADD COLUMN IF NOT EXISTS edited_at TIMESTAMPTZ;
ALTER TABLE document_nodes ADD COLUMN IF NOT EXISTS content_version INT NOT NULL DEFAULT 0;
ALTER TABLE document_nodes ADD COLUMN IF NOT EXISTS pending_change INT NOT NULL DEFAULT 0;
ALTER TABLE document_nodes ADD COLUMN IF NOT EXISTS summary_version INT NOT NULL DEFAULT 0;

-- Notes cover a section's subtree: source_stamp is the sum of the subtree's content versions when written.
-- It only grows, so notes are behind exactly when the current sum is larger.
ALTER TABLE study_notes ADD COLUMN IF NOT EXISTS source_stamp INT NOT NULL DEFAULT 0;
