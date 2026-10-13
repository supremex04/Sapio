"""Error taxonomy of the inference service: what went wrong decides the wire status, code and retryability.

Domain code raises one of these; ``api.errors`` turns them into the error envelope of the contract
(docs/specs/inference-api.md): ``{"code", "message", "retryable"}``. Callers branch on ``code`` and
``retryable`` only, never on message text.
"""

from typing import ClassVar


class InferenceError(Exception):
    """Base of every expected failure. Subclasses fix the code, HTTP status and whether retrying can help."""

    code: ClassVar[str] = "INTERNAL_ERROR"
    status: ClassVar[int] = 500
    retryable: ClassVar[bool] = False

    def __init__(self, message: str, *, retry_after: float | None = None) -> None:
        super().__init__(message)
        self.message = message
        #: Seconds until a retry is likely to succeed (sent as Retry-After), when known.
        self.retry_after = retry_after


class DocumentError(InferenceError):
    """The input cannot be processed (corrupt file, too little text). The message is safe to show to users."""

    code = "INPUT_REJECTED"
    status = 422


class EngineError(InferenceError):
    """An engine or model is busy, out of quota or unreachable, or returned unusable output. Worth retrying."""

    code = "MODEL_UNAVAILABLE"
    status = 503
    retryable = True


class ConfigurationError(InferenceError):
    """The service is misconfigured (missing key or extra, dimension mismatch, wrong route). An operator must fix
    it; retrying does not help. The message is for operators: callers show their own generic text."""

    code = "MISCONFIGURED"
    status = 500
