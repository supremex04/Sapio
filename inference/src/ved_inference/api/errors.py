"""The error half of the wire contract: every non-2xx response has the same JSON body, built here and nowhere else.

Routes never translate exceptions themselves; domain code raises an :class:`~ved_inference.errors.InferenceError`
subclass and these handlers render it. See docs/specs/inference-api.md ("Errors").
"""

import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from ved_inference.api.schemas import ApiModel
from ved_inference.errors import InferenceError
from ved_inference.observability import current_request_id

log = logging.getLogger(__name__)


class ErrorResponse(ApiModel):
    """``code`` is stable and machine-readable; ``message`` is for people (user-safe for INPUT_REJECTED and
    MODEL_UNAVAILABLE, operator-facing otherwise); ``retryable`` says whether the same request may succeed later."""

    code: str
    message: str
    retryable: bool
    request_id: str | None = None


#: Statuses raised by the framework itself (routing, auth dependency, upload limit).
_HTTP_CODES = {
    401: "UNAUTHORIZED",
    404: "NOT_FOUND",
    405: "METHOD_NOT_ALLOWED",
    413: "INPUT_TOO_LARGE",
}

#: Documented on every route, so the OpenAPI contract lists the error body.
ERROR_RESPONSES: dict[int | str, dict] = {
    status: {"model": ErrorResponse} for status in (400, 401, 413, 422, 500, 503)
}


def _respond(
    status: int, code: str, message: str, retryable: bool, retry_after: float | None = None
) -> JSONResponse:
    body = ErrorResponse(code=code, message=message, retryable=retryable, request_id=current_request_id())
    headers = {"Retry-After": str(max(1, round(retry_after)))} if retry_after is not None else None
    return JSONResponse(
        body.model_dump(by_alias=True, exclude_none=True), status_code=status, headers=headers
    )


async def _inference_error(_: Request, exc: Exception) -> JSONResponse:
    assert isinstance(exc, InferenceError)
    if exc.status >= 500 and not exc.retryable:
        log.error("%s: %s", exc.code, exc.message, exc_info=exc.__cause__)
    else:
        log.warning("%s: %s", exc.code, exc.message)
    return _respond(exc.status, exc.code, exc.message, exc.retryable, exc.retry_after)


async def _validation_error(_: Request, exc: Exception) -> JSONResponse:
    assert isinstance(exc, RequestValidationError)
    # The caller sent a body that does not match the contract: a bug or version skew, never the learner's input.
    problems = "; ".join(
        f"{'.'.join(str(p) for p in e.get('loc', ()))}: {e.get('msg', '')}" for e in exc.errors()[:5]
    )
    log.error("Contract violation: %s", problems)
    return _respond(400, "CONTRACT_VIOLATION", f"Request does not match the contract: {problems}", False)


async def _http_error(_: Request, exc: Exception) -> JSONResponse:
    assert isinstance(exc, StarletteHTTPException)
    code = _HTTP_CODES.get(exc.status_code, "HTTP_ERROR")
    return _respond(exc.status_code, code, str(exc.detail), False)


async def _unexpected(_: Request, exc: Exception) -> JSONResponse:
    log.exception("Unhandled error", exc_info=exc)
    return _respond(500, "INTERNAL_ERROR", "Unexpected error in the inference service", False)


def install_error_handlers(app: FastAPI) -> None:
    app.add_exception_handler(InferenceError, _inference_error)
    app.add_exception_handler(RequestValidationError, _validation_error)
    app.add_exception_handler(StarletteHTTPException, _http_error)
    app.add_exception_handler(Exception, _unexpected)
