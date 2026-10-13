"""LLM-backed study endpoints. Plain ``def`` handlers: FastAPI runs them in a worker thread."""

from typing import Annotated

from fastapi import APIRouter, Depends, Request

from sapio_inference.api import study_schemas as s
from sapio_inference.api.errors import ERROR_RESPONSES
from sapio_inference.api.security import require_internal_api_key
from sapio_inference.study.service import StudyService

router = APIRouter(
    prefix="/v1/study", dependencies=[Depends(require_internal_api_key)], responses=ERROR_RESPONSES
)


def get_study_service(request: Request) -> StudyService:
    return request.app.state.study_service


Service = Annotated[StudyService, Depends(get_study_service)]


@router.post("/material", response_model=s.MaterialResponse)
def material(body: s.MaterialRequest, service: Service) -> s.MaterialResponse:
    return service.build_material(body)


@router.post("/lesson", response_model=s.LessonResponse)
def lesson(body: s.LessonRequest, service: Service) -> s.LessonResponse:
    return service.lesson(body)


@router.post("/questions", response_model=s.QuestionsResponse)
def questions(body: s.QuestionsRequest, service: Service) -> s.QuestionsResponse:
    return service.questions(body)


@router.post("/grade", response_model=s.GradeResponse)
def grade(body: s.GradeRequest, service: Service) -> s.GradeResponse:
    return service.grade(body)


@router.post("/assessment-feedback", response_model=s.FeedbackResponse)
def assessment_feedback(body: s.FeedbackRequest, service: Service) -> s.FeedbackResponse:
    return service.assessment_feedback(body)


@router.post("/answer", response_model=s.AnswerResponse)
def answer(body: s.AnswerRequest, service: Service) -> s.AnswerResponse:
    return service.answer(body)


@router.post("/notes", response_model=s.NotesResponse)
def notes(body: s.NotesRequest, service: Service) -> s.NotesResponse:
    return service.notes(body)
