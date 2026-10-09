from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request, UploadFile, status
from fastapi.concurrency import run_in_threadpool

from ved_inference.api.schemas import HealthResponse, OcrResponse
from ved_inference.api.security import require_internal_api_key
from ved_inference.config import Settings, get_settings
from ved_inference.errors import DocumentError, EngineError
from ved_inference.service import OcrService

router = APIRouter()


def get_ocr_service(request: Request) -> OcrService:
    return request.app.state.ocr_service


@router.get("/health", response_model=HealthResponse)
def health(service: Annotated[OcrService, Depends(get_ocr_service)]) -> HealthResponse:
    return HealthResponse(status="ok", engine=service.engine_name)


@router.post("/v1/ocr", response_model=OcrResponse, dependencies=[Depends(require_internal_api_key)])
async def ocr(
    file: UploadFile,
    service: Annotated[OcrService, Depends(get_ocr_service)],
    settings: Annotated[Settings, Depends(get_settings)],
) -> OcrResponse:
    data = await file.read(settings.max_upload_bytes + 1)
    if len(data) > settings.max_upload_bytes:
        raise HTTPException(status.HTTP_413_CONTENT_TOO_LARGE, detail="File too large")
    try:
        # Engines are blocking (CPU/GPU/HTTP); keep the event loop free.
        result = await run_in_threadpool(service.convert, data, file.filename or "document")
    except DocumentError as exc:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_CONTENT, detail=str(exc)) from exc
    except EngineError as exc:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exc)) from exc
    return OcrResponse(markdown=result.markdown, engine=result.engine, page_count=result.page_count)
