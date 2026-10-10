-- Section notes come in styles: EXPAND (in-depth explanation) and REVISION (condensed revision notes).
-- Notes generated so far were in-depth study notes.
ALTER TABLE study_notes ADD COLUMN style VARCHAR(20) NOT NULL DEFAULT 'EXPAND';
ALTER TABLE study_notes ALTER COLUMN style DROP DEFAULT;
ALTER TABLE study_notes DROP CONSTRAINT study_notes_node_id_key;
ALTER TABLE study_notes ADD CONSTRAINT uq_study_notes_node_style UNIQUE (node_id, style);
