from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request, UploadFile, status
from fastapi.concurrency import run_in_threadpool

from ved_inference.api.schemas import ExtractResponse, HealthResponse, SegmentOut
from ved_inference.api.security import require_internal_api_key
from ved_inference.config import Settings, get_settings
from ved_inference.engines.markdown import join_pages
from ved_inference.errors import DocumentError, EngineError
from ved_inference.extraction.extractor import DocumentExtractor
from ved_inference.llm.base import LlmClient

router = APIRouter()


def get_extractor(request: Request) -> DocumentExtractor:
    return request.app.state.extractor


def get_llm(request: Request) -> LlmClient:
    return request.app.state.llm


@router.get("/health", response_model=HealthResponse)
def health(
    extractor: Annotated[DocumentExtractor, Depends(get_extractor)],
    llm: Annotated[LlmClient, Depends(get_llm)],
) -> HealthResponse:
    return HealthResponse(status="ok", ocr_engine=extractor.ocr_engine_name, llm_model=llm.model_name)


@router.post("/v1/extract", response_model=ExtractResponse, dependencies=[Depends(require_internal_api_key)])
async def extract(
    file: UploadFile,
    extractor: Annotated[DocumentExtractor, Depends(get_extractor)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> ExtractResponse:
    data = await file.read(settings.max_upload_bytes + 1)
    if len(data) > settings.max_upload_bytes:
        raise HTTPException(status.HTTP_413_CONTENT_TOO_LARGE, detail="File too large")
    try:
        # Extraction is blocking (CPU/GPU/HTTP); keep the event loop free.
        result = await run_in_threadpool(extractor.extract, data, file.filename or "document")
    except DocumentError as exc:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_CONTENT, detail=str(exc)) from exc
    except EngineError as exc:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exc)) from exc
    return ExtractResponse(
        markdown=join_pages([s.markdown for s in result.segments]),
        engine=result.engine,
        page_count=result.page_count,
        text_pages=result.text_pages,
        ocr_pages=result.ocr_pages,
        segments=[
            SegmentOut(page_from=s.page_from, page_to=s.page_to, method=s.method, markdown=s.markdown)
            for s in result.segments
        ],
    )
