# Configuration: models, providers and tunings

How to change AI vendors, models, OCR and every tuning knob **without code changes**. Two services
are configurable: the Python **inference** service (models, OCR, parsing) and the Spring Boot
**backend** (retrieval, context budgets, processing). The frontend has no runtime configuration.

Why it is built this way: [ADR-0010](../adr/0010-vendor-neutral-model-configuration.md).

## Where configuration lives

| Service | File | Overrides | Secrets |
|---|---|---|---|
| inference | [`inference/settings.yaml`](../../inference/settings.yaml) (providers, routes, tunings) | env `SAPIO_INFERENCE_*`, `inference/.env` | `inference/.env` (gitignored), referenced by name |
| backend | [`backend/src/main/resources/application.yml`](../../backend/src/main/resources/application.yml) | env vars (placeholders + Spring relaxed binding) | env vars (`SAPIO_JWT_SECRET`, …) |

Restart the service after a change; nothing is hot-reloaded.

### Inference precedence (highest first)

1. Constructor arguments (tests only)
2. Environment variables: prefix `SAPIO_INFERENCE_`, nested keys joined with `__`
3. `inference/.env` (same names)
4. `settings.yaml` in the working directory, or the file(s) named by `SAPIO_INFERENCE_CONFIG` (a base file followed
   by overlays, see below)
5. Defaults in [`config.py`](../../inference/src/sapio_inference/config.py)

Sources are **deep-merged**, so an override replaces one leaf and keeps the rest:

```bash
SAPIO_INFERENCE_OCR__ENGINE=unlimited-remote
SAPIO_INFERENCE_OCR__REMOTE__BASE_URL=http://gpu-host:10000/v1
SAPIO_INFERENCE_LLM__DEFAULT__PRIMARY__MODEL=gemini-3.8-flash
SAPIO_INFERENCE_PARSING__MAX_PAGES=300
SAPIO_INFERENCE_CONFIG=settings.prod.yaml          # pick another file per environment
SAPIO_INFERENCE_CONFIG=settings.yaml,settings.local-embedding.yaml   # base + overlay
```

- Lists and objects are given as JSON: `SAPIO_INFERENCE_LLM__DEFAULT__FALLBACKS='[{"provider":"gemini","model":"gemini-3.5-flash-lite"}]'`.
  A list replaces the whole list.
- An override can only change a route that exists: `SAPIO_INFERENCE_LLM__TASKS__NOTES__PRIMARY__MODEL`
  fails validation unless `llm.tasks.notes` (with its `provider`) is defined somewhere.
- For anything bigger than one or two values, use a separate settings file and `SAPIO_INFERENCE_CONFIG`.
- **Overlays:** `SAPIO_INFERENCE_CONFIG` accepts a comma-separated list, base file first, then overlays. The files
  are deep-merged in order (later wins; nested maps merge key by key, so an overlay can add one provider or change
  one section and keep the rest). A missing overlay is a startup error. Shipped overlay:
  [`settings.local-embedding.yaml`](../../inference/settings.local-embedding.yaml) (in-house embeddings, see
  [below](#in-house-embeddings-provider-type-onnx)).
- `SAPIO_INFERENCE_API_KEY` (the shared backend ↔ inference secret) is a top-level setting; it must equal the backend's.

### Backend precedence

Standard Spring Boot: environment variables and system properties override `application.yml`.

- Explicit placeholders (`${SAPIO_DB_URL:…}`) exist for deployment values: `SAPIO_DB_URL`, `SAPIO_DB_USERNAME`,
  `SAPIO_DB_PASSWORD`, `SAPIO_PORT`, `SAPIO_JWT_SECRET`, `SAPIO_COOKIE_SECURE`, `SAPIO_CORS_ORIGINS`,
  `SAPIO_STORAGE_DIR`, `SAPIO_FIGURE_URL_SECRET`, `SAPIO_INFERENCE_URL`, `SAPIO_INFERENCE_API_KEY`.
- Every other key can be overridden through **relaxed binding**: uppercase, `.` → `_`, dashes removed:
  `sapio.knowledge.retrieval.rrf-k` → `SAPIO_KNOWLEDGE_RETRIEVAL_RRFK` (Spring also accepts the legacy
  form with dashes as underscores, `SAPIO_KNOWLEDGE_RETRIEVAL_RRF_K`). Lists are comma-separated:
  `SAPIO_DOCUMENTS_PROCESSING_AUTORETRYDELAYS=2m,10m`.

## Secrets rule

Keys never go in `settings.yaml` or `application.yml`. A provider names the **environment variable**
that holds its key:

```yaml
providers:
  openai:
    type: openai
    api_key_env: OPENAI_API_KEY      # the NAME of the variable, not the key
```

and the value goes in `inference/.env` (loaded into the process at startup) or the real environment:

```env
OPENAI_API_KEY=sk-...
```

`ocr.remote.api_key_env` works the same way for a protected OCR server. Production must also override
`SAPIO_JWT_SECRET`, `SAPIO_FIGURE_URL_SECRET` and `SAPIO_INFERENCE_API_KEY` on both services.

## Providers

A provider is a vendor or endpoint. Define as many as you like under `providers:` and reference them
by name from routes and embeddings. Only providers that a route or the embeddings reference are built.

| Key | Default | Effect |
|---|---|---|
| `type` | required | `gemini` (Google Generative Language API) or `openai` (any OpenAI-compatible API) |
| `base_url` | Gemini public API / `https://api.openai.com/v1` | endpoint; set it for gateways and local servers |
| `api_key_env` | none | name of the env var holding the key; `gemini` requires it, `openai` sends `Authorization: Bearer` only if set |
| `timeout_seconds` | `180` | HTTP read timeout per call (raise for slow local models) |
| `max_retries` | `4` | retries per model on 429 / 5xx / network errors / invalid structured output |
| `max_backoff_seconds` | `45` | cap per wait; a server hint above **2×** this means "quota exhausted" and moves to the next model at once |
| `headers` | `{}` | extra HTTP headers (gateway attribution, org/project ids) |
| `json_mode` | `schema` | `openai` only: `schema` = strict `response_format: json_schema`; `object` = `json_object` + the schema in the prompt, for servers without JSON-schema support |

OpenAI-compatible endpoints:

| Vendor | `base_url` | Notes |
|---|---|---|
| OpenAI | (default) | `json_mode: schema` |
| OpenRouter | `https://openrouter.ai/api/v1` | models as `vendor/model`; optional `headers: {HTTP-Referer: …, X-Title: Sapio}` |
| Groq | `https://api.groq.com/openai/v1` | check the model supports JSON schema, else `json_mode: object` |
| vLLM | `http://<host>:8000/v1` | no key unless the server sets one |
| LM Studio | `http://localhost:1234/v1` | |
| Ollama | `http://localhost:11434/v1` | `json_mode: object`; no key |

### Model options

A model reference is `{provider, model, options}`. `options` are provider-specific:

| Provider | Option | Effect |
|---|---|---|
| `gemini` | `thinking_level: minimal \| low \| medium \| high` | sent as `thinkingConfig.thinkingLevel`; lower = faster/cheaper |
| `openai` | any chat-completions field: `reasoning_effort`, `max_tokens`, `top_p`, … | merged into the request body; when `reasoning_effort` is set, `temperature` is **not** sent (reasoning models reject it) |

## Routes: one model chain per task

Every inference request names a **task**. It uses `llm.tasks.<task>` if defined, else `llm.default`.
A route is a `primary` model plus ordered `fallbacks`, which may be on **different providers**.

| Task | Used by | Endpoint |
|---|---|---|
| `outline` | document analysis: learning path (topics) from the tree | `/v1/study/material` |
| `lesson` | Learn: lesson sections, warm-up and checks | `/v1/study/lesson` |
| `questions` | Test: assessment questions | `/v1/study/questions` |
| `grade` | AI grading of short answers (explain-back, test, review) | `/v1/study/grade` |
| `feedback` | Test report narrative (summary, strengths, weak areas, next steps) | `/v1/study/assessment-feedback` |
| `answer` | lesson AI tutor (`style: tutor`) and ask-your-document (`style: answer`) | `/v1/study/answer` |
| `notes` | study notes per section and chapter overviews | `/v1/study/notes` |
| `figures` | figure descriptions + role (visual / textual / decorative) (**vision**: every model in this route must accept images) | `/v1/index/figures` |
| `summaries` | node summaries and key terms while indexing (high volume) | `/v1/index/summaries` |

### Fallbacks and quota exhaustion

For each model in the chain, the provider retries transient failures with backoff (server hint from
`Retry-After` / Google `RetryInfo`, else 2, 4, 8 … s, capped at `max_backoff_seconds`). The router moves
to the **next model** when the current one is unavailable:

| Situation | Behaviour |
|---|---|
| 429 / 5xx / network / invalid or empty output | retry up to `max_retries`, then next model |
| Server hint > 2 × `max_backoff_seconds` (e.g. "retry in 17 h": daily quota) | next model **immediately** |
| OpenAI-style `429` with code `insufficient_quota` | next model immediately |
| 404 (unknown model) | next model immediately |
| Other 4xx (bad request, bad key) | fail at once (no other model would succeed): `500 MISCONFIGURED`, logged at ERROR |
| Whole chain exhausted | `503 MODEL_UNAVAILABLE` "busy or out of quota" (with `Retry-After` when every model reported its quota reset) → backend reports `AI_UNAVAILABLE`; indexing/analysis retry automatically, not before the reset |

`GET /health` reports the model that served the most recent request (`llmModel`, `provider:model`).

## Embeddings

```yaml
embeddings:
  provider: gemini
  model: gemini-embedding-2
  dimensions: 768
  multimodal: true
  batch_size: 64
```

| Key | Default | Effect |
|---|---|---|
| `provider` | `gemini` | a name from `providers` |
| `model` | `gemini-embedding-2` | embedding model |
| `dimensions` | `768` | **must be 768**: the database columns are `vector(768)`. Sent to the API (`outputDimensionality` / `dimensions`); a response of another length fails the request |
| `multimodal` | `true` | embed figures from the image (+ caption). Effective only on providers that support image embeddings (Gemini); otherwise figures are embedded from caption + description text |
| `batch_size` | `64` | items per embedding API call |

Embeddings have no fallback chain: one model must embed everything, or vectors would not be comparable.

**Switching the embedding model is safe:**

1. Every vector stores the model that made it (`embedding_model`, `provider:model`; renaming the provider counts as a switch).
2. Search embeds the query with the current model and compares it **only** with vectors of that model;
   keyword search still covers the rest.
3. The first search on a document with stale vectors triggers a **background re-embed** of that document
   (on the document worker pool); if the AI is busy it resumes on the next search.
4. At startup the backend reads `/health` (`contractVersion`, `embeddingModel`, `embeddingDimensions`) and logs
   the model, or an **error** if the dimensions are not 768 (or the contract major version differs).
5. Re-tune `sapio.knowledge.retrieval.figure-min-similarity`: similarity scales differ per model.

### In-house embeddings (provider type `onnx`)

Embedding models are small (hundreds of millions of parameters, versus billions for a chat model) and only read
text, so they run fine in-process on a CPU. Provider type `onnx` runs Hugging Face ONNX exports with ONNX Runtime
(no PyTorch; `pip install -e '.[embed]'`). The model downloads once to `~/.cache/huggingface`.

```yaml
providers:
  local: {type: onnx}
embeddings:
  provider: local
  model: onnx-community/Qwen3-Embedding-0.6B-ONNX   # Hugging Face repo id
  dimensions: 768                                   # Matryoshka model: first 768 of 1024 dims, re-normalised
  multimodal: false                                 # figures embedded from caption + description
  options:
    file: onnx/model_int8.onnx                      # 614 MB; model_fp16 (1.2 GB) is slightly more accurate
    pooling: last                                   # last | mean | cls (see the model card)
    batch_size: 8                                   # texts per forward pass; memory grows with batch × length
    query_prefix: "Instruct: Given a student's question about their study material, retrieve passages that answer it\nQuery: "
```

`options` per model family: `file`, `pooling`, `query_prefix` / `document_prefix` (instruction-tuned models need
them), `max_tokens` (512), `batch_size` (8), `threads` (0 = all cores). Example for a smaller English model:
`nomic-ai/nomic-embed-text-v1.5` with `pooling: mean`, `query_prefix: "search_query: "`,
`document_prefix: "search_document: "`.

The configuration above ships as an overlay, so the default stays Gemini and in-house embeddings are one switch away:

```bash
./dev.sh --local-embedding                      # or: ./dev.sh -d --local-embedding
SAPIO_INFERENCE_CONFIG=settings.yaml,settings.local-embedding.yaml   # manual runs (from inference/)
```

Switching either way re-embeds each document in the background the first time it is searched with the other
model (see "Switching the embedding model is safe" above); until then search runs on keywords for that document.

### Choosing an embedding model: measure on your own documents

`inference/scripts/eval_embeddings.py` compares candidates (defined at the top of the script) on real material:
it asks the LLM for one student question per passage and figure (cached, so every model faces the same
questions), then reports how often the right passage ranks first / in the top 3 / top 5 (hit@k, MRR), for meaning
search alone and fused with keyword search as in production. Copies of the same image count as one answer.
Question styles: `natural` (the student's own words, using the material's terms where a student would) and
`paraphrase` (deliberately reworded, hard for keyword search).

Export documents from Postgres into the script's JSON format (passages are prefixed with their breadcrumb, like
the indexer does) and run from `inference/`:

```bash
.venv/bin/python scripts/eval_embeddings.py export.json \
    --questions natural=natural.json --questions paraphrase=paraphrase.json --tune
```

| Option | Effect |
|---|---|
| `--questions STYLE=FILE` | question set, repeatable; generated with the LLM if `FILE` is missing (default `paraphrase=eval_questions.json`) |
| `--models qwen,gemini` | substrings of candidate names to run (default: all) |
| `--fusion KW,VW,K` | keyword weight, vector weight and k for the hybrid rows (default `1,1,60`) |
| `--tune` | also grid-search the fusion (keyword 0.5-1, vector 1-3, k 10/30/60), ranked by mean passage MRR over the question sets |
| `--cache DIR` | where vectors are cached (default `.eval_cache`), so re-runs and tuning make no embedding calls |

Results on 3 documents (125 passages, 11 figures, 134 questions; 2026-10-10; hybrid rows with the fusion of
then, 1 : 1, k=60):

| Model | Search | Passage #1 | Top 3 | Top 5 | MRR | Embedding time |
|---|---|---|---|---|---|---|
| gemini-embedding-2 (API) | meaning only | 88% | 100% | 100% | 0.936 | 128 s (rate limits) |
| Qwen3-Embedding-0.6B int8 (CPU) | meaning only | 76% | 95% | 99% | 0.860 | 77 s |
| gemini-embedding-2 (API) | keyword + meaning | 76% | 96% | 97% | 0.861 | |
| Qwen3-Embedding-0.6B int8 (CPU) | keyword + meaning | 74% | 94% | 97% | 0.846 | |

The questions deliberately avoid the source's wording, which makes keyword search look worse than it is with
real students; treat the numbers as a comparison between models, not as absolute quality.

**Fusion tuning** (`--tune`, 268 questions over the same 3 documents, natural + paraphrase; Qwen vectors, as
Gemini's daily quota was exhausted during tuning, so to be re-checked on Gemini). Hybrid ranking, weights
keyword : vector:

| Fusion | Natural #1 | Natural top 3 | Paraphrase #1 | Paraphrase top 3 | Mean passage MRR | Figures #1 (natural / paraphrase) |
|---|---|---|---|---|---|---|
| 1 : 1, k=60 (previous) | 80% | 99% | 76% | 94% | 0.871 | 82% / 73% |
| **1 : 2, k=10 (default)** | **85%** | 99% | **79%** | 93% | **0.895** | **91% / 82%** |

Trade-off: a passage found only by keyword search, even at #1, now ranks below meaning search's top results.
Safeguard: each ranker's #1 always survives the cut to `limit` results (it replaces the last slot), so exact-term
matches (commands, error codes) are never dropped. Hence `sapio.knowledge.retrieval.keyword-weight: 1.0`, `vector-weight: 2.0`, `rrf-k: 10`.

## OCR engine

Only scanned pages and images are OCR'd; PDF pages with a text layer are read natively.

```yaml
ocr:
  engine: rapid                # rapid | unlimited-remote | unlimited-local
  remote:
    base_url: http://localhost:10000/v1
    model: Unlimited-OCR
    flavor: sglang             # sglang | vllm
    pages_per_request: 8
    # api_key_env: OCR_SERVER_KEY
    # extra_body: {key: value}
  local:
    model_id: baidu/Unlimited-OCR
    device: cuda
```

| Key | Default | Effect |
|---|---|---|
| `ocr.engine` | `rapid` | `rapid` = RapidOCR inside the layout model (CPU); `unlimited-remote` = Unlimited-OCR on SGLang/vLLM; `unlimited-local` = in-process transformers (GPU ~8 GB+, `pip install -e '.[local]'`) |
| `ocr.remote.base_url` | `http://localhost:10000/v1` | OpenAI-compatible OCR server |
| `ocr.remote.model` | `Unlimited-OCR` | served model name |
| `ocr.remote.api_key_env` | none | env var holding the server's key |
| `ocr.remote.flavor` | `sglang` | `vllm` drops SGLang-only request fields |
| `ocr.remote.timeout_seconds` | `1200` | per request |
| `ocr.remote.pages_per_request` | `8` | pages per multi-page call (32k-token context) |
| `ocr.remote.extra_body` | `{}` | extra server-specific request fields |
| `ocr.local.model_id` | `baidu/Unlimited-OCR` | Hugging Face model id |
| `ocr.local.device` | `cuda` | torch device |
| `ocr.local.pages_per_request` | `8` | pages per call |

Setup details: [ocr-engines.md](ocr-engines.md).

## Recipes

Put these in `settings.yaml` (or a copy selected with `SAPIO_INFERENCE_CONFIG`). A section you write
replaces that section's defaults, so give complete `providers`, `llm` and `embeddings` blocks.
Model names are examples; use what your account offers.

### Switch everything to OpenAI

```yaml
providers:
  openai:
    type: openai
    api_key_env: OPENAI_API_KEY
llm:
  default:
    primary: {provider: openai, model: gpt-4.1-mini}
    fallbacks:
      - {provider: openai, model: gpt-4.1-nano}
  tasks: {}                       # drop the Gemini-specific summaries route
embeddings:
  provider: openai
  model: text-embedding-3-small
  dimensions: 768                 # text-embedding-3-* shortens to 768 on request
  multimodal: false
```

`inference/.env`: `OPENAI_API_KEY=…`. Existing documents are re-embedded on their next search.

### Run fully local with Ollama

```bash
ollama serve
ollama pull qwen3:8b && ollama pull qwen2.5vl:7b && ollama pull nomic-embed-text
```

```yaml
providers:
  ollama:
    type: openai
    base_url: http://localhost:11434/v1
    json_mode: object             # schema goes in the prompt
    timeout_seconds: 600          # local models are slow on long lessons
    max_retries: 2
llm:
  default:
    primary: {provider: ollama, model: "qwen3:8b"}
  tasks:
    figures:                      # needs a vision model
      primary: {provider: ollama, model: "qwen2.5vl:7b"}
embeddings:
  provider: ollama
  model: nomic-embed-text         # 768-d natively
  dimensions: 768
  multimodal: false
ocr:
  engine: rapid
```

Quality of lessons and grading depends heavily on the local model; validate on your own documents.

### Cheap model for summaries, strong model for notes

```yaml
llm:
  default:
    primary: {provider: gemini, model: gemini-3.5-flash, options: {thinking_level: low}}
    fallbacks:
      - {provider: gemini, model: gemini-3.5-flash-lite, options: {thinking_level: low}}
  tasks:
    summaries:
      primary: {provider: gemini, model: gemini-3.5-flash-lite, options: {thinking_level: minimal}}
      fallbacks:
        - {provider: gemini, model: gemini-3.5-flash, options: {thinking_level: low}}
    notes:
      primary: {provider: gemini, model: gemini-3.8-flash, options: {thinking_level: medium}}
      fallbacks:
        - {provider: gemini, model: gemini-3.5-flash, options: {thinking_level: low}}
```

### OpenRouter as a cross-vendor fallback

```yaml
providers:
  gemini:
    type: gemini
    api_key_env: SAPIO_INFERENCE_GEMINI_API_KEY
  openrouter:
    type: openai
    base_url: https://openrouter.ai/api/v1
    api_key_env: OPENROUTER_API_KEY
    headers: {X-Title: Sapio}
llm:
  default:
    primary: {provider: gemini, model: gemini-3.5-flash, options: {thinking_level: low}}
    fallbacks:
      - {provider: gemini, model: gemini-3.5-flash-lite, options: {thinking_level: low}}
      - {provider: openrouter, model: openai/gpt-4.1-mini}
```

When the Gemini daily quota runs out, requests go straight to OpenRouter instead of waiting.
Add the same fallback to every route in `llm.tasks`; a task route does not inherit `llm.default`'s fallbacks.

## Speech-to-text (audio and video uploads)

`speech.*` in `inference/settings.yaml` (install the `stt` extra; `./dev.sh` does). faster-whisper runs Whisper on
CTranslate2: any CPU (Linux, macOS Intel / Apple Silicon, Windows), an NVIDIA GPU when present. Why this model:
[ADR-0013](../adr/0013-lectures-from-audio-and-video.md).

| Key | Default | Notes |
|---|---|---|
| `model` | `small` | `base` is ~2.5x faster but makes ~4x more errors; `medium` / `large-v3` / `distil-large-v3` are more accurate on a GPU |
| `device` | `auto` | `cuda` when available, else `cpu`; a GPU that fails to load falls back to CPU |
| `compute_type` | `auto` | `int8_float16` on GPU, `int8` on CPU |
| `beam_size` | `1` | `5` is a little more accurate and ~1.5x slower |
| `language` | `null` | detected from the first window; force `en`, `ne`, `hi`… for mixed or noisy audio |
| `window_seconds` | `600` | decode / transcribe window (memory stays flat for long recordings); cuts at the quietest moment |
| `max_duration_seconds` | `14400` | longer recordings are rejected |
| `max_concurrency` | `1` | transcriptions at once per process (~1.5 GB each with `small`) |

`lecture_notes.window_seconds` (900) sets how much transcript goes into one notes request; the LLM route is task
`lecture_notes` (the default route unless overridden under `llm.tasks`). Backend side: `sapio.documents.max-media-bytes`
(1 GiB), `sapio.documents.media-url-secret` / `media-url-ttl` (signed playback URLs), `sapio.inference.media-read-timeout`
(3 h, for transcribing long recordings).

## Inference tuning knobs

### `parsing.*` (structural parsing)

| Key | Default | Effect |
|---|---|---|
| `max_upload_bytes` | `26214400` (25 MiB) | larger uploads → 413; keep in line with the backend limit |
| `max_pages` | `200` | longer documents are rejected (422) |
| `dpi` | `200` (72–400) | page rendering for OCR and figure crops |
| `max_concurrency` | `1` | parallel parses (keep 1 on a single GPU) |
| `native_min_chars` | `50` | letters/digits a PDF page needs to be read natively instead of OCR'd |
| `chunk_target_chars` | `900` | leaf chunk size (~150–250 tokens) |
| `chunk_max_chars` | `1400` | hard cap per chunk |
| `max_depth` | `4` | deepest tree level; deeper headings are clamped to it |
| `max_heading_chars` | `120` | longer "headings" are treated as text |
| `pages_per_fallback_node` | `5` | documents without headings get one node per N pages |

### `parsing.figures.*` (decorative-image filter)

| Key | Default | Effect |
|---|---|---|
| `max_side_px` | `1600` | figures are downscaled to this |
| `min_side_px` | `100` | smaller images are dropped |
| `min_page_area_fraction` | `0.012` | minimum share of the page area |
| `max_aspect` | `6.0` | thinner strips (rules, banners) are dropped |
| `repeat_pages` | `3` | the same small image on ≥ N pages is a logo/ornament |
| `logo_max_area_fraction` | `0.08` | repeated images at least this share of the page are reused content, kept once per section |
| `background_area_fraction` | `0.85` | images covering more of the page behind text are backgrounds |
| `max_figures` | `400` | per document |

### `indexing.*` and `study.*`

| Key | Default | Effect |
|---|---|---|
| `indexing.figures_per_call` | `6` | images per vision request (`figures` task) |
| `indexing.nodes_per_call` | `12` | sections per summary request (`summaries` task) |
| `indexing.node_text_chars` | `6000` | text per section sent for summarising (truncated here as well as by the backend) |
| `indexing.transcript_chars` | `2000` | max text kept from a text-only image (terminal output, code) in place of the image |
| `study.max_topics` | `12` | upper bound on learning-path topics |
| `study.outline_summary_chars` | `400` | summary characters per node in the outline prompt |

## Backend tuning knobs

All are `@ConfigurationProperties` records with `@Validated` constraints in `application.yml`.

### `sapio.knowledge.*` ([`KnowledgeProperties`](../../backend/src/main/java/com/sapio/knowledge/KnowledgeProperties.java))

| Key | Default | Effect |
|---|---|---|
| `figure-url-secret` | dev value (`SAPIO_FIGURE_URL_SECRET`) | HMAC key for figure image URLs; **override in production** |
| `figure-url-ttl` | `6h` | validity of a signed figure URL |
| `retrieval.candidates` | `30` | results per ranker (keyword, vector) before fusion |
| `retrieval.rrf-k` | `10` | Reciprocal Rank Fusion constant; higher flattens the weight of top ranks |
| `retrieval.keyword-weight` | `1.0` | how much the keyword (full-text) ranking counts in the fusion: score = Σ weight / (k + rank) |
| `retrieval.vector-weight` | `2.0` | how much the meaning (embedding) ranking counts. Weights and `rrf-k` were tuned with `eval_embeddings.py --tune` ([results](#choosing-an-embedding-model-measure-on-your-own-documents)) |
| `reading.unit-min-chars` | `300` | smallest study unit of the Preview (sections that get Expand / Revision): less text than this gets no rewrite of its own |
| `reading.unit-max-chars` | `6000` | largest study unit (~2-4 textbook pages, a 5-10 minute read); bigger sections split into their subsections. Keep below `sapio.study.context.notes.chars` |
| `retrieval.figure-min-similarity` | `0.48` | cosine similarity a figure needs to count as a semantic match (caption keyword matches always count). Calibrated on `gemini-embedding-2`: on-topic ~0.52–0.56, loosely related ~0.42–0.46, unrelated ~0.36–0.38. **Re-tune when switching embedding models** |
| `indexing.figure-batch` | `12` | figures per `/v1/index/figures` call (max 60) |
| `indexing.summary-batch` | `36` | nodes per `/v1/index/summaries` call (max 120) |
| `indexing.chunk-embed-batch` | `96` | chunks per `/v1/index/embed` call (max 256) |
| `indexing.figure-embed-batch` | `16` | figures per embed call (images make requests large) |
| `indexing.summary-text-chars` | `6000` | source text per section sent for summarising |

Each indexing batch commits on its own, so a failure resumes from the last batch.

### `sapio.study.context.*` ([`StudyProperties`](../../backend/src/main/java/com/sapio/study/StudyProperties.java))

Characters of retrieved source text (`chars`) and maximum figures (`figures`) sent to the model per use.
Larger = better grounding but slower, costlier calls.

| Key | `chars` | `figures` | Used by |
|---|---|---|---|
| `lesson` | `24000` | `6` | lesson generation |
| `questions` | `12000` | `0` | test questions |
| `grade` | `6000` | `0` | short-answer grading |
| `tutor` | `10000` | `2` | lesson tutor |
| `ask` | `14000` | `5` | ask-your-document |
| `notes` | `18000` | `5` | study notes |

Env example: `SAPIO_STUDY_CONTEXT_LESSON_CHARS=30000`.

### `sapio.documents.*` ([`DocumentProperties`](../../backend/src/main/java/com/sapio/document/DocumentProperties.java))

| Key | Default | Effect |
|---|---|---|
| `storage-dir` | `./var/storage` (`SAPIO_STORAGE_DIR`) | uploaded files and figure images |
| `max-size-bytes` | `26214400` | upload limit; keep `spring.servlet.multipart.max-file-size` (25MB) and `max-request-size` (26MB) in sync |
| `processing.core-pool-size` | `2` | document worker threads (also bounds concurrent inference calls from processing) |
| `processing.max-pool-size` | `4` | worker threads under load |
| `processing.queue-capacity` | `200` | queued documents per instance; beyond it a document stays `PENDING` and is picked up once its lease expires |
| `processing.auto-retry-delays` | `1m, 4m, 15m` | waits before each automatic retry of a transiently failed document (AI busy / out of quota / misconfigured); one entry per attempt; `[]` disables. A retry never starts before the AI's own retry hint (quota reset, capped at 24 h) |
| `processing.retry-poll` | `30s` | how often due retries and abandoned documents are picked up |
| `processing.lease` | `2m` | an in-flight document whose heartbeat is older than this is considered abandoned (crash, scale-down) and requeued by any instance; a crash resumes within one lease |
| `processing.heartbeat-interval` | `30s` (code default, not in `application.yml` or `DocumentProperties`) | how often an instance refreshes the heartbeat of the documents it is working on; keep well below `lease` |

### `sapio.inference.*` and `sapio.security.*`

| Key | Default | Effect |
|---|---|---|
| `sapio.inference.base-url` | `http://localhost:8000` (`SAPIO_INFERENCE_URL`) | inference service |
| `sapio.inference.api-key` | `dev-internal-key` (`SAPIO_INFERENCE_API_KEY`) | must equal the inference service's `SAPIO_INFERENCE_API_KEY` |
| `sapio.inference.connect-timeout` | `5s` | |
| `sapio.inference.read-timeout` | `20m` | long parses and generations |
| `sapio.security.jwt.secret` | dev value (`SAPIO_JWT_SECRET`) | ≥ 32 bytes; **override in production** |
| `sapio.security.jwt.access-token-ttl` | `15m` | |
| `sapio.security.refresh-token.ttl` | `14d` | sliding session lifetime |
| `sapio.security.refresh-token.cookie-secure` | `false` (`SAPIO_COOKIE_SECURE`) | `true` behind HTTPS |
| `sapio.security.cors.allowed-origins` | `http://localhost:5173` (`SAPIO_CORS_ORIGINS`) | |

## Validation: mistakes fail fast

| Mistake | Result |
|---|---|
| Route or embeddings reference a provider not under `providers` | inference fails at startup: `Unknown provider(s) [...]; define them under 'providers'` |
| Unknown task name under `llm.tasks`, unknown `ocr.engine`, `type`, `json_mode` or `flavor`, out-of-range number (e.g. `dpi: 500`) | inference fails at startup (pydantic validation error naming the key) |
| `gemini` provider in use without its key in the environment | inference fails at startup: `set the API key in $<api_key_env>` |
| Embedding dimensions ≠ 768 | backend logs an error at startup; embedding calls fail (500 `MISCONFIGURED`) until fixed |
| Inference contract major ≠ the backend's (`InferenceCompatibilityCheck.CONTRACT_MAJOR`) | backend logs an error at startup: deploy matching versions of both services |
| Backend value violating its constraint (e.g. `rrf-k: 0`, JWT secret < 32 bytes, `figure-min-similarity` outside -1..1) | backend fails at startup with a binding error naming the key |
