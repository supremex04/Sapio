"""HTTP API of the renderer. Called only by the backend, with the renderer's own key (it guards render capacity;
the service holds nothing else worth protecting)."""

import base64
import hmac
import logging
import os
from typing import Annotated, Literal

from fastapi import FastAPI, Header, HTTPException
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

from sapio_renderer import render as renderer
from sapio_renderer import safety, speech

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)-5s %(name)s: %(message)s")
log = logging.getLogger("sapio_renderer")

API_KEY = os.environ.get("SAPIO_RENDERER_API_KEY", "dev-renderer-key")
# The key must not reach scene code: renders run with an empty environment, and it is dropped here too.
os.environ.pop("SAPIO_RENDERER_API_KEY", None)


class ApiModel(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class RenderRequest(ApiModel):
    code: str = Field(max_length=safety.MAX_CODE_CHARS)
    quality: Literal["preview", "final"] = "final"
    #: The spoken line of each beat, in order (the scene plays them with sapio_narration.narrate)
    narration: list[Annotated[str, Field(max_length=speech.MAX_TEXT)]] = Field(
        default_factory=list, max_length=8
    )


class RenderResponse(ApiModel):
    video_base64: str
    thumbnail_base64: str
    duration_seconds: float
    #: Whether the video has spoken narration
    narrated: bool = False


class RenderError(ApiModel):
    #: code (rejected before running), error (Manim raised), timeout, output (no usable video)
    kind: str
    message: str


app = FastAPI(title="Sapio renderer", version="1.0")


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "engine": "manim 0.19.0", "speech": speech.engine_name()}


@app.post("/v1/render", response_model=RenderResponse, responses={422: {"model": RenderError}})
async def render(body: RenderRequest, x_internal_api_key: Annotated[str | None, Header()] = None):
    if not x_internal_api_key or not hmac.compare_digest(x_internal_api_key, API_KEY):
        raise HTTPException(status_code=401, detail="Missing or wrong renderer key")
    try:
        result = await run_in_threadpool(renderer.render, body.code, body.quality, body.narration)
    except safety.Rejected as e:
        return JSONResponse(status_code=422, content=RenderError(kind="code", message=e.reason).model_dump())
    except renderer.RenderFailed as e:
        log.info("Render failed (%s): %s", e.kind, e.message.splitlines()[-1] if e.message else "")
        return JSONResponse(status_code=422, content=RenderError(kind=e.kind, message=e.message).model_dump())
    log.info(
        "Rendered %.1f s of video (%d KB, %s)",
        result.duration_seconds,
        len(result.video) // 1024,
        "narrated" if result.narrated else "silent",
    )
    return RenderResponse(
        video_base64=base64.b64encode(result.video).decode(),
        thumbnail_base64=base64.b64encode(result.thumbnail).decode(),
        duration_seconds=result.duration_seconds,
        narrated=result.narrated,
    ).model_dump(by_alias=True)
