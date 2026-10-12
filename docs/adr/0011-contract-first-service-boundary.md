# 0011. Contract-first service boundary and DB-coordinated processing

- Status: Accepted (amends 0004)
- Date: 2026-10-12

## Context

The backend ↔ inference boundary ([ADR-0001](0001-polyglot-three-service-architecture.md)) was held together by
convention, and several failures showed it:

- Errors were FastAPI `{"detail": "..."}` and the backend classified them by status class alone
  (`isClientError()`): a wrong internal API key (401) was reported to the learner as "document rejected", and
  operator details could leak into user messages. Nothing said whether a failure was worth retrying or when.
- The wire shape existed only in prose (`inference-api.md`) and in two sets of hand-written models. A field
  renamed on one side was silently dropped by the other. In fact `StudyAi` specs were sending
  `ContextAssembler.Section` / `Figure` records (with internal DB ids `nodeId` / `figureId` and `label` / `title`)
  that the service ignored.
- A daily quota exhaustion ("retry in 17 h") burned all three automatic retries (1m, 4m, 15m) in 20 minutes.
- One learner action could not be followed across the two services' logs.
- Document processing was safe only with one backend instance (ADR-0004): every instance requeued all in-flight
  documents at startup, so a second instance would steal live work.

## Decision

1. **Typed error envelope and taxonomy.** Every non-2xx inference response is
   `{"code", "message", "retryable", "requestId"}`, rendered in one place (`api/errors.py`). Domain code raises
   `DocumentError` (422 `INPUT_REJECTED`, user-safe), `EngineError` (503 `MODEL_UNAVAILABLE`, retryable, optional
   `Retry-After`) or `ConfigurationError` (500 `MISCONFIGURED`, operator-facing); framework errors map to
   `CONTRACT_VIOLATION`, `UNAUTHORIZED`, `INPUT_TOO_LARGE`, …; anything else is a generic 500 `INTERNAL_ERROR`. The
   backend decodes it into `InferenceException.Kind` and only ever shows learners messages of user-safe kinds. AI
   ports throw a technology-neutral `AiException` (input rejected / retryable / retry-after), rendered as
   `AI_INPUT_REJECTED` (422) or `AI_UNAVAILABLE` (503, `Retry-After`).
2. **Committed contract, tested from both sides.** `contracts/inference-openapi.json` is generated from the FastAPI
   app. The provider test fails when it differs from the code; the consumer test (`InferenceContractTest`) checks
   every backend DTO against it (fields, required fields, enums, types, endpoints, error envelope, contract major).
   `/health` reports `contractVersion`; the backend checks the major at startup.
3. **Ports own their wire records.** A record that is serialised to another service is declared on the port and
   carries only what that service needs (`StudyAi.Source`, `StudyAi.SourceFigure`); domain and persistence types
   never go on the wire.
4. **Request correlation.** `X-Request-Id` is accepted or generated at the backend edge, kept in the MDC, forwarded to
   the inference service, inherited by the document jobs an upload starts, and shown on every log line and error body
   of both services.
5. **DB-coordinated processing as the scale-out mechanism, before a broker.** A worker claims a document under a row
   lock (only `PENDING` can be claimed), the owning instance refreshes `documents.heartbeat_at`
   (`processing.heartbeat-interval`), and any instance requeues in-flight documents whose heartbeat is older than
   `processing.lease`. Shutdown expires the instance's leases. In the background, everything except rejected input is
   transient, and an automatic retry waits at least the AI's retry hint (capped at 24 h).

## Consequences

- Failures are classified by who can fix them (learner, nobody/wait, operator, developer), not by status class;
  operator details stay in logs, which quote the same request id the user sees as "ref".
- Drift between the services fails a build instead of silently dropping data. The cost: every API change means
  regenerating the contract file (`scripts/export_openapi.py`) and, when it breaks callers, bumping the contract
  major on both sides together.
- Ports need explicit conversions (`StudyAi.sources(...)`, `StudyAi.figures(...)`): a little more code, but internal
  ids no longer leave the backend through those records.
- Several backend instances can run without a broker or extra infrastructure. Trade-offs:
  - a crashed instance's documents resume only after up to one lease (2m by default); a clean shutdown resumes them
    at once;
  - concurrency is capped per instance (each has its own executor); total inference load grows with the number of
    instances;
  - heartbeats add a small periodic write per instance with in-flight work;
  - some state stays per instance: `KnowledgeIndexer`'s re-embedding de-duplication only works within one instance
    (duplicates across instances are harmless because the writes are idempotent).
- A misconfiguration fixed by an operator heals itself through the automatic retries, but a long-standing one uses
  them up; the user can still retry by hand.
- If DB coordination becomes a bottleneck (many instances, fairness, priorities), replace
  `DocumentProcessingTriggers` with a broker; `DocumentProcessor` does not change.
