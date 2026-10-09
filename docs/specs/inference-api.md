# Spec: Inference service internal API

The Python service is **internal**: only the backend calls it. Default URL `http://localhost:8000`.

## Authentication

Header `X-Internal-Api-Key: <VED_INFERENCE_API_KEY>`. It must match the backend's
`VED_INFERENCE_API_KEY`. Missing or wrong key → `401`.

## `GET /health`

No auth. `200 {"status": "ok", "engine": "rapidocr"}`. `engine` is the active engine's name.

## `POST /v1/ocr`

Converts one document into Markdown. Synchronous: it can take minutes for long PDFs.

Request: `multipart/form-data`, a single part `file` with a filename (the extension selects the
PDF or image decoder).

| Status | Body | When |
|---|---|---|
| 200 | `{"markdown": "...", "engine": "unlimited-ocr", "pageCount": 3}` | success |
| 401 | `{"detail": "Invalid internal API key"}` | bad key |
| 413 | `{"detail": "File too large"}` | > `VED_INFERENCE_MAX_UPLOAD_BYTES` |
| 422 | `{"detail": "<user-safe reason>"}` | corrupt / password-protected / too many pages / unsupported |
| 503 | `{"detail": "<reason>"}` | engine or model server failure (retryable) |

The backend maps 4xx to a `FAILED` document whose `errorMessage` includes `detail`, and maps
5xx/network errors to "OCR service is unavailable, please retry later".

## Conventions for future endpoints

- Version prefix `/v1/...`; JSON camelCase; errors as FastAPI `{"detail": "..."}`.
- Stateless: everything needed comes with the request; nothing is persisted.
- Long-running work stays synchronous here; the backend owns queueing and status.
