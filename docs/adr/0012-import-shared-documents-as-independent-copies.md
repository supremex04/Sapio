# 0012. Import shared documents as independent copies

- Status: Accepted
- Date: 2026-10-12

## Context

Learners can share their documents to Explore and others import them (see [sharing.md](../specs/sharing.md)).
The importer must be able to study, generate Expand / Revision notes, lessons and tests on the document without
changing the original, and the sharer must stay free to keep studying, re-share or delete theirs. Every query in
the knowledge store and study modules is scoped by document and owner ([ADR-0007](0007-shared-study-material-content-vs-learner-state.md)).

Options:

1. **Reference the original** and add an ownership exception for importers: no duplication, but every
   generated artefact (lessons, notes) would need a per-user overlay, every query would need to know about
   imports, and deleting the original would break all imports.
2. **Copy-on-write**: share rows until someone writes, then copy. Least storage, but every write path in every
   module needs to know about it.
3. **Deep copy at import**: duplicate the document's rows and files for the importer.

## Decision

Option 3. Importing copies the document, its file, the knowledge store (including embeddings and figure files)
and the study content (learning path, lessons, questions, notes) inside one `REPEATABLE READ` transaction, with
new ids and remapped references. Learner state is never copied. Explore lists a **snapshot** taken at sharing
time, so browsing never reads the sharer's live data.

The copy is orchestrated by the document module (`DocumentCloner`) and performed by each module for its own
tables (`DocumentCopyStep`), keeping module boundaries ([ADR-0005](0005-modular-monolith-backend.md)). Rows are
copied with `INSERT … SELECT` over the table's actual columns, so schema additions are copied automatically.

## Consequences

- The copy is a normal document: every existing feature, access check and query works on it unchanged, and it
  is ready instantly (no OCR, LLM or embedding calls; a 30-page document imports in ~0.4 s).
- Complete isolation, in both directions, including deletion.
- Storage grows with each import (rows, vectors, the file and figure images). Fine at current scale; content-
  addressed files with reference counting can remove the file duplication later without changing the model.
- Improvements the sharer makes after an import (new lessons, notes) do not reach existing copies. A future
  "update available" flow could re-import into a fresh copy.
- Per-document data added by a new module needs a `DocumentCopyStep`; otherwise it is not carried over.
