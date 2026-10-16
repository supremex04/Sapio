-- Visualization engines (docs/adr/0017-visualization-engines.md): besides animations, molecules (RDKit), Protein
-- Data Bank structures and traced programs, each shown in its own viewer in time with a narration track.
ALTER TABLE visualizations ADD COLUMN IF NOT EXISTS engine_choice VARCHAR(16) NOT NULL DEFAULT 'AUTO'; -- asked for
ALTER TABLE visualizations ADD COLUMN IF NOT EXISTS engine VARCHAR(16);   -- used (once planned)
ALTER TABLE visualizations ADD COLUMN IF NOT EXISTS plan_json TEXT;       -- the engine's data from the storyboard
ALTER TABLE visualizations ADD COLUMN IF NOT EXISTS scene_json TEXT;      -- what the viewer shows (built)
ALTER TABLE visualizations ADD COLUMN IF NOT EXISTS audio_key VARCHAR(512);     -- narration track (non-video engines)
ALTER TABLE visualizations ADD COLUMN IF NOT EXISTS structure_key VARCHAR(512); -- protein structure (mmCIF)
UPDATE visualizations SET engine = 'ANIMATION' WHERE engine IS NULL AND storyboard_json IS NOT NULL;
