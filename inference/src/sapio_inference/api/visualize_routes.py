"""Visualization endpoints: storyboard and scene code (the backend renders through the renderer service)."""

from typing import Annotated

from fastapi import APIRouter, Depends, Request

from sapio_inference.api import visualize_schemas as v
from sapio_inference.api.errors import ERROR_RESPONSES
from sapio_inference.api.security import require_internal_api_key
from sapio_inference.visualize.service import VisualizeService

router = APIRouter(
    prefix="/v1/visualize", dependencies=[Depends(require_internal_api_key)], responses=ERROR_RESPONSES
)


def get_visualize_service(request: Request) -> VisualizeService:
    return request.app.state.visualize_service


Service = Annotated[VisualizeService, Depends(get_visualize_service)]


@router.post("/storyboard", response_model=v.StoryboardResponse)
def storyboard(body: v.StoryboardRequest, service: Service) -> v.StoryboardResponse:
    return service.storyboard(body)


@router.post("/code", response_model=v.SceneCodeResponse)
def code(body: v.SceneCodeRequest, service: Service) -> v.SceneCodeResponse:
    return service.code(body)
