# Architecture overview

## System context

```mermaid
flowchart LR
    U[Student / Teacher<br/>browser] -->|HTTPS /api| FE[Frontend<br/>React + TypeScript]
    FE -->|REST + JWT| BE[Core backend<br/>Java 21 · Spring Boot 4]
    BE -->|JDBC| DB[(PostgreSQL)]
    BE -->|files| FS[(Document storage<br/>local disk today)]
    BE -->|internal REST<br/>X-Internal-Api-Key| INF[Inference service<br/>Python · FastAPI]
    INF -->|OpenAI-compatible API| GPU[GPU model server<br/>SGLang / vLLM<br/>Unlimited-OCR]
```

| Component | Folder | Responsibility | Talks to |
|---|---|---|---|
| Frontend | `frontend/` | UI only. No business rules beyond input hints. | Backend (`/api`) |
| Core backend | `backend/` | **The single source of truth**: identity, authorisation, domain data, job orchestration. | PostgreSQL, storage, inference |
| Inference service | `inference/` | Stateless AI/ML computations (OCR today; grading, recommendations, analytics later). No database, no user concept. | Model servers / local models |

Ground rules:

1. **Only the backend is public.** The inference service is internal and trusts the backend
   (shared API key). Browsers never call it.
2. **Only the backend owns data.** The inference service receives inputs and returns outputs; it
   never stores user data.
3. **Language per job:** Java for business logic, Python for ML, TypeScript for the UI
   ([ADR-0001](../adr/0001-polyglot-three-service-architecture.md)).

## Backend internals

Package-by-feature (`com.ved.<feature>`), with a thin layered structure inside each feature:

```
com.ved
├── common/        cross-cutting: error model, time, pagination
├── security/      JWT issuing/validation, CORS, @CurrentUserId
├── user/          User aggregate, /api/users
├── auth/          register/login/refresh/logout use-cases
│   └── session/   refresh-token sessions (open / rotate / revoke)
└── document/      upload + OCR conversion
    ├── storage/   DocumentStorage port + local filesystem adapter
    ├── ocr/       OcrGateway port + inference-service HTTP adapter
    └── processing/ async worker, triggers (after-commit, startup recovery)
```

Inside a feature: `Controller → Service → Repository / Port`. Controllers handle HTTP only;
services hold the use-cases and transactions; **ports** (interfaces) wrap anything external
(storage, OCR, token format) so it can be swapped without touching the use-cases. See
[conventions.md](conventions.md).

## Key flows

### Authentication

```mermaid
sequenceDiagram
    participant B as Browser
    participant API as Backend
    participant DB as PostgreSQL
    B->>API: POST /api/auth/login {email, password}
    API->>DB: find user, verify bcrypt hash
    API->>DB: insert user_sessions (sha256(refreshToken))
    API-->>B: 200 {accessToken (JWT, 15 min), user}<br/>Set-Cookie: ved_refresh (HttpOnly, 14 d, Path=/api/auth)
    B->>API: GET /api/... Authorization: Bearer <JWT>
    Note over B,API: access token expires → 401
    B->>API: POST /api/auth/refresh (cookie)
    API->>DB: rotate token hash in session row
    API-->>B: 200 {new accessToken} + new cookie
```

Details: [specs/auth.md](../specs/auth.md), [ADR-0002](../adr/0002-jwt-access-tokens-with-rotating-refresh-sessions.md).

### Document conversion (OCR)

```mermaid
sequenceDiagram
    participant B as Browser
    participant API as Backend
    participant W as Worker pool (in backend)
    participant INF as Inference service
    B->>API: POST /api/documents (multipart)
    API->>API: validate type (extension + magic bytes) & size, store file
    API-->>B: 202 {id, status: PENDING}
    API--)W: after commit: process(id)
    W->>W: status PROCESSING
    W->>INF: POST /v1/ocr (file)
    INF->>INF: PDF → page images → engine → Markdown
    INF-->>W: {markdown, engine, pageCount}
    W->>W: status COMPLETED (or FAILED + reason)
    loop every 2 s while not terminal
        B->>API: GET /api/documents/{id}
    end
    B->>API: GET /api/documents/{id}/result
```

Details: [specs/ocr.md](../specs/ocr.md), [ADR-0003](../adr/0003-pluggable-ocr-engines-in-a-python-service.md),
[ADR-0004](../adr/0004-in-process-async-document-processing.md).

## Data model (M1)

```mermaid
erDiagram
    users ||--o{ user_sessions : has
    users ||--o{ documents : owns
    users {
        uuid id PK
        varchar email UK "lower-cased"
        varchar password_hash "bcrypt (delegating encoder)"
        varchar full_name
        varchar role "STUDENT | TEACHER | ADMIN"
        bool enabled
    }
    user_sessions {
        uuid id PK
        uuid user_id FK
        varchar refresh_token_hash UK "sha256 hex"
        timestamptz expires_at
        timestamptz revoked_at
    }
    documents {
        uuid id PK
        uuid owner_id FK
        varchar original_filename
        varchar content_type
        varchar storage_key
        varchar status "PENDING | PROCESSING | COMPLETED | FAILED"
        text result_markdown
        varchar error_message
    }
```

Schema changes are **Flyway migrations** in `backend/src/main/resources/db/migration`
(`V<n>__<description>.sql`). Never edit a migration that has run in a shared environment; add a new one.
Hibernate only *validates* the schema (`ddl-auto: validate`).

## Frontend internals

```
src/
├── app/            routing + authenticated layout
├── shared/         feature-agnostic code
│   ├── api/        fetch wrapper (token, single-flight refresh), ApiError
│   ├── lib/        formatting, downloads
│   └── ui/         small presentational components
└── features/
    ├── auth/       AuthProvider (session state), pages, route guards
    └── documents/  API client, upload, list, detail
```

A feature owns its API calls, types and pages. `shared/` must not import from `features/`.
The access token lives in memory only; on reload the session is restored with the HttpOnly
refresh cookie.

## Inference service internals

```
ved_inference/
├── api/          FastAPI routes, schemas (camelCase JSON), API-key check
├── service.py    OcrService use-case (load → engine → result), concurrency limit
├── documents/    loader: PDF/image bytes → page images (PyMuPDF, Pillow)
└── engines/      OcrEngine ABC + implementations + registry
```

Engines are chosen by `VED_INFERENCE_ENGINE`; see [guides/ocr-engines.md](../guides/ocr-engines.md).
