-- Visualizations are narrated: each beat's caption is spoken (Kokoro TTS in the renderer).
ALTER TABLE visualizations ADD COLUMN IF NOT EXISTS narrated BOOLEAN NOT NULL DEFAULT FALSE;
