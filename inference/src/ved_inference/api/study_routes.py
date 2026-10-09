"""LLM-backed study endpoints. Plain ``def`` handlers: FastAPI runs them in a worker thread."""

from collections.abc import Callable
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Request, status

from ved_inference.api import study_schemas as s
from ved_inference.api.security import require_internal_api_key
from ved_inference.errors import DocumentError, EngineError
from ved_inference.study.service import StudyService

router = APIRouter(prefix="/v1/study", dependencies=[Depends(require_internal_api_key)])


def get_study_service(request: Request) -> StudyService:
    return request.app.state.study_service


Service = Annotated[StudyService, Depends(get_study_service)]


def _run[R](fn: Callable[[], R]) -> R:
    try:
        return fn()
    except DocumentError as exc:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_CONTENT, detail=str(exc)) from exc
    except EngineError as exc:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exc)) from exc


@router.post("/material", response_model=s.MaterialResponse)
def material(body: s.MaterialRequest, service: Service) -> s.MaterialResponse:
    return _run(lambda: service.build_material(body))


@router.post("/lesson", response_model=s.LessonResponse)
def lesson(body: s.LessonRequest, service: Service) -> s.LessonResponse:
    return _run(lambda: service.lesson(body))


@router.post("/questions", response_model=s.QuestionsResponse)
def questions(body: s.QuestionsRequest, service: Service) -> s.QuestionsResponse:
    return _run(lambda: service.questions(body))


@router.post("/grade", response_model=s.GradeResponse)
def grade(body: s.GradeRequest, service: Service) -> s.GradeResponse:
    return _run(lambda: service.grade(body))


@router.post("/assessment-feedback", response_model=s.FeedbackResponse)
def assessment_feedback(body: s.FeedbackRequest, service: Service) -> s.FeedbackResponse:
    return _run(lambda: service.assessment_feedback(body))


@router.post("/ask", response_model=s.AskResponse)
def ask(body: s.AskRequest, service: Service) -> s.AskResponse:
    return _run(lambda: service.ask(body))
