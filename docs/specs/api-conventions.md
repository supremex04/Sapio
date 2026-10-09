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
  "fieldErrors": [{ "field": "email", "message": "must be a well-formed email address" }]
}
```

`fieldErrors` appears only for validation failures. Clients branch on **`code`**, never on `message`.

| Code | HTTP | Meaning |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Malformed or invalid input |
| `UNAUTHENTICATED` | 401 | Missing/invalid/expired access token |
| `INVALID_CREDENTIALS` | 401 | Wrong email or password (deliberately not saying which) |
| `INVALID_REFRESH_TOKEN` | 401 | Refresh cookie missing, expired, revoked or already rotated |
| `ACCESS_DENIED` | 403 | Authenticated but not allowed (role checks) |
| `NOT_FOUND` | 404 | Doesn't exist **or belongs to someone else** |
| `EMAIL_ALREADY_REGISTERED` | 409 | Sign-up with an existing email |
| `RESULT_NOT_READY` | 409 | Document result requested before conversion completed |
| `FILE_TOO_LARGE` | 413 | Upload above the size limit |
| `UNSUPPORTED_FILE_TYPE` | 415 | Extension not allowed or content doesn't match it |
| `INTERNAL_ERROR` | 500 | Bug; details only in server logs |

## Pagination

List endpoints take `?page=<0-based>&size=<1..100, default 20>` and return:

```json
{ "items": [ ... ], "page": 0, "size": 20, "totalItems": 42, "totalPages": 3 }
```

## Versioning

No URL versioning yet (single first-party client). Changes must be additive (new fields,
new endpoints). When a breaking change becomes unavoidable, introduce `/api/v2/...` for the
affected resources.
