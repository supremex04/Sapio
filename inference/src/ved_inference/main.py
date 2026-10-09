import logging
from collections.abc import AsyncIterator, Callable
from contextlib import asynccontextmanager

from fastapi import FastAPI

from ved_inference.api.routes import router
from ved_inference.config import Settings, get_settings
from ved_inference.documents.loader import DocumentLoader
from ved_inference.engines.base import OcrEngine
from ved_inference.engines.registry import build_engine
from ved_inference.service import OcrService

log = logging.getLogger("ved_inference")


def create_app(engine_factory: Callable[[Settings], OcrEngine] = build_engine) -> FastAPI:
    """Application factory. Tests pass a fake ``engine_factory``."""

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        settings = get_settings()
        engine = engine_factory(settings)
        log.info("OCR engine ready: %s", engine.name)
        app.state.ocr_service = OcrService(
            loader=DocumentLoader(max_pages=settings.max_pages, pdf_dpi=settings.pdf_render_dpi),
            engine=engine,
            max_concurrency=settings.max_concurrency,
        )
        yield
        app.state.ocr_service.close()

    app = FastAPI(title="Ved Inference Service", version="0.1.0", lifespan=lifespan)
    app.include_router(router)
    return app


app = create_app()
