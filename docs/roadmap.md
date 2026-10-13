# Roadmap & known limitations

## Done

**M1: foundation + OCR**
- [x] Registration (student/teacher), login, refresh-token sessions, logout
- [x] Document upload → async extraction → Markdown view / copy / download
- [x] Pluggable OCR engines (RapidOCR CPU, Unlimited-OCR remote/local)

**M2: study a document (Learn & Test)**
- [x] Hybrid extraction (native PDF text + OCR for scans), retry of failed documents
- [x] Study material: chunks + topics shared by both modes (ADR-0007)
- [x] Learn: grounded lessons with warm-up pretest, CONCEPT/EXAMPLE/ANALOGY sections, checks, explain-back, tutor
- [x] Test: interleaved, grounded questions (choice + AI-graded short answer), confidence, report with calibration
- [x] Spaced review (Leitner), mastery per topic, single "next best step"
- [x] XP, levels, forgiving streaks, daily goal, heartbeat active time, logins/day, learning-event log
- [x] Design system (light/dark/system), learning-science-driven UX ([learning-science.md](architecture/learning-science.md))

**M3: knowledge store & configurability**
- [x] Knowledge store: document tree, leaf chunks, figures, term index, hybrid retrieval ([ADR-0009](adr/0009-knowledge-store-tree-hybrid-index-figures.md)); notes & ask-your-document API
- [x] Vendor-neutral AI: providers (Gemini, OpenAI-compatible), per-task model routes, cross-vendor fallbacks, quota-aware switching ([ADR-0010](adr/0010-vendor-neutral-model-configuration.md))
- [x] Backend tunings in validated `@ConfigurationProperties` (retrieval, context budgets, indexing batches, auto-retry delays) ([configuration.md](guides/configuration.md))
- [x] Embedding model tracked per vector; background re-embed on the next search after a model switch; startup dimension check
- [x] Figure similarity threshold for semantic figure matches (`ved.knowledge.retrieval.figure-min-similarity`)
- [x] Automatic retries of transiently failed documents (AI busy / out of quota)
- [x] Architecture docs: document parsing, knowledge storage, retrieval, backend ↔ inference integration; [capabilities.md](capabilities.md)
- [x] Contract-first service boundary: typed error envelope, committed OpenAPI contract tested from both sides, `X-Request-Id` correlation ([ADR-0011](adr/0011-contract-first-service-boundary.md))
- [x] Several backend instances: row-locked claim + heartbeat lease for document processing; retries honour quota-reset hints
- [x] Document **Preview**: reading view of the whole document (sections, figures in place, contents), the original upload, raw text; per-section **Expand** / **Revision** notes; inline-HTML cleanup of extracted text

## Next: hardening M2

- [ ] Frontend **Read mode**: tree navigation, search, term index, ask-your-document (API exists; notes are in the Preview)
- [ ] Rate limits / quotas on AI endpoints per user (cost control); per-request cost logging
- [ ] Generate the first lesson right after analysis (removes the first-open wait)
- [ ] Stream lesson generation (SSE) instead of a 10–30 s spinner
- [ ] Prompt/eval harness: golden documents + automatic checks for grounding, answer validity and difficulty
- [ ] Unlimited-OCR on a GPU server; compare extraction quality on real scans
- [ ] Material versioning (re-analysis currently replaces the material and its question history)
- [ ] Login rate limiting / lockout; refresh-token reuse detection; password reset (email port)
- [ ] OpenAPI generation (springdoc) + generated TS client; CI running all three suites; containers

## Recordings and more formats

- [x] Word documents (.docx) from their own structure
- [x] Lecture audio / video: faster-whisper speech-to-text, AI study notes, transcript, seekable playback (ADR-0013)
- [ ] Speaker turns (diarisation) for discussions and Q&A; slide frames from video as figures
- [ ] PowerPoint (.pptx) decks

## Next: community

- [x] Share to Explore with a category; import as an independent copy; "Imported" tag (ADR-0012)
- [x] Upvotes / downvotes, "Top rated" (Wilson lower bound) and comments on shared documents
- [ ] Moderation: report a share, hide abusive comments

## Later: learning platform

- **Classes & sharing**: teachers share materials with classes (extend `MaterialAccess`)
- **Teacher analytics**: per-student mastery, weak topics, active time (data already in `learning_events`)
- **Personalised assignments**: teacher or AI assigns review sets / tests targeting weak topics
- **Exams**: timed, proctoring-light assessments; question bank curation
- **Gamified flows**: achievements tied to learning behaviours (e.g. "10 spaced reviews on time"),
  gentle streak protection, class challenges (relatedness), always within the ethos in learning-science.md
- **Learning-effectiveness dashboard**: retention on spaced reviews vs first attempts, calibration over time
- **Real-time**: SSE/WebSocket notifications instead of polling

## Known limitations

| Limitation | Impact | Where it's tracked |
|---|---|---|
| Processing concurrency is per instance (own executor); a crashed instance's documents wait up to one lease (2 min) | uneven load; short delay after a crash | ADR-0011 |
| Progress listeners run in the learner's transaction | a progress bug fails the action | ADR-0008 |
| Lesson/test generation is synchronous (10–40 s) | waiting UI | above: SSE / pre-generation |
| No per-user AI quotas | cost exposure | above |
| Onboarding "goal set" is tracked per device (localStorage) | may re-show on a new device | frontend `/welcome` |
| Access tokens stay valid up to 15 min after logout/ban | short window | ADR-0002 |
| RapidOCR output is plain text | weaker structure on scans | ocr-engines guide |
| Files on local disk | not shared between hosts | `DocumentStorage` port: add S3/MinIO adapter |
