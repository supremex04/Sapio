"""Indexing endpoints (figure descriptions, summaries, embeddings). Plain ``def``: run in a worker thread."""

from typing import Annotated

from fastapi import APIRouter, Depends, Request

from ved_inference.api import index_schemas as s
from ved_inference.api.errors import ERROR_RESPONSES
from ved_inference.api.security import require_internal_api_key
from ved_inference.indexing.service import IndexingService

router = APIRouter(
    prefix="/v1/index", dependencies=[Depends(require_internal_api_key)], responses=ERROR_RESPONSES
)


def get_indexing_service(request: Request) -> IndexingService:
    return request.app.state.indexing_service


Service = Annotated[IndexingService, Depends(get_indexing_service)]


@router.post("/figures", response_model=s.DescribeFiguresResponse)
def figures(body: s.DescribeFiguresRequest, service: Service) -> s.DescribeFiguresResponse:
    return service.describe_figures(body)


@router.post("/summaries", response_model=s.SummarizeResponse)
def summaries(body: s.SummarizeRequest, service: Service) -> s.SummarizeResponse:
    return service.summarize(body)


@router.post("/embed", response_model=s.EmbedResponse)
def embed(body: s.EmbedRequest, service: Service) -> s.EmbedResponse:
    return service.embed(body)
