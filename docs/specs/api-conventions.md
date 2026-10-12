# Public REST API conventions

Applies to every endpoint under `/api` served by the backend.

## Basics

- Base path `/api`; resources are plural nouns (`/api/documents`, `/api/users/me`).
- JSON bodies, **camelCase** fields. Timestamps are ISO-8601 UTC (`2026-10-09T16:11:02.295Z`). IDs are UUIDs.
- Authentication: `Authorization: Bearer <accessToken>` on everything except the public auth
  endpoints and `/actuator/health`.
- Creating something that is processed later returns **202 Accepted** with the resource.

## Error body

Every non-2xx response has this shape:

```json
{
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/auth/register",
  "timestamp": "2026-10-09T16:11:33.411Z",
  "fieldErrors": [{ "field": "email", "message": "must be a well-formed email address" }],
  "requestId": "9b1f0c2e4a6d4e8f8c3b5a7d9e1f2a3b"
}
```

`fieldErrors` appears only for validation failures. `requestId` equals the `X-Request-Id` response header (see
[Request ids](#request-ids)). Clients branch on **`code`**, never on `message`.

| Code | HTTP | Meaning |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Malformed or invalid input (including a body that isn't valid JSON) |
| `UNAUTHENTICATED` | 401 | Missing/invalid/expired access token |
| `INVALID_CREDENTIALS` | 401 | Wrong email or password (deliberately not saying which) |
| `INVALID_REFRESH_TOKEN` | 401 | Refresh cookie missing, expired, revoked or already rotated |
| `ACCESS_DENIED` | 403 | Authenticated but not allowed (role checks, deleting someone else's comment) |
| `NOT_FOUND` | 404 | Doesn't exist **or belongs to someone else** |
| `EMAIL_ALREADY_REGISTERED` | 409 | Sign-up with an existing email |
| `RESULT_NOT_READY` | 409 | Document result requested before conversion completed |
| `FILE_TOO_LARGE` | 413 | Upload above the size limit |
| `UNSUPPORTED_FILE_TYPE` | 415 | Extension not allowed or content doesn't match it |
| `RATE_LIMITED` | 429 | Too many actions in a short time (e.g. comments per minute) |
| `AI_INPUT_REJECTED` | 422 | The AI refused the input itself (e.g. unreadable content); retrying the same input will not help |
| `AI_UNAVAILABLE` | 503 | The AI is busy, out of quota or misconfigured; retry later. Carries `Retry-After: <seconds>` when the reset time is known |
| `INTERNAL_ERROR` | 500 | Bug; details only in server logs (the UI shows the first 8 characters of `requestId` as "ref") |

`AI_*` messages are user-safe: operator details (missing keys, contract mismatches) never reach the client and are
replaced by a generic message.

## Request ids

Every response carries `X-Request-Id`. A client may send its own (`[A-Za-z0-9._:-]{1,128}`, otherwise it is
replaced by a generated one). The id is on every backend log line (`[requestId]`), forwarded to the inference
service, inherited by the document processing job an upload starts, and echoed in error bodies: quote it in bug
reports.

## Pagination

List endpoints take `?page=<0-based>&size=<1..100, default 20>` and return:

```json
{ "items": [ ... ], "page": 0, "size": 20, "totalItems": 42, "totalPages": 3 }
```

## Versioning

No URL versioning yet (single first-party client). Changes must be additive (new fields,
new endpoints). When a breaking change becomes unavoidable, introduce `/api/v2/...` for the
affected resources.
