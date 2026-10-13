# Spec: Inference service internal API

The Python service is **internal**: only the backend calls it. Default URL `http://localhost:8000`.
All JSON is camelCase. The machine-readable contract is `contracts/inference-openapi.json` (see
[Contract file](#contract-file)); this page explains it.

## Authentication

Header `X-Internal-Api-Key: <VED_INFERENCE_API_KEY>`, which must equal the backend's `VED_INFERENCE_API_KEY`.
Missing or wrong key → `401 UNAUTHORIZED`. `/health` is open.

## Request correlation

Every request may carry `X-Request-Id` (the backend sends its own, see
[api-conventions.md](api-conventions.md#request-ids)). An id matching `[A-Za-z0-9._:-]{1,128}` is adopted,
anything else is replaced by a new one. The id is echoed in the `X-Request-Id` response header and in error bodies,
and every `ved_inference.*` log line shows it as `[request-id]`, so one learner action can be followed through both
services' logs.

## Errors

Every non-2xx response has the same body, built in one place (`api/errors.py`); routes never translate exceptions
themselves:

```json
{ "code": "MODEL_UNAVAILABLE", "message": "The AI service is busy or out of quota right now. Please try again later.",
  "retryable": true, "requestId": "3f2a9c41d7e84b0b9a1c2d3e4f5a6b7c" }
```

Callers branch on **`code`** and **`retryable`**, never on `message`. `message` is user-safe only for
`INPUT_REJECTED` and `MODEL_UNAVAILABLE`; for every other code it is written for operators and must not be shown to
learners. When the service knows when a retry will succeed (a quota reset), the response also carries
`Retry-After: <seconds>`.

| Code | Status | Retryable | Raised for | Who fixes it | Backend reaction |
|---|---|---|---|---|---|
| `INPUT_REJECTED` | 422 | no | `DocumentError`: corrupt/empty/encrypted file, too many pages, no readable text | the learner (other file) | document `FAILED` permanently / `AI_INPUT_REJECTED` (422) with the message |
| `INPUT_TOO_LARGE` | 413 | no | upload above `parsing.max_upload_bytes` | the learner | treated like `INPUT_REJECTED` |
| `MODEL_UNAVAILABLE` | 503 | yes | `EngineError`: every model in the route busy or out of quota, OCR server down, unusable model output | nobody (wait) | automatic retry (pipeline) / `AI_UNAVAILABLE` (503) |
| `MISCONFIGURED` | 500 | no | `ConfigurationError`: missing API key or optional extra, embedding dimension mismatch, provider rejected the request (HTTP 4xx), `onnx` provider asked to generate text | operator | pipeline retries later; requests get `AI_UNAVAILABLE` with a generic message; logged at ERROR |
| `CONTRACT_VIOLATION` | 400 | no | request body does not match the contract (FastAPI validation) | developer: backend bug or version skew | as `MISCONFIGURED` |
| `UNAUTHORIZED` | 401 | no | missing/wrong `X-Internal-Api-Key` | operator (align the keys) | as `MISCONFIGURED` |
| `NOT_FOUND`, `METHOD_NOT_ALLOWED` | 404, 405 | no | unknown route or method | developer (version skew) | as `CONTRACT_VIOLATION` |
| `INTERNAL_ERROR` | 500 | no | any unexpected exception; the message is generic, details only in the service log | developer | as `MISCONFIGURED` |

`EngineError.retry_after` comes from the provider: a `ModelUnavailable` keeps the quota reset hint
(`llm/retry.py`), and the router reports the earliest reset only when **every** model in the route gave one
(otherwise some model may recover sooner, so no hint is sent). The backend's full mapping is in
[backend-inference-integration.md](../architecture/backend-inference-integration.md#error-propagation).

## Model routing

Every LLM call names an internal **task** (`outline`, `lesson`, `questions`, `grade`, `feedback`,
`answer`, `notes`, `figures`, `summaries`; listed per endpoint below). The task is not part of the
wire contract: the service maps it to a model route (primary + fallbacks, any provider) configured in
`inference/settings.yaml`, see [configuration.md](../guides/configuration.md). Every generated response
carries `model` (`provider:model` that actually served it).

## `GET /health`

```jsonc
{ "status": "ok", "contractVersion": "1.1", "ocrEngine": "rapidocr-layout", "llmModel": "gemini:gemini-3.5-flash",
  "embeddingModel": "gemini:gemini-embedding-2", "embeddingDimensions": 768, "speechModel": "faster-whisper:small" }
```

`llmModel` = the model that served the most recent request (initially the default route's primary).
`contractVersion` is `CONTRACT_VERSION` in `api/schemas.py`. The backend (`InferenceCompatibilityCheck`) reads
`/health` at startup and logs an error if the contract major version differs from the one it implements or if
`embeddingDimensions` doesn't match its `vector(768)` columns.

## `POST /v1/extract`

Multipart, one part `file` (with filename), up to `parsing.max_upload_bytes` (413 `INPUT_TOO_LARGE` above). Per page:
native text if the PDF page has ≥ `parsing.native_min_chars` letters/digits in its text layer, else OCR.
Also returns the document tree (details: [knowledge.md](knowledge.md)).

```jsonc
{
  "markdown": "joined text; pages separated by \n\n---\n\n",
  "engine": "pymupdf+rapidocr-layout", "language": "en",
  "pageCount": 3, "textPages": 2, "ocrPages": 1,
  "segments": [ { "pageFrom": 1, "pageTo": 1, "method": "text | ocr", "markdown": "…" } ],
  "tree": {
    "nodes":   [ { "key", "parentKey", "level", "path", "label", "title", "ordinal", "pageFrom", "pageTo" } ],
    "chunks":  [ { "key", "nodeKey", "ordinal", "text", "pageFrom", "pageTo" } ],
    "figures": [ { "key", "nodeKey", "chunkKey", "ordinal", "page", "bbox", "caption", "mime", "width", "height", "imageBase64" } ]
  }
}
```

`/v1/extract` also accepts Word documents (`.docx`): parsed from their own structure (heading styles, lists,
tables, embedded images), `engine: "docx"`, pages from Word's recorded page breaks.

## Recordings: `POST /v1/transcribe`, `POST /v1/extract/transcript`

Audio and video (only the first audio track is used). Two calls, so the backend can store the transcript and retry
the notes alone when the AI is busy. Design: [ADR-0013](../adr/0013-lectures-from-audio-and-video.md).

`POST /v1/transcribe`: multipart `file`, up to `speech.max_upload_bytes` (413) and `speech.max_duration_seconds`
(422 `INPUT_REJECTED`); no audio track or a corrupt file is 422. Slow (about 7x real time on CPU): the backend uses
its `media-read-timeout`.

```jsonc
{ "language": "en", "durationSeconds": 2891.4, "engine": "faster-whisper:small",
  "segments": [ { "start": 0.0, "end": 3.5, "text": "Today we are going to talk about database indexing." } ] }
```

`POST /v1/extract/transcript` (task `lecture_notes`): `{ "title": "<file name>", "transcript": <the above> }` →
the same response as `/v1/extract`. The tree holds the study notes (topic sections with time ranges); `markdown` and
`segments` (`method: "stt"`) hold the transcript as paragraphs starting with their time (`**12:30** ...`).
"Page" means minute of the recording (page 1 = 00:00-00:59). Less than `lecture_notes.min_words` of speech is 422
("No speech was found in this recording").

## Indexing endpoints (`/v1/index/*`, JSON in/out)

| Endpoint | Task | Request | Response |
|---|---|---|---|
| `POST /figures` | `figures` (vision) | `{language, figures:[{key, imageBase64, mime, caption, context}]}` (1–60) | `{figures:[{key, kind, role, description, transcript}], model}` |
| `POST /summaries` | `summaries` | `{language, nodes:[{key, path, text, childSummaries}]}` (1–120) | `{summaries:[{key, summary, keyTerms}], model}` |
| `POST /embed` | (embeddings) | `{task: RETRIEVAL_DOCUMENT \| RETRIEVAL_QUERY, items:[{text?, imageBase64?, mime?}]}` (1–256) | `{vectors, model, dimensions}` |

- `figures`: one entry per input key or 503 `MODEL_UNAVAILABLE`; `role` is `VISUAL` (shown to students), `TEXTUAL` (its text carries the
  meaning: terminal output, code, text-only slides; `transcript` holds the key text verbatim, at most
  `indexing.transcript_chars`) or `DECORATIVE` (the backend deletes it). When unsure the model is told to choose
  `VISUAL`; kind `DECORATIVE` always forces role `DECORATIVE`. `transcript` is empty unless the role is `TEXTUAL`.
- `summaries`: one entry per key or 503; at most 8 deduplicated `keyTerms`.
- `embed`: `model` (`provider:model`) is stored with every vector; images are used only when the
  embedding model is multimodal, otherwise items are embedded from their text. A vector of a
  different length than `dimensions` is a 500 `MISCONFIGURED`.

## Study endpoints (`/v1/study/*`, JSON in/out)

Study endpoints never receive the whole document: the backend retrieves the relevant **sections**
(`{id: "§2.3", path, pages, summary, passages[]}`) and **figures** (`{id, sectionId, caption, description}`)
from the knowledge store. Generated markdown cites sections as `[§id]` and embeds figures as
`![caption](figure:<id>)`.

| Endpoint | Task | Request | Response |
|---|---|---|---|
| `POST /material` | `outline` | `{filename, sample, nodes:[{key, label, title, level, pages, summary, keyTerms}]}` | `{title, summary, language, kind, topics:[{position, title, summary, keyConcepts, difficulty, nodeKeys}], model}` |
| `POST /lesson` | `lesson` | `{materialTitle, language, kind, topic:{title, summary, keyConcepts, difficulty}, previousTopics, nextTopics, sections, figures}` | `{sections:[{kind, heading, body}], keyTakeaways, checks:[{afterSection, type, prompt, options, correctOptionIndex, referenceAnswer, explanation}], figureIds, model}` |
| `POST /questions` | `questions` | `{materialTitle, language, kind, count (1–30), difficulty: EASY\|MIXED\|HARD, topics:[{key, title, summary, keyConcepts, difficulty, sections}], avoidPrompts}` | `{questions:[{topicKey, type, prompt, options, correctOptionIndex, referenceAnswer, explanation, sourceSectionIds}], model}` |
| `POST /grade` | `grade` | `{language, question:{prompt, referenceAnswer, explanation}, sections, answer}` | `{verdict, score, feedback, model}` |
| `POST /assessment-feedback` | `feedback` | `{materialTitle, language, score, topics:[{title, questionCount, score}], missed:[{topicTitle, prompt, learnerAnswer, correctAnswer}]}` | `{summary, strengths, weakAreas:[{topicTitle, advice}], nextSteps, model}` |
| `POST /answer` | `answer` | `{materialTitle, language, question, history:[{role: user\|tutor, text}] (≤ 20), sections, figures, style: tutor\|answer}` | `{answer, grounded, citedSectionIds, figureIds, model}` |
| `POST /notes` | `notes` | `{materialTitle, language, kind, title, path, scope: section\|overview, style: expand\|revision (default expand), sections, figures}` | `{markdown, figureIds, citedSectionIds, model}` |

`/answer` serves both the lesson tutor (`style: tutor`, short and conversational) and
ask-your-document (`style: answer`, long-form). `/notes` with `scope: overview` writes chapter notes
from the child sections' notes (the reduce step). `style` picks the prompt (`prompts.notes_prompt`): `expand` explains
in depth (small steps, why and how, jargon in plain words, examples and analogies, "Key terms" at the end);
`revision` writes terse key-point bullets (under a third of the source) ending with a "Quick self-test" of 3-5
questions without answers.

Guarantees the service enforces on LLM output:

- Topics reference only node keys from the request, in document order; topics without valid keys are
  dropped (≥ 1 topic or 503).
- Lesson checks: warm-up has `afterSection = -1`; no check follows the SUMMARY section; the
  explain-back (`SHORT_ANSWER`) check follows the last section; checks are sorted by `afterSection`.
- Choice questions: MULTIPLE_CHOICE has ≥ 3 distinct options, shuffled, with `correctOptionIndex`
  remapped; TRUE_FALSE has exactly 2 options, capitalised; invalid indices are dropped.
- `questions[].topicKey` is one of the request's keys and `sourceSectionIds` ⊆ that topic's sections.
- Figure embeds pointing at ids not in the request are removed; `figureIds` lists the ones kept.
  Citations must reference provided sections.
- Grades: `score` is clamped into the verdict band (CORRECT 0.8–1, PARTIAL 0.3–0.7, INCORRECT 0–0.2);
  blank answers are INCORRECT without calling the LLM.

## Contract file

`contracts/inference-openapi.json` (repo root) is the OpenAPI document generated from the FastAPI app by
`inference/scripts/export_openapi.py`. Both services are tested against it:

| Test | Side | Fails when |
|---|---|---|
| `inference/tests/test_contract.py` | provider | the file differs from what the code serves; an endpoint does not document `ErrorResponse` |
| `backend/.../common/inference/InferenceContractTest.java` | consumer | for each call the backend makes: a sent field is not in the contract (it would be silently dropped), a required field is not sent, a field the backend reads is not returned, an enum value is not accepted/known in that direction, types differ, the endpoint is missing, the error envelope differs from `InferenceClient.ErrorBody`, or the contract major ≠ `InferenceCompatibilityCheck.CONTRACT_MAJOR` |

After an intentional API change, from `inference/`:

```bash
.venv/bin/python scripts/export_openapi.py
```

Review the diff, then run the backend tests. On a breaking change (field removed or renamed, new required field,
enum value removed) bump the major of `CONTRACT_VERSION` and `InferenceCompatibilityCheck.CONTRACT_MAJOR` together;
additive changes bump the minor.

## Conventions for future endpoints

- Version prefix `/v1/...`; stateless; validate every LLM field before returning it.
- Raise an `errors.py` exception (`DocumentError`, `EngineError`, `ConfigurationError`); never return an error body
  or `HTTPException` from a route.
- Long-running work stays synchronous here; the backend owns queueing, state and retries.
- Regenerate the contract file in the same change.
