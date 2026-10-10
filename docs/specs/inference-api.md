# Spec: Inference service internal API

The Python service is **internal**: only the backend calls it. Default URL `http://localhost:8000`.
All JSON is camelCase. Errors are FastAPI `{"detail": "<user-safe message>"}`.

## Authentication

Header `X-Internal-Api-Key: <VED_INFERENCE_API_KEY>`, which must equal the backend's `VED_INFERENCE_API_KEY`.
Missing or wrong key → `401`. `/health` is open.

## Status codes (all endpoints)

| Status | Meaning | Backend reaction |
|---|---|---|
| 422 | Input rejected (corrupt file, too many pages, too little text) | fail the document / request with `detail` |
| 503 | Engine or LLM unavailable (after retries and the route's fallbacks), or quota exhausted on every model | `AI_UNAVAILABLE` / retryable failure |

## Model routing

Every LLM call names an internal **task** (`outline`, `lesson`, `questions`, `grade`, `feedback`,
`answer`, `notes`, `figures`, `summaries`; listed per endpoint below). The task is not part of the
wire contract: the service maps it to a model route (primary + fallbacks, any provider) configured in
`inference/settings.yaml`, see [configuration.md](../guides/configuration.md). Every generated response
carries `model` (`provider:model` that actually served it).

## `GET /health`

```jsonc
{ "status": "ok", "ocrEngine": "rapidocr-layout", "llmModel": "gemini:gemini-3.5-flash",
  "embeddingModel": "gemini:gemini-embedding-2", "embeddingDimensions": 768 }
```

`llmModel` = the model that served the most recent request (initially the default route's primary).
The backend reads `embeddingModel` / `embeddingDimensions` at startup and logs an error if the
dimensions don't match its `vector(768)` columns.

## `POST /v1/extract`

Multipart, one part `file` (with filename), up to `parsing.max_upload_bytes` (413 above). Per page:
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

## Indexing endpoints (`/v1/index/*`, JSON in/out)

| Endpoint | Task | Request | Response |
|---|---|---|---|
| `POST /figures` | `figures` (vision) | `{language, figures:[{key, imageBase64, mime, caption, context}]}` (1–60) | `{figures:[{key, kind, role, description, transcript}], model}` |
| `POST /summaries` | `summaries` | `{language, nodes:[{key, path, text, childSummaries}]}` (1–120) | `{summaries:[{key, summary, keyTerms}], model}` |
| `POST /embed` | (embeddings) | `{task: RETRIEVAL_DOCUMENT \| RETRIEVAL_QUERY, items:[{text?, imageBase64?, mime?}]}` (1–256) | `{vectors, model, dimensions}` |

- `figures`: one entry per input key or 503; `role` is `VISUAL` (shown to students), `TEXTUAL` (its text carries the
  meaning: terminal output, code, text-only slides; `transcript` holds the key text verbatim, at most
  `indexing.transcript_chars`) or `DECORATIVE` (the backend deletes it). When unsure the model is told to choose
  `VISUAL`; kind `DECORATIVE` always forces role `DECORATIVE`. `transcript` is empty unless the role is `TEXTUAL`.
- `summaries`: one entry per key or 503; at most 8 deduplicated `keyTerms`.
- `embed`: `model` (`provider:model`) is stored with every vector; images are used only when the
  embedding model is multimodal, otherwise items are embedded from their text. A vector of a
  different length than `dimensions` is a 503.

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

## Conventions for future endpoints

- Version prefix `/v1/...`; stateless; validate every LLM field before returning it.
- Long-running work stays synchronous here; the backend owns queueing, state and retries.
