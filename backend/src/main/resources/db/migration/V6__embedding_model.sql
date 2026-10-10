-- Which embedding model produced each vector. Vectors from different models live in different spaces and must
-- never be compared: search only matches vectors of the query's model, and stale vectors are re-embedded.
ALTER TABLE document_chunks ADD COLUMN embedding_model TEXT;
ALTER TABLE document_figures ADD COLUMN embedding_model TEXT;

-- Everything embedded so far came from the only model used before this migration.
UPDATE document_chunks SET embedding_model = 'gemini:gemini-embedding-2' WHERE embedding IS NOT NULL;
UPDATE document_figures SET embedding_model = 'gemini:gemini-embedding-2' WHERE embedding IS NOT NULL;
