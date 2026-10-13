import secrets
from typing import Annotated

from fastapi import Depends, Header, HTTPException, status

from sapio_inference.config import Settings, get_settings


def require_internal_api_key(
    settings: Annotated[Settings, Depends(get_settings)],
    x_internal_api_key: Annotated[str | None, Header()] = None,
) -> None:
    """Only the backend may call this service; it authenticates with a shared secret."""
    expected = settings.api_key.get_secret_value()
    if not expected:
        return
    if x_internal_api_key is None or not secrets.compare_digest(x_internal_api_key, expected):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid internal API key")
