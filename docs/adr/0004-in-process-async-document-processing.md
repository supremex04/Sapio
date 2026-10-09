# 0004. In-process async worker pool for document processing

- Status: Accepted
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
