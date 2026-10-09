import logging
from collections.abc import AsyncIterator, Callable
from contextlib import asynccontextmanager

from fastapi import FastAPI

from ved_inference.api import routes, study_routes
from ved_inference.config import Settings, get_settings
from ved_inference.documents.loader import DocumentLoader
from ved_inference.engines.base import OcrEngine
from ved_inference.engines.registry import build_engine
from ved_inference.extraction.extractor import DocumentExtractor
from ved_inference.llm.base import LlmClient
from ved_inference.llm.registry import build_llm
from ved_inference.study.service import StudyService

log = logging.getLogger("ved_inference")


def create_app(
    engine_factory: Callable[[Settings], OcrEngine] = build_engine,
    llm_factory: Callable[[Settings], LlmClient] = build_llm,
) -> FastAPI:
    """Application factory (composition root). Tests pass fake factories."""

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        settings = get_settings()
        engine = engine_factory(settings)
        llm = llm_factory(settings)
        log.info("OCR engine: %s, LLM: %s", engine.name, llm.model_name)
        app.state.extractor = DocumentExtractor(
            engine,
            DocumentLoader(max_pages=settings.max_pages, pdf_dpi=settings.pdf_render_dpi),
            max_pages=settings.max_pages,
            pdf_dpi=settings.pdf_render_dpi,
            native_min_chars=settings.native_text_min_chars,
            max_concurrency=settings.max_concurrency,
        )
        app.state.llm = llm
        app.state.study_service = StudyService(llm)
        yield
        app.state.extractor.close()
        llm.close()

    app = FastAPI(title="Ved Inference Service", version="0.2.0", lifespan=lifespan)
    app.include_router(routes.router)
    app.include_router(study_routes.router)
    return app


app = create_app()
