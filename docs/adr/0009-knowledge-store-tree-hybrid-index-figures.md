# 0009. Knowledge store: document tree + hybrid index + linked figures, in PostgreSQL

- Status: Accepted (supersedes the flat-chunk part of 0007)
- Date: 2026-10-10

## Context

Students upload curricula whose value is in their **structure** (units, chapters, sections) and their
**visuals** (diagrams, charts). M2 flattened documents into large chunks and re-sent raw text to the LLM
for every lesson and test. We need: parse once, navigate by chapter/section, retrieve precisely, keep
figures in context, and produce long-form notes and answers cheaply. Reference:
*RAG Architecture for Student Learning Platforms* (project root).

## Decision

1. **Hierarchical tree (parent–child)**: `document_nodes` with an `ltree` path (`2.3.1`) and the book's
   own label; small leaf chunks under each node. Retrieval searches leaves and expands to parents
   (small-to-big). Notes follow the tree, so they never come out of curriculum order.
2. **Hybrid retrieval in one database**: Postgres full-text (`tsvector` + GIN) as the inverted index
   for exact terms, formulas and acronyms; **pgvector** (HNSW, cosine, 768-d) for meaning; fused
   with Reciprocal Rank Fusion. No separate search or vector service.
3. **Multimodal embeddings**: `gemini-embedding-2` embeds text and images into one space, so
   "show me the diagram of X" can match figures directly; figures also get a vision-model description
   that serves as alt text, keyword-searchable text and LLM context (no pixels sent at generation time).
4. **Figures as first-class rows** linked to node + anchor chunk, files in `DocumentStorage`
   (object-storage-ready), served through **HMAC-signed URLs** (like S3 presigned URLs).
5. **Structured context injection** (YAML-like sections + figures with ids) and output validation
   (unknown figure ids and citations are stripped).
6. **Key-term index** from bottom-up summaries instead of a full knowledge graph for now.

## Consequences

- Everything stays in PostgreSQL: one backup, transactional consistency between tree, chunks and
  figures; the image becomes `pgvector/pgvector:pg17` (same Postgres 17).
- Lessons/tests/notes/answers get small, relevant context: lower cost, fewer hallucinations, figures included.
- Ingestion does more work up front (figure descriptions, summaries, embeddings: batched LLM calls).
  It runs once per document and is resumable.
- Existing M2 study materials are rebuilt: migration V4 resets processed documents to PENDING (dev data only).
- A full **knowledge graph** (concept nodes + PREREQUISITE_OF / CONTRASTS_WITH edges, GraphRAG) is
  deferred. The key-term index and section summaries are its seed; see roadmap.
- A cross-encoder reranker is deferred. RRF fusion plus small-to-big covers the current scale.
