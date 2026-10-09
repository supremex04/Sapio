# Engineering conventions

We want code that is **easy to extend without being heavy**. The rules below are the working
interpretation of SOLID for this codebase. Prefer them over cleverness.

## SOLID, concretely

| Principle | What it means here | Example |
|---|---|---|
| **S**ingle responsibility | One reason to change per class. Controllers = HTTP; services = use-cases + transactions; adapters = one external system. | `AuthController` maps HTTP; `AuthService` runs register/login; `SessionService` owns refresh-token rules; `RefreshTokenCookies` owns the cookie format. |
| **O**pen/closed | Add behaviour by adding a class, not by editing a `switch` in many places. | New OCR engine = new `OcrEngine` subclass + one registry line. New storage = new `DocumentStorage` implementation. |
| **L**iskov substitution | Any implementation of a port must honour its contract (errors included). | Every `OcrEngine` returns Markdown or raises `EngineError`; every `OcrGateway` throws `OcrException` with a user-safe message. |
| **I**nterface segregation | Small, purpose-specific ports. | `DocumentStorage` has `store/load/delete` only; `AccessTokenService` only issues tokens. |
| **D**ependency inversion | Use-cases depend on ports; adapters depend on frameworks. | `DocumentProcessor` depends on `OcrGateway`, not on `RestClient` or the Python URL. |

### When to introduce an interface

*Not* for every service. Create an interface (port) when **at least one** holds:

- it wraps an external system (HTTP service, storage, model, email, payment…), or
- we realistically expect a second implementation (e.g. JWT → opaque tokens), or
- tests need to replace it (e.g. the OCR gateway in integration tests).

Plain concrete `@Service` classes are fine otherwise. Extract an interface the day a second
implementation appears.

## Backend (Java / Spring Boot)

- **Package by feature**: `com.ved.<feature>`. Classes are package-private by default; make
  public only what other features use.
- **Constructor injection only**, no field injection, final fields.
- **Entities protect their invariants**: state transitions are methods (`document.markCompleted(..)`),
  not public setters.
- **DTOs are records** (`*Request`, `*Response`); entities never leave the service layer through HTTP.
- **Transactions** live on service methods. Never call slow external systems inside a transaction
  (see `DocumentProcessor`).
- **Configuration** is typed: `@ConfigurationProperties` records with validation, under the `ved.*` prefix.
  Every setting that differs per environment can be overridden by an env var (`VED_*`).
- **Time** comes from the injected `Clock`, never `Instant.now()` in business logic.
- **Errors**: throw `ApplicationException(ErrorCode, message)` for expected failures; the
  `GlobalExceptionHandler` renders the standard error body. Add new codes to `ErrorCode`.
- **Authorisation**: every query on user-owned data is scoped by owner id
  (`findByIdAndOwnerId`). Someone else's resource returns **404**, not 403, so ids aren't confirmed.
  Role-based checks use `@PreAuthorize("hasRole('TEACHER')")`.
- **Database**: Flyway migration per schema change; UUID primary keys; `timestamptz` everywhere.

## Inference service (Python)

- Python 3.12, FastAPI, Pydantic v2; `ruff` for lint + format.
- Keep layers apart: `api/` (HTTP) → `service.py` (use-case) → `engines/`, `documents/` (work).
- Engines import heavy optional deps lazily, inside their module, so the service starts with
  only what the configured engine needs.
- Raise `DocumentError` for bad input (→ HTTP 422) and `EngineError` for engine/infra failures
  (→ HTTP 503). Messages must be safe to show to end users.
- JSON is **camelCase** on the wire (via `ApiModel`), snake_case in Python.

## Frontend (TypeScript / React)

- Feature folders under `src/features/<feature>`; shared code in `src/shared` (never imports features).
- All HTTP goes through `shared/api/http.ts` (`apiJson`, `apiRequest`); it attaches the token,
  refreshes once on 401, and throws `ApiError` with the backend `code`.
- Branch on `ApiError.code`, never on message text.
- Strict TypeScript (`strict`, `noUncheckedIndexedAccess`). No `any`.

## API design

See [specs/api-conventions.md](../specs/api-conventions.md).

## Testing strategy

| Layer | Tool | Scope |
|---|---|---|
| Backend unit | JUnit 5 + AssertJ (`*Test`, `mvn test`) | pure logic (file-type sniffing, adapters against stub servers) |
| Backend integration | Spring Boot Test + Testcontainers PostgreSQL (`*IT`, `mvn verify`) | full HTTP → DB flows; external ports mocked with `@MockitoBean` |
| Inference | pytest | loader, post-processing, engines (mock HTTP transport), API with a fake engine; one real CPU-OCR test |
| Frontend | Vitest | pure helpers; component tests to be added as UI grows |

Rule of thumb: every new endpoint gets an integration test covering the happy path, validation,
and "another user cannot access it".

## Adding a new feature (checklist)

1. Write/extend a spec in `docs/specs/` (requirements, endpoints, errors).
2. Backend: migration → entity/repository → service → controller → `*IT` test.
3. If it needs ML: add an endpoint + engine/use-case in `inference/`, document it in
   `docs/specs/inference-api.md`, call it from the backend through a port.
4. Frontend: `src/features/<feature>/` with `api.ts`, `types.ts`, pages; register routes in `app/App.tsx`.
5. Record any significant decision as an ADR.
