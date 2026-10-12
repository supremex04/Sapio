"""Request correlation: every request carries an id (the backend's ``X-Request-Id``, or a new one) that is echoed
in the response, attached to every log line and included in error bodies, so one learner action can be followed
across services."""

import logging
import re
import uuid
from contextvars import ContextVar

from starlette.types import ASGIApp, Message, Receive, Scope, Send

REQUEST_ID_HEADER = "X-Request-Id"
_HEADER = REQUEST_ID_HEADER.lower().encode("latin-1")
#: Accept caller ids that are safe to log and echo; anything else is replaced.
_VALID_ID = re.compile(r"^[A-Za-z0-9._:-]{1,128}$")

_request_id: ContextVar[str | None] = ContextVar("request_id", default=None)


def current_request_id() -> str | None:
    return _request_id.get()


class RequestIdMiddleware:
    """Pure ASGI (not ``BaseHTTPMiddleware``), so the id stays visible to the error handlers around it."""

    def __init__(self, app: ASGIApp) -> None:
        self.app = app

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return
        incoming = next((v.decode("latin-1") for k, v in scope["headers"] if k == _HEADER), "")
        request_id = incoming if _VALID_ID.match(incoming) else uuid.uuid4().hex
        _request_id.set(request_id)

        async def send_with_id(message: Message) -> None:
            if message["type"] == "http.response.start":
                message.setdefault("headers", [])
                message["headers"] = [*message["headers"], (_HEADER, request_id.encode("latin-1"))]
            await send(message)

        await self.app(scope, receive, send_with_id)


class RequestIdFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.request_id = _request_id.get() or "-"
        return True


def configure_logging(level: int = logging.INFO) -> None:
    """Service logs (``ved_inference.*``) with the request id; idempotent (``--reload`` imports twice)."""
    logger = logging.getLogger("ved_inference")
    if any(isinstance(f, RequestIdFilter) for h in logger.handlers for f in h.filters):
        return
    handler = logging.StreamHandler()
    handler.addFilter(RequestIdFilter())
    handler.setFormatter(
        logging.Formatter("%(asctime)s %(levelname)-5s [%(request_id)s] %(name)s: %(message)s")
    )
    logger.addHandler(handler)
    logger.setLevel(level)
    logger.propagate = False
