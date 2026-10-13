import logging
from collections.abc import AsyncIterator, Callable
from contextlib import asynccontextmanager

from fastapi import FastAPI

from ved_inference.api import index_routes, media_routes, routes, study_routes
from ved_inference.api.errors import install_error_handlers
from ved_inference.config import Settings, get_settings
from ved_inference.engines.base import OcrEngine
from ved_inference.engines.registry import build_engine
from ved_inference.indexing.service import IndexingService
from ved_inference.llm.base import Embedder, LlmClient
from ved_inference.llm.registry import build_embedder, build_llm
from ved_inference.observability import RequestIdMiddleware, configure_logging
from ved_inference.parsing.parser import DocumentParser
from ved_inference.speech.base import SpeechEngine
from ved_inference.speech.notes import LectureNotes
from ved_inference.speech.registry import build_speech
from ved_inference.study.service import StudyService

log = logging.getLogger("ved_inference")


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
        app.state.indexing_service = IndexingService(llm, embedder, settings.indexing)
        app.state.speech = speech_factory(settings)
        app.state.lecture_notes = LectureNotes(llm, settings.lecture_notes, settings.parsing)
        yield
        app.state.speech.close()
        app.state.parser.close()
        llm.close()
        embedder.close()

    app = FastAPI(title="Ved Inference Service", version="0.3.0", lifespan=lifespan)
    install_error_handlers(app)
    app.add_middleware(RequestIdMiddleware)
    app.include_router(routes.router)
    app.include_router(study_routes.router)
    app.include_router(index_routes.router)
    app.include_router(media_routes.router)
    return app


app = create_app()
