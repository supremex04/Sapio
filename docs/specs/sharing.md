# Spec: Sharing and Explore

Learners publish their own ready documents to **Explore**, filed under a category. Other learners browse
Explore and **import** a document: they get an independent copy in their library, with the original's
learning path, lessons, question bank and notes. Nothing they do afterwards changes the original, and
nothing the sharer does changes their copy. Design: [ADR-0012](../adr/0012-import-shared-documents-as-independent-copies.md).

Iteration 1 (this spec): share / unshare with a category and note, Explore (browse, filter, search, sort),
detail, import, "Imported" tag. Later: votes, comments (see [roadmap](../roadmap.md)).

## Rules

| # | Rule |
|---|---|
| SH-1 | Only the owner shares, and only a `READY` document they **uploaded** (imported copies cannot be re-shared: `409 INVALID_STATE`). It must have a learning path (`409 MATERIAL_NOT_READY` otherwise). |
| SH-2 | A category from `GET /api/explore/categories` is required (`400 VALIDATION_FAILED` otherwise); the note is optional, ≤ 500 characters. |
| SH-3 | One share per document. Sharing again updates category and note and refreshes the listing snapshot (title, summary, kind, language, pages, topic titles). |
| SH-4 | Explore shows the snapshot only. The original document, its text and the sharer's progress stay private (`404` for others). |
| SH-5 | Import copies: the document and its file; the knowledge store (tree, chunks **with embeddings**, figures **with their own image files**, term index); the study content (learning path, generated lessons, question bank, Expand / Revision notes). Ids are new; references (foreign keys, id arrays, `figure:<id>` in markdown) point into the copy. No AI calls: the copy is `READY` at once. |
| SH-6 | Never copied: learner state (topic progress, attempts, assessments, review cards, XP events). |
| SH-7 | A learner imports a share once; importing again returns the existing copy (`200`, `alreadyImported: true`). Deleting the copy allows importing again. Sharers cannot import their own share (`409`). |
| SH-8 | Withdrawing a share (or deleting the original) removes it from Explore; existing copies stay, still tagged with who shared them (`origin.shareId` becomes `null`). |
| SH-9 | Copies are fully independent: deleting either document never affects the other (separate files, rows and ids). |

## API

All endpoints require authentication. Errors follow [api-conventions.md](api-conventions.md).

| Method & path | Body / query | Response |
|---|---|---|
| `GET /api/explore/categories` | | `[{slug, name, emoji}]` in display order |
| `GET /api/explore` | `category`, `q` (title/summary contains), `sort=new\|popular`, `page`, `size` (≤ 60) | `PageResponse<ShareSummary>` |
| `GET /api/explore/{shareId}` | | `ShareDetail` |
| `POST /api/explore/{shareId}/import` | | `201` new copy / `200` existing copy: `{documentId, alreadyImported}` |
| `GET /api/shares` | | the caller's shares: `[MyShare]` |
| `PUT /api/documents/{id}/share` | `{category, description?}` | `MyShare` (create or update) |
| `DELETE /api/documents/{id}/share` | | `204` |

```jsonc
// ShareSummary
{ "id", "title", "summary", "category": {"slug", "name", "emoji"}, "kind", "language", "pageCount",
  "topicCount", "sharedBy", "sharedAt", "importCount",
  "mine": false,                // shared by the caller: no import
  "importedDocumentId": null }  // the caller's copy, if any
// ShareDetail
{ "share": ShareSummary, "description", "topics": ["…"], "updatedAt" }
// MyShare
{ "id", "documentId", "category", "description", "importCount", "sharedAt", "updatedAt" }
```

`DocumentResponse` (all document endpoints) gains `origin`: `null` for uploads, else
`{shareId | null, sharedBy, importedAt}`, shown as the **Imported** tag.

## Data

- `share_categories(slug, name, emoji, position)`: seeded by migration; add a row to add a category.
- `document_shares`: one row per shared document (cascade-deleted with it), category, note, listing snapshot,
  denormalised `import_count` (incremented after each import in its own short transaction).
- `documents.origin_share_id` (→ share, `ON DELETE SET NULL`), `origin_owner_name`, `imported_at`; unique
  `(owner_id, origin_share_id)` makes a double import impossible even under concurrency.

## How the copy works

`DocumentCloner` (document module) copies the document row and file, then runs every `DocumentCopyStep`
bean in order, in one `REPEATABLE READ` transaction (a consistent snapshot of the source):

| Order | Step | Copies |
|---|---|---|
| 100 | `KnowledgeCopyStep` | `document_nodes`, `document_chunks`, `document_figures` (+ files), `document_terms` |
| 200 | `StudyContentCopyStep` | `study_materials`, `study_topics`, `study_lessons`, `study_questions`, `study_notes`; rewrites `figure:<id>` in lesson and note markdown |

Steps copy rows with `DocumentCopy.copyRows(table, overrides, where)`, which reads the table's columns from
the database and copies every column except the overridden ones (ids, foreign keys, owner). A column added by
a later migration is therefore copied without code changes. Old → new ids live in a transaction-scoped temp
table (`copy_ids`); `mapped(...)` / `mappedArray(...)` remap references. Files copied during a failed import
are deleted on rollback. A module that adds per-document data adds a step; nothing else changes.
