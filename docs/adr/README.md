# Architecture Decision Records

Short records of significant decisions: context, the decision, and its consequences. They are
immutable once accepted; to change a decision, add a new ADR that supersedes the old one.

| # | Decision | Status |
|---|---|---|
| [0001](0001-polyglot-three-service-architecture.md) | Three services: Spring Boot backend, Python inference, React/TS frontend | Accepted |
| [0002](0002-jwt-access-tokens-with-rotating-refresh-sessions.md) | Short-lived JWT + rotating refresh-token sessions in an HttpOnly cookie | Accepted |
| [0003](0003-pluggable-ocr-engines-in-a-python-service.md) | OCR engines behind one interface; Unlimited-OCR primary, RapidOCR fallback | Accepted |
| [0004](0004-in-process-async-document-processing.md) | In-process async worker pool instead of a message broker (for now) | Accepted |
| [0005](0005-modular-monolith-backend.md) | Backend as a modular monolith, package-by-feature | Accepted |
| [0006](0006-llm-behind-a-provider-agnostic-port.md) | LLM behind a provider-agnostic port; Gemini first | Accepted |
| [0007](0007-shared-study-material-content-vs-learner-state.md) | One study material for both modes; content separate from learner state | Accepted |
| [0008](0008-progress-via-in-transaction-domain-events.md) | Progress and analytics via in-transaction domain events | Accepted |

New ADR: copy [template.md](template.md) to `NNNN-short-title.md`.
