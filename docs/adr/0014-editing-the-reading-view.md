# 0014. Editing the reading view: in-place Markdown, minimal re-indexing, AI rewrites on request

- Status: Accepted
- Date: 2026-10-14

## Context

Sapio's promise is fast notes, learning and testing from anything you upload. Note tools such as Notion let people
fix and extend their notes; Sapio's reading view was read-only. Learners want to correct an OCR slip, add a line
of their own, a link or a formula, without leaving the page.

The reading view is not a document file. Each section (a tree node) is stored as **chunks** that are also the
search index (keyword vector and embedding each), with figures anchored after chunks. A lot is derived from that
text: the section summary and key terms (one AI call), Expand / Revision notes (one AI call each), and the course
(topics, lessons, checks, tests) that holds the learner's attempts, XP and review cards. A naive "save the text,
rebuild everything" would make a one-character typo fix cost several AI calls and could wipe progress.

## Decision

**Edit unit: one section, as Markdown.** The editor shows exactly what the reading view renders: the section's
Markdown with figures as `![caption](figure:<id>)` lines (shown in the editor as the figure; move or delete the
line to move or delete the figure). Markdown already covers what learners need, and the renderer already supports
it: GFM (links, lists, tables, task lists, strikethrough), code blocks and LaTeX math (`$…$`, `$$…$$`). A WYSIWYG
model (ProseMirror) would have to round-trip tables, math and figure references losslessly; Markdown source with
rich styling and shortcuts gives most of the feel with none of that risk.

**Low friction.** Right-click any section (or the pencil on hover) and choose *Edit section*: the editor opens in
place with the cursor where you clicked (the rendered text before the click is located in the Markdown source).
`Ctrl+S` saves and keeps editing, `Ctrl+Enter` saves and closes, `Esc` closes (a second `Esc` discards changes).
Formatting shortcuts and a toolbar wrap or toggle Markdown (bold, italic, strikethrough, code, links, headings,
lists, checklists, quotes, inline and display formulas, code blocks, tables); pasting a URL over selected text links
it; a formula under the caret is previewed rendered while typing; a Preview toggle shows the result. Unsaved text is
kept as a draft in the browser and offered back next time. The editor (CodeMirror 6) is loaded only when editing.

**Saving changes as little as possible** (`PUT /api/documents/{id}/sections/{nodeId}`):

| Derived data | Cost | On save |
|---|---|---|
| Keyword index | free | updated with the text (generated column) |
| Embeddings | very cheap | only for new or changed chunks: unchanged chunks, matched by their paragraphs, keep their id and vector; new paragraphs are packed into new chunks as extraction does; embedding happens in the background after commit |
| Summary, key terms | one AI call | not rewritten; marked behind after a significant change, rewritten when something is built on them |
| Expand / Revision notes | one AI call each | never deleted; shown with "Written before your latest changes · Rewrite"; rewritten only when the learner asks |
| Topics, lessons, checks, tests | several AI calls, hold progress | untouched |

**Significance.** Each save measures the changed characters (paragraphs aligned by longest common subsequence; an
edited paragraph counts only its changed middle). Changes accumulate per section until they reach 400 characters
or 15% of the section, which bumps its *content version*. Summaries record the version they were written from;
notes record the sum of their subtree's versions (it only grows), so a section's notes, and those of the sections
containing it, are flagged as behind exactly when the covered text changed significantly. Typo fixes never flag
anything.

**Concurrency.** Every save bumps a per-section *revision*; a save names the revision it started from and a stale
one is rejected (`409 EDIT_CONFLICT`, the text is kept as a browser draft). Editing is available once a document is
`READY`, so it never races the processing pipeline. Only the owner can edit (imported copies are the importer's own).

## Consequences

- A typo fix costs one small embedding call; a rewrite costs nothing more until the learner asks for new notes.
- The Raw text view stays the extraction output (what was read from the file); the reading view is the learner's
  version. Re-processing a document from its file would replace the edits (not offered today).
- Lessons and tests built before an edit keep the old wording. A later step can reuse the same content versions to
  offer "update this lesson" on request.
- Small new paragraphs between unchanged chunks become small chunks of their own; retrieval handles them fine, and
  no unchanged neighbour is re-embedded to merge them.
