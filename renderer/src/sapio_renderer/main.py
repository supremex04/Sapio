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

from sapio_renderer import circuit, molecule, safety, speech, trace
from sapio_renderer import render as renderer

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
    #: Layout problems seen at the end of the beats (off-frame, over the subtitles, drawn over something). The video
    #: is usable; these can go back to the scene's author to fix.
    layout_problems: list[str] = Field(default_factory=list)


class RenderError(ApiModel):
    #: code (rejected before running), error (Manim raised), timeout, output (no usable video)
    kind: str
    message: str


class MoleculeRequest(ApiModel):
    smiles: str = Field(max_length=molecule.MAX_SMILES)
    #: Each beat's focus: a SMARTS pattern or a named group ("group:methyl"); empty: nothing highlighted
    highlights: list[Annotated[str, Field(max_length=molecule.MAX_PATTERN)]] = Field(
        default_factory=list, max_length=8
    )
    #: Each beat's narration: a group it names rescues a focus that lights up nothing or most of the molecule
    captions: list[Annotated[str, Field(max_length=speech.MAX_TEXT)]] = Field(
        default_factory=list, max_length=8
    )


class MoleculeResponse(ApiModel):
    canonical_smiles: str
    formula: str
    weight: float
    heavy_atoms: int
    mol_block: str
    three_d: bool
    png_base64: str
    highlights: list[list[int]]
    #: Per highlight: the 2D drawing with those atoms highlighted (PNG), for viewers without WebGL
    drawings_base64: list[str]


class TraceRequest(ApiModel):
    code: str = Field(max_length=safety.MAX_PROGRAM_CHARS)
    stdin: list[Annotated[str, Field(max_length=500)]] = Field(default_factory=list, max_length=50)


class CircuitNode(ApiModel):
    id: str = Field(max_length=20)
    x: int
    y: int
    ground: bool = False


class CircuitPart(ApiModel):
    id: str = Field(max_length=20)
    kind: str = Field(max_length=20)
    #: Ohms, farads, henries, volts or amperes; none for a wire
    value: float | None = None
    a: str = Field(max_length=20)
    b: str = Field(max_length=20)


class CircuitRequest(ApiModel):
    nodes: list[CircuitNode] = Field(max_length=circuit.MAX_NODES)
    parts: list[CircuitPart] = Field(max_length=circuit.MAX_PARTS)


class CircuitError(ApiModel):
    kind: str = "input"
    message: str
    #: Every problem found, each in words the circuit's author can act on
    problems: list[str]


class NarrateRequest(ApiModel):
    lines: list[Annotated[str, Field(max_length=speech.MAX_TEXT)]] = Field(min_length=1, max_length=8)


class BeatTiming(ApiModel):
    start: float
    seconds: float


class NarrateResponse(ApiModel):
    #: AAC in MP4 (audio/mp4); null when speech is unavailable (the beats then carry estimated timings)
    audio_base64: str | None
    beats: list[BeatTiming]


app = FastAPI(title="Sapio renderer", version="1.1")


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "engine": "manim 0.19.0", "speech": speech.engine_name()}


def _authorised(key: str | None) -> None:
    if not key or not hmac.compare_digest(key, API_KEY):
        raise HTTPException(status_code=401, detail="Missing or wrong renderer key")


def _error(kind: str, message: str) -> JSONResponse:
    return JSONResponse(status_code=422, content=RenderError(kind=kind, message=message).model_dump())


@app.post("/v1/molecule", response_model=MoleculeResponse, responses={422: {"model": RenderError}})
async def build_molecule(body: MoleculeRequest, x_internal_api_key: Annotated[str | None, Header()] = None):
    """A molecule from SMILES (RDKit): 3D structure, 2D drawing, facts, atoms matching each highlight."""
    _authorised(x_internal_api_key)
    try:
        built = await run_in_threadpool(molecule.build, body.smiles, body.highlights, body.captions)
    except molecule.Invalid as e:
        return _error("input", e.message)
    return MoleculeResponse(
        canonical_smiles=built.canonical_smiles,
        formula=built.formula,
        weight=built.weight,
        heavy_atoms=built.heavy_atoms,
        mol_block=built.mol_block,
        three_d=built.three_d,
        png_base64=base64.b64encode(built.png).decode(),
        highlights=built.highlights,
        drawings_base64=[base64.b64encode(d).decode() for d in built.drawings],
    ).model_dump(by_alias=True)


@app.post("/v1/trace", responses={422: {"model": RenderError}})
async def trace_program(body: TraceRequest, x_internal_api_key: Annotated[str | None, Header()] = None):
    """Runs a Python program line by line in the sandbox: ``{steps, output, error, truncated}``."""
    _authorised(x_internal_api_key)
    try:
        return await run_in_threadpool(trace.trace, body.code, body.stdin)
    except safety.Rejected as e:
        return _error("code", e.reason)
    except renderer.RenderFailed as e:
        return _error(e.kind, e.message)


@app.post("/v1/circuit", responses={422: {"model": CircuitError}})
async def solve_circuit(body: CircuitRequest, x_internal_api_key: Annotated[str | None, Header()] = None):
    """Checks a circuit (as a drawing and as a circuit) and solves it exactly: the DC operating point, or the
    response from switch-on with ``times``, and node voltages and part currents and voltages at each time."""
    _authorised(x_internal_api_key)
    try:
        return await run_in_threadpool(circuit.solve, body.model_dump())
    except circuit.Invalid as e:
        return JSONResponse(
            status_code=422,
            content=CircuitError(message=" ".join(e.problems), problems=e.problems).model_dump(),
        )


@app.post("/v1/narrate", response_model=NarrateResponse)
async def narrate(body: NarrateRequest, x_internal_api_key: Annotated[str | None, Header()] = None):
    """The narration alone, one track, with where each line starts (for viewers that are not videos)."""
    _authorised(x_internal_api_key)
    track = await run_in_threadpool(speech.track, body.lines)
    return NarrateResponse(
        audio_base64=base64.b64encode(track.audio).decode() if track.audio else None,
        beats=[BeatTiming(start=start, seconds=seconds) for start, seconds in track.beats],
    ).model_dump(by_alias=True)


@app.post("/v1/render", response_model=RenderResponse, responses={422: {"model": RenderError}})
async def render(body: RenderRequest, x_internal_api_key: Annotated[str | None, Header()] = None):
    _authorised(x_internal_api_key)
    try:
        result = await run_in_threadpool(renderer.render, body.code, body.quality, body.narration)
    except safety.Rejected as e:
        return _error("code", e.reason)
    except renderer.RenderFailed as e:
        log.info("Render failed (%s): %s", e.kind, e.message.splitlines()[-1] if e.message else "")
        return _error(e.kind, e.message)
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
        layout_problems=result.layout_problems or [],
    ).model_dump(by_alias=True)
