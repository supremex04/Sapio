import logging
from collections.abc import AsyncIterator, Callable
from contextlib import asynccontextmanager

from fastapi import FastAPI

from sapio_inference.api import index_routes, media_routes, routes, study_routes, visualize_routes
from sapio_inference.api.errors import install_error_handlers
from sapio_inference.config import Settings, get_settings
from sapio_inference.engines.base import OcrEngine
from sapio_inference.engines.registry import build_engine
from sapio_inference.indexing.service import IndexingService
from sapio_inference.llm.base import Embedder, LlmClient
from sapio_inference.llm.registry import build_embedder, build_llm
from sapio_inference.observability import RequestIdMiddleware, configure_logging
from sapio_inference.parsing.parser import DocumentParser
from sapio_inference.speech.base import SpeechEngine
from sapio_inference.speech.notes import LectureNotes
from sapio_inference.speech.registry import build_speech
from sapio_inference.study.service import StudyService
from sapio_inference.visualize.service import VisualizeService

log = logging.getLogger("sapio_inference")


def create_app(
    engine_factory: Callable[[Settings], OcrEngine | None] = build_engine,
    llm_factory: Callable[[Settings], LlmClient] = build_llm,
    embedder_factory: Callable[[Settings], Embedder] = build_embedder,
    speech_factory: Callable[[Settings], SpeechEngine] = build_speech,
) -> FastAPI:
    """Application factory (composition root). Tests pass fake factories."""
    configure_logging()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        settings = get_settings()
        engine = engine_factory(settings)
        llm = llm_factory(settings)
        embedder = embedder_factory(settings)
        app.state.parser = DocumentParser(engine, settings.parsing)
        log.info(
            "OCR: %s, LLM: %s, embeddings: %s",
            app.state.parser.ocr_engine_name,
            llm.model_name,
            embedder.model_name,
        )
        app.state.llm = llm
        app.state.embedder = embedder
        app.state.study_service = StudyService(llm, config=settings.study)
        app.state.visualize_service = VisualizeService(llm)
        app.state.indexing_service = IndexingService(llm, embedder, settings.indexing)
        app.state.speech = speech_factory(settings)
        app.state.lecture_notes = LectureNotes(llm, settings.lecture_notes, settings.parsing)
        yield
        app.state.speech.close()
        app.state.parser.close()
        llm.close()
        embedder.close()

    app = FastAPI(title="Sapio Inference Service", version="0.3.0", lifespan=lifespan)
    install_error_handlers(app)
    app.add_middleware(RequestIdMiddleware)
    app.include_router(routes.router)
    app.include_router(study_routes.router)
    app.include_router(index_routes.router)
    app.include_router(media_routes.router)
    app.include_router(visualize_routes.router)
    return app


app = create_app()
