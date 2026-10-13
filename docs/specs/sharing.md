# Spec: Sharing and Explore

Learners publish their own ready documents to **Explore**, filed under a category. Other learners browse
Explore and **import** a document: they get an independent copy in their library, with the original's
learning path, lessons, question bank and notes. Nothing they do afterwards changes the original, and
nothing the sharer does changes their copy. Design: [ADR-0012](../adr/0012-import-shared-documents-as-independent-copies.md).

Covered: share / unshare with a category and note, Explore (browse, filter, search, sort), detail, import,
"Imported" tag, and quality signals: up / down votes, "Top rated" ranking and comments.

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
| SH-10 | One vote per learner and share: up (`1`), down (`-1`) or none (`0` takes it back). Sharers cannot vote on their own share (`409`). |
| SH-11 | **Top rated** orders by the lower bound of the Wilson score interval (95%) of upvotes, so 45 up / 5 down ranks above 1 up / 0 down and early votes don't dominate. Stored as a generated, indexed column (`document_shares.quality`). |
| SH-12 | Anyone signed in may comment (1 to `sapio.sharing.comments.max-length` characters, default 2000), newest first. The author or the sharer may delete a comment; the sharer's comments are marked. |
| SH-13 | Flood guard: at most `sapio.sharing.comments.per-minute` (default 5) comments per learner per minute, across shares (`429 RATE_LIMITED`). |
| SH-14 | Vote and comment counters live on the share and change in the same transaction as the vote or comment, after the share row is locked, so concurrent votes never lose an update. |

## API

All endpoints require authentication. Errors follow [api-conventions.md](api-conventions.md).

| Method & path | Body / query | Response |
|---|---|---|
| `GET /api/explore/categories` | | `[{slug, name, emoji}]` in display order |
| `GET /api/explore` | `category`, `q` (title/summary contains), `sort=new\|top\|popular`, `page`, `size` (≤ 60) | `PageResponse<ShareSummary>` |
| `GET /api/explore/{shareId}` | | `ShareDetail` |
| `POST /api/explore/{shareId}/import` | | `201` new copy / `200` existing copy: `{documentId, alreadyImported}` |
| `PUT /api/explore/{shareId}/vote` | `{value: 1 \| -1 \| 0}` | `Votes` |
| `GET /api/explore/{shareId}/comments` | `page`, `size` | `PageResponse<Comment>`, newest first |
| `POST /api/explore/{shareId}/comments` | `{body}` | `201 Comment` |
| `DELETE /api/explore/{shareId}/comments/{commentId}` | | `204` (author or sharer; `403` otherwise) |
| `GET /api/shares` | | the caller's shares: `[MyShare]` |
| `PUT /api/documents/{id}/share` | `{category, description?}` | `MyShare` (create or update) |
| `DELETE /api/documents/{id}/share` | | `204` |

```jsonc
// ShareSummary
{ "id", "title", "summary", "category": {"slug", "name", "emoji"}, "kind", "language", "pageCount",
  "topicCount", "sharedBy", "sharedAt", "importCount",
  "votes": {"upvotes", "downvotes", "myVote"},  // myVote: 1, -1 or 0
  "commentCount",
  "mine": false,                // shared by the caller: no import
  "importedDocumentId": null }  // the caller's copy, if any
// ShareDetail
{ "share": ShareSummary, "description", "topics": ["…"], "updatedAt" }
// MyShare
{ "id", "documentId", "category", "description", "importCount", "upvotes", "downvotes", "commentCount",
  "sharedAt", "updatedAt" }
// Comment
{ "id", "body", "author", "createdAt", "bySharer", "mine", "canDelete" }
```

`DocumentResponse` (all document endpoints) gains `origin`: `null` for uploads, else
`{shareId | null, sharedBy, importedAt}`, shown as the **Imported** tag.

## Data

- `share_categories(slug, name, emoji, position)`: seeded by migration; add a row to add a category.
- `document_shares`: one row per shared document (cascade-deleted with it), category, note, listing snapshot,
  denormalised `import_count` (incremented after each import in its own short transaction).
- `share_votes(share_id, user_id, value ±1)` (one per learner) and `share_comments(id, share_id, author_id,
  body, created_at)`, both cascade-deleted with the share; counters `upvotes`, `downvotes`, `comment_count` and
  the generated `quality` on `document_shares`.
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
