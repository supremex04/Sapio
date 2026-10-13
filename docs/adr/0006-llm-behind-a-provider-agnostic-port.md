# 0006. LLM behind a provider-agnostic port; Gemini first

- Status: Accepted
- Date: 2026-10-09

## Context

Learn and Test need an LLM to write lessons, questions, short-answer grades, report narratives
and tutor answers. Models and vendors change quickly. In October 2026 `gemini-2.5-*` was already
retired for new keys and `gemini-3.8-flash` was intermittently overloaded. We need structured,
validated output, not free text.

## Decision

- The inference service defines `LlmClient.generate(system, prompt, schema) -> PydanticModel`.
  The first implementation is `GeminiClient`: `generateContent` with `responseJsonSchema` generated from
  the Pydantic model, a low thinking level for latency, retries with backoff on 429/5xx or invalid
  output, then fallback through a configured model list.
- Default model `gemini-3.5-flash` (≈2–8 s per call with structured output in our tests); fallbacks
  `gemini-3.8-flash`, `gemini-3.5-flash-lite`. All configurable via `SAPIO_INFERENCE_GEMINI_*`.
- `StudyService` treats LLM output as untrusted: it validates chunk references, drops malformed
  questions, shuffles multiple-choice options (LLMs favour certain answer positions), clamps
  grades into their verdict band, and enforces lesson structure (warm-up first, explain-back last).
- The backend talks to the inference service only through its own `StudyAi` port.

## Consequences

- Switching vendor or adding a local model = one new `LlmClient` class + a registry entry.
- Answer keys and grading rules stay deterministic and testable; only content is generated.
- Costs and latency depend on the vendor; lessons are cached per topic and questions are stored,
  so each piece of content is generated once.
- The API key lives only in the inference service's environment (`inference/.env`, gitignored).

> **Note (2026-10-10):** models are no longer configured with `SAPIO_INFERENCE_LLM_PROVIDER` /
> `SAPIO_INFERENCE_GEMINI_*`; providers, per-task routes and cross-vendor fallbacks live in
> `inference/settings.yaml` ([ADR-0010](0010-vendor-neutral-model-configuration.md),
> [configuration.md](../guides/configuration.md)). Only the key, `SAPIO_INFERENCE_GEMINI_API_KEY`, kept its name.
