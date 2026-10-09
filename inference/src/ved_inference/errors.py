class DocumentError(Exception):
    """The input document cannot be processed (client error; message is safe to show to users)."""


class EngineError(Exception):
    """The OCR engine failed or is unavailable (server error)."""
