# 0004. In-process async worker pool for document processing

- Status: Accepted (crash recovery and the single-instance limit amended by 0011)
- Date: 2026-10-09

## Context

OCR takes seconds to minutes, so uploads must be asynchronous. A message broker (RabbitMQ,
Kafka, Redis streams) adds infrastructure we don't need at M1 scale with a single backend instance.

## Decision

- Upload stores the file and a `PENDING` row, then publishes `DocumentUploadedEvent`.
- After the transaction commits, a bounded `ThreadPoolTaskExecutor` (`@Async`) runs
  `DocumentProcessor`, which claims the job, calls the inference service **outside** any DB
  transaction, then records the result.
- On startup, `PENDING`/`PROCESSING` documents are re-queued (crash recovery).
- The client polls `GET /api/documents/{id}`.

## Consequences

- No extra infrastructure; simple to reason about and test.
- Only safe with **one backend instance**: with several, two instances could resume the same
  job at startup. Before scaling out, replace the trigger with a broker or a DB-backed queue
  (`SELECT … FOR UPDATE SKIP LOCKED`). Only `DocumentProcessingTriggers` changes.
- A full queue leaves documents `PENDING` until restart; a periodic sweeper is on the roadmap.
- Polling can later be replaced by SSE/WebSocket notifications without changing processing.

## Update (2026-10-12)

The in-process worker pool stays. Two consequences above no longer hold, see
[ADR-0011](0011-contract-first-service-boundary.md):

- Startup no longer requeues every in-flight document. Claiming takes a row lock (only a `PENDING` document can be
  claimed, by exactly one instance), the owning instance refreshes `documents.heartbeat_at` while it works
  (`processing.heartbeat-interval`, 30s), and any instance requeues in-flight documents whose heartbeat is older than
  `processing.lease` (2m), on startup and every `retry-poll`. Several backend instances are therefore safe; a crash
  resumes within one lease, a clean shutdown at once (leases are expired in `@PreDestroy`).
- A full queue no longer waits for a restart: the document stays `PENDING` and is picked up by the same sweep.

A broker remains the next step if DB coordination stops being enough.
