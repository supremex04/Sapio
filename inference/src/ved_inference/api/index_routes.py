"""Indexing endpoints (figure descriptions, summaries, embeddings). Plain ``def``: run in a worker thread."""

from collections.abc import Callable
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request, status

from ved_inference.api import index_schemas as s
from ved_inference.api.security import require_internal_api_key
from ved_inference.errors import DocumentError, EngineError
from ved_inference.indexing.service import IndexingService

router = APIRouter(prefix="/v1/index", dependencies=[Depends(require_internal_api_key)])


def get_indexing_service(request: Request) -> IndexingService:
    return request.app.state.indexing_service


Service = Annotated[IndexingService, Depends(get_indexing_service)]


def _run[R](fn: Callable[[], R]) -> R:
    try:
        return fn()
    except DocumentError as exc:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_CONTENT, detail=str(exc)) from exc
    except EngineError as exc:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exc)) from exc


@router.post("/figures", response_model=s.DescribeFiguresResponse)
def figures(body: s.DescribeFiguresRequest, service: Service) -> s.DescribeFiguresResponse:
    return _run(lambda: service.describe_figures(body))


@router.post("/summaries", response_model=s.SummarizeResponse)
def summaries(body: s.SummarizeRequest, service: Service) -> s.SummarizeResponse:
    return _run(lambda: service.summarize(body))


@router.post("/embed", response_model=s.EmbedResponse)
def embed(body: s.EmbedRequest, service: Service) -> s.EmbedResponse:
    return _run(lambda: service.embed(body))
