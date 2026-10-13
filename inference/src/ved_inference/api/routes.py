import base64
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request, UploadFile, status
from fastapi.concurrency import run_in_threadpool

from ved_inference.api.errors import ERROR_RESPONSES
from ved_inference.api.schemas import (
    ChunkOut,
    ExtractResponse,
    FigureOut,
    HealthResponse,
    NodeOut,
    SegmentOut,
    TreeOut,
)
from ved_inference.api.security import require_internal_api_key
from ved_inference.config import Settings, get_settings
from ved_inference.engines.markdown import join_pages
from ved_inference.llm.base import Embedder, LlmClient
from ved_inference.parsing.parser import DocumentParser, ParsedDocument

router = APIRouter()


def get_parser(request: Request) -> DocumentParser:
    return request.app.state.parser


@router.get("/health", response_model=HealthResponse)
def health(request: Request) -> HealthResponse:
    llm: LlmClient = request.app.state.llm
    embedder: Embedder = request.app.state.embedder
    return HealthResponse(
        status="ok",
        ocr_engine=request.app.state.parser.ocr_engine_name,
        llm_model=llm.model_name,
        embedding_model=embedder.model_name,
        embedding_dimensions=embedder.dimensions,
        speech_model=request.app.state.speech.name,
    )


@router.post(
    "/v1/extract",
    response_model=ExtractResponse,
    dependencies=[Depends(require_internal_api_key)],
    responses=ERROR_RESPONSES,
)
async def extract(
    file: UploadFile,
    parser: Annotated[DocumentParser, Depends(get_parser)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> ExtractResponse:
    data = await file.read(settings.parsing.max_upload_bytes + 1)
    if len(data) > settings.parsing.max_upload_bytes:
        raise HTTPException(status.HTTP_413_CONTENT_TOO_LARGE, detail="File too large")
    # Parsing is blocking (layout model, OCR); keep the event loop free.
    parsed = await run_in_threadpool(parser.parse, data, file.filename or "document")
    return to_response(parsed)


def to_response(parsed: ParsedDocument) -> ExtractResponse:
    tree = parsed.tree
    return ExtractResponse(
        markdown=join_pages([p.markdown for p in parsed.pages]),
        engine=parsed.engine,
        language=parsed.language,
        page_count=parsed.page_count,
        text_pages=parsed.text_pages,
        ocr_pages=parsed.ocr_pages,
        segments=[
            SegmentOut(page_from=p.page, page_to=p.page, method=p.method, markdown=p.markdown)
            for p in parsed.pages
        ],  # type: ignore[arg-type]
        tree=TreeOut(
            nodes=[
                NodeOut(
                    key=n.key,
                    parent_key=n.parent_key,
                    level=n.level,
                    path=n.path,
                    label=n.label,
                    title=n.title,
                    ordinal=n.ordinal,
                    page_from=n.page_from,
                    page_to=n.page_to,
                )
                for n in tree.nodes
            ],
            chunks=[
                ChunkOut(
                    key=c.key,
                    node_key=c.node_key,
                    ordinal=c.ordinal,
                    text=c.text,
                    page_from=c.page_from,
                    page_to=c.page_to,
                )
                for c in tree.chunks
            ],
            figures=[
                FigureOut(
                    key=f.key,
                    node_key=f.node_key,
                    chunk_key=f.chunk_key,
                    ordinal=f.ordinal,
                    page=f.page,
                    bbox=list(f.bbox) if f.bbox else None,
                    caption=f.caption,
                    mime=f.image.mime,
                    width=f.image.width,
                    height=f.image.height,
                    image_base64=base64.b64encode(f.image.data).decode("ascii"),
                )
                for f in tree.figures
            ],
        ),
    )
