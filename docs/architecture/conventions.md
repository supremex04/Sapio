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

- **Package by feature**: `com.sapio.<feature>`. Classes are package-private by default; make
  public only what other features use.
- **Constructor injection only**, no field injection, final fields.
- **Entities protect their invariants**: state transitions are methods (`document.markCompleted(..)`),
  not public setters.
- **DTOs are records** (`*Request`, `*Response`); entities never leave the service layer through HTTP.
- **Transactions** live on service methods. Never call slow external systems inside a transaction
  (see `DocumentProcessor`).
- **Configuration** is typed: `@ConfigurationProperties` records with validation, under the `sapio.*` prefix.
  Every setting that differs per environment can be overridden by an env var (`SAPIO_*`).
- **Time** comes from the injected `Clock`, never `Instant.now()` in business logic.
- **Errors**: throw `ApplicationException(ErrorCode, message)` for expected failures; the
  `GlobalExceptionHandler` renders the standard error body. Add new codes to `ErrorCode`. AI ports throw an
  `AiException` subclass (input rejected / retryable / retry-after), never an HTTP or vendor exception; adapters
  classify by `InferenceException.kind()`, never by message text, and show only `userMessage(fallback)` to learners.
- **Wire records belong to the port**: what crosses a service boundary is a record declared on the port
  (`StudyAi.Source`, not `ContextAssembler.Section`), carrying only what the other side needs; internal ids and
  domain types stay inside.
- **Authorisation**: every query on user-owned data is scoped by owner id
  (`findByIdAndOwnerId`). Someone else's resource returns **404**, not 403, so ids aren't confirmed.
  Role-based checks use `@PreAuthorize("hasRole('TEACHER')")`.
- **Database**: Flyway migration per schema change; UUID primary keys; `timestamptz` everywhere.
- **Cross-feature side effects go through domain events** (`LearningActivityEvent`,
  `UserLoggedInEvent`), not direct calls into the consuming feature (ADR-0008).
- **Never call AI/OCR inside a DB transaction.** Load → call outside → persist in a short
  `TransactionTemplate` block (see `LessonService`, `AssessmentService`, `DocumentProcessor`).
- **Never send answer keys before the learner answers.** Use `QuestionView` before, `AnswerResult` after.

## Inference service (Python)

- Python 3.12, FastAPI, Pydantic v2; `ruff` for lint + format.
- Keep layers apart: `api/` (HTTP) → `service.py` (use-case) → `engines/`, `documents/` (work).
- Engines import heavy optional deps lazily, inside their module, so the service starts with
  only what the configured engine needs.
- Raise from the taxonomy in `errors.py`: `DocumentError` for bad input (→ 422 `INPUT_REJECTED`),
  `EngineError` for busy/unavailable engines or models (→ 503 `MODEL_UNAVAILABLE`, retryable, with `retry_after`
  when known), `ConfigurationError` for what an operator must fix (→ 500 `MISCONFIGURED`). Only `DocumentError` and
  `EngineError` messages are shown to end users. Routes never catch exceptions; `api/errors.py` renders them.
- Any API change: regenerate `contracts/inference-openapi.json` (`scripts/export_openapi.py`); the contract tests
  on both sides must pass ([inference-api.md](../specs/inference-api.md#contract-file)).
- JSON is **camelCase** on the wire (via `ApiModel`), snake_case in Python.
- **LLM output is untrusted input**: request it with a JSON schema (`LlmClient.generate`) and validate
  every field in the service (references, indices, structure) before returning it.
- Prompts live in `study/prompts.py` and encode the pedagogy in
  [learning-science.md](learning-science.md); change both together.

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
| Contract | `test_contract.py` (pytest) + `InferenceContractTest` (JUnit) | committed OpenAPI file = code; backend DTOs agree with it |
| Inference | pytest | loader, post-processing, engines (mock HTTP transport), API with a fake engine; one real CPU-OCR test |
| Frontend | Vitest | pure helpers; component tests to be added as UI grows |

Rule of thumb: every new endpoint gets an integration test covering the happy path, validation,
and "another user cannot access it".

## Design principle check

Before shipping a learner-facing feature, check it against
[learning-science.md](learning-science.md): which principle it uses, and whether it is a nudge or a
manipulation (no shame, fake urgency or punishing loss mechanics).

## Adding a new feature (checklist)

1. Write/extend a spec in `docs/specs/` (requirements, endpoints, errors).
2. Backend: migration → entity/repository → service → controller → `*IT` test.
3. If it needs ML: add an endpoint + engine/use-case in `inference/`, document it in
   `docs/specs/inference-api.md`, call it from the backend through a port.
4. Frontend: `src/features/<feature>/` with `api.ts`, `types.ts`, pages; register routes in `app/App.tsx`.
5. Record any significant decision as an ADR.
