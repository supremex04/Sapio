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
| 503 | Engine or LLM unavailable (after retries and model fallback) | `AI_UNAVAILABLE` / retryable failure |

## `GET /health`

`200 {"status": "ok", "ocrEngine": "rapidocr", "llmModel": "gemini-3.5-flash"}`

## `POST /v1/extract`

Multipart, one part `file` (with filename). Per page: native text if the PDF page has ≥
`VED_INFERENCE_NATIVE_TEXT_MIN_CHARS` letters/digits in its text layer, else OCR. Consecutive OCR pages
go to the engine together.

```jsonc
{
  "markdown": "joined text; pages separated by \n\n---\n\n",
  "engine": "pymupdf+rapidocr",
  "pageCount": 3, "textPages": 2, "ocrPages": 1,
  "segments": [ { "pageFrom": 1, "pageTo": 1, "method": "text | ocr", "markdown": "…" } ]
}
```

## Study endpoints (`/v1/study/*`, JSON in/out)

| Endpoint | Request | Response |
|---|---|---|
| `POST /material` | `{filename, pages:[{pageFrom, pageTo, text}]}` | `{title, summary, language, kind, chunks:[{position, heading, content, pageFrom, pageTo}], topics:[{position, title, summary, keyConcepts, difficulty, chunkPositions}], model}` |
| `POST /lesson` | `{materialTitle, language, kind, topic:{title, summary, keyConcepts, difficulty}, previousTopics, nextTopics, sources:[{position, heading, content}]}` | `{sections:[{kind, heading, body}], keyTakeaways, checks:[{afterSection, type, prompt, options, correctOptionIndex, referenceAnswer, explanation}], model}` |
| `POST /questions` | `{materialTitle, language, kind, count, difficulty: EASY\|MIXED\|HARD, topics:[{key, title, summary, keyConcepts, difficulty, sources}], avoidPrompts}` | `{questions:[{topicKey, type, prompt, options, correctOptionIndex, referenceAnswer, explanation, sourceChunkPositions}], model}` |
| `POST /grade` | `{language, question:{prompt, referenceAnswer, explanation}, sources, answer}` | `{verdict, score, feedback, model}` |
| `POST /assessment-feedback` | `{materialTitle, language, score, topics:[{title, questionCount, score}], missed:[{topicTitle, prompt, learnerAnswer, correctAnswer}]}` | `{summary, strengths, weakAreas:[{topicTitle, advice}], nextSteps, model}` |
| `POST /ask` | `{materialTitle, language, topicTitle, sources, question, history:[{role: user\|tutor, text}]}` | `{answer, grounded, model}` |

Guarantees the service enforces on LLM output:

- Topics reference only existing chunks; topics without valid chunks are dropped (≥ 1 topic or 503).
- Lesson checks: warm-up has `afterSection = -1`; no check follows the SUMMARY section; the
  explain-back (`SHORT_ANSWER`) check follows the last section; checks are sorted by `afterSection`.
- Choice questions: MULTIPLE_CHOICE has ≥ 3 distinct options, shuffled, with `correctOptionIndex`
  remapped; TRUE_FALSE has exactly 2 options, capitalised; invalid indices are dropped.
- `questions[].topicKey` is one of the request's keys and `sourceChunkPositions` ⊆ that topic's sources.
- Grades: `score` is clamped into the verdict band (CORRECT 0.8–1, PARTIAL 0.3–0.7, INCORRECT 0–0.2);
  blank answers are INCORRECT without calling the LLM.

## Conventions for future endpoints

- Version prefix `/v1/...`; stateless; validate every LLM field before returning it.
- Long-running work stays synchronous here; the backend owns queueing, state and retries.
