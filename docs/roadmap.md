# Roadmap & known limitations

## Done: M1, foundation + OCR

- [x] Registration (student/teacher), login, refresh-token sessions, logout
- [x] Document upload → async OCR → Markdown view / copy / download
- [x] Pluggable OCR engines (RapidOCR CPU, Unlimited-OCR remote/local)
- [x] Docs, specs, ADRs; unit + integration tests in all three services

## Next: hardening M1

- [ ] Run `unlimited-remote` against a real GPU server; tune DPI, batch size and the n-gram logit processor
- [ ] Retry a failed document (`POST /api/documents/{id}/retry`)
- [ ] Periodic sweeper for `PENDING` jobs (today only restart re-queues after a full queue)
- [ ] Login rate limiting / lockout; refresh-token reuse detection
- [ ] Password reset and email verification (needs an email port)
- [ ] Per-user upload quotas
- [ ] Containerise backend/inference/frontend; CI pipeline running the three test suites
- [ ] Structured logging + request ids across backend → inference
- [ ] OpenAPI generation (springdoc) and a typed TS client generated from it

## Later: learning platform

- **Classes**: teachers create classes, students join with a code (`classroom` feature)
- **Assignments & homework**: teacher creates tasks, possibly by importing an OCR'd worksheet
- **Submissions**: students submit text or photos (OCR turns handwriting into text)
- **Exams**: timed assessments, question bank
- **Progress & analytics**: per-student/per-class dashboards (event tracking → analytics store)
- **AI**: generated practice tasks, feedback on submissions, personalised assignment suggestions
  (new inference endpoints; prompts and models behind engine-style interfaces)
- **Real-time**: SSE/WebSocket notifications instead of polling

## Known limitations

| Limitation | Impact | Where it's tracked |
|---|---|---|
| Single backend instance assumed for job processing | can't scale out yet | ADR-0004 |
| Access tokens stay valid up to 15 min after logout/ban | short window | ADR-0002 |
| RapidOCR output is plain text | no tables/headings in dev | ocr-engines guide |
| `unlimited-local` not run on suitable hardware yet | may need adjustments | ADR-0003 |
| Files on local disk | not shared between hosts | `DocumentStorage` port: add S3/MinIO adapter |
