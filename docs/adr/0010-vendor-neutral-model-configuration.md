# 0010. Vendor-neutral model configuration: providers, per-task routes, fallbacks

- Status: Accepted (extends 0006; replaces its `VED_INFERENCE_GEMINI_*` configuration)
- Date: 2026-10-10

## Context

ADR-0006 put the LLM behind a port, but configuration still assumed one vendor: a Gemini client,
a model list in `VED_INFERENCE_GEMINI_*` env vars, and the same model for every job. In practice:

- Jobs differ: bulk indexing (summaries of hundreds of sections) wants a cheap model; notes and
  lessons want a strong one; figure descriptions need vision.
- Free and paid tiers run out of **daily** quota. Waiting on a "retry in 17 h" hint stalls indexing;
  the only useful reaction is another model, possibly from another vendor.
- Schools and self-hosters want OpenAI, OpenRouter or fully local models (Ollama, vLLM).
- Embeddings are stored per document. Switching the embedding model silently mixed incomparable vectors.
- Backend tunings (RRF constant, candidate counts, context budgets, batch sizes, retry delays) were
  hard-coded constants, so tuning meant a rebuild.

## Decision

**Inference service**

- `settings.yaml` declares named **providers** (`type: gemini | openai`, where `openai` = any
  OpenAI-compatible API) with endpoint, timeouts, retry policy, headers and JSON mode.
- Every request carries a **task** (`outline`, `lesson`, `questions`, `grade`, `feedback`, `answer`,
  `notes`, `figures`, `summaries`). `llm.tasks.<task>` or `llm.default` is a **route**: a primary model
  plus ordered **fallbacks**, across providers. Provider-specific options (`thinking_level`,
  `reasoning_effort`, …) sit on the model reference.
- One shared retry policy: honour server hints, back off exponentially, and treat "unknown model" or
  an exhausted quota (hint > 2 × the backoff cap, or `insufficient_quota`) as **model unavailable** →
  next model immediately.
- Keys are referenced by **env var name** (`api_key_env`), never stored in YAML.
- Precedence: env `VED_INFERENCE_*` (`__` for nesting) > `.env` > `settings.yaml` (`VED_INFERENCE_CONFIG`
  picks the file) > code defaults. Pydantic validates the whole tree at startup, including that
  every route names a defined provider.
- `embeddings` names one provider/model with fixed `dimensions` (768, matching `vector(768)`) and a
  `multimodal` flag. `/health` and `/v1/index/embed` report the model and dimensions.

**Backend**

- Every vector stores the `embedding_model` that produced it. Search compares only vectors of the
  query's model; a document with stale vectors is re-embedded in the background on its next search.
  A startup check logs an error when the inference service's dimensions don't fit the database.
- Tunings move to validated `@ConfigurationProperties` records (`ved.knowledge.*`, `ved.study.context.*`,
  `ved.documents.processing.*`) in `application.yml`, overridable by env vars through relaxed binding.

## Alternatives considered

| Alternative | Why not |
|---|---|
| LiteLLM or LangChain as the abstraction | large, fast-moving dependency for two wire formats we need; their structured-output, retry and fallback semantics don't match ours (quota-aware fallback, schema validation per use-case); harder to test with fakes |
| Env-only configuration | nested routes and fallback lists are unreadable as env vars; no single file to review. Env vars stay as overrides |
| Hard-coded constants / one model per vendor class | every model switch or tuning is a code change and a deploy; no per-task choice |
| One global model with fallbacks (no tasks) | forces the most expensive model onto bulk indexing, or the cheapest onto notes and grading |

## Consequences

- Switching vendor, adding a local model, or using a cheaper model for one task is a YAML change;
  adding a new *wire format* is one `Provider` subclass + a branch in `llm/registry.py`.
- Quota exhaustion degrades to the next model instead of failing or stalling.
- Changing the embedding model is safe and gradual, but similarity thresholds
  (`ved.knowledge.retrieval.figure-min-similarity`) are model-specific and must be re-tuned; changing
  the **dimensions** still needs a database migration.
- Output quality now depends on configuration: a weak model in a route degrades lessons or grading
  without any code change. An evaluation harness (roadmap) becomes more important.
- Misconfiguration fails fast at startup rather than on the first request.
- Reference: [guides/configuration.md](../guides/configuration.md).
