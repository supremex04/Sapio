"""Maps the configured engine name to an implementation (the only place that knows them all)."""

from collections.abc import Callable

from sapio_inference.config import EngineName, Settings
from sapio_inference.engines.base import OcrEngine


def _rapid(_: Settings) -> OcrEngine | None:
    # None = the parser's built-in layout OCR (RapidOCR inside the layout model).
    return None


def _unlimited_remote(settings: Settings) -> OcrEngine:
    from sapio_inference.engines.unlimited_remote import UnlimitedOcrRemoteEngine

    return UnlimitedOcrRemoteEngine(settings)


def _unlimited_local(settings: Settings) -> OcrEngine:
    from sapio_inference.engines.unlimited_local import UnlimitedOcrLocalEngine

    return UnlimitedOcrLocalEngine(settings)


# Imports are deferred so heavy optional dependencies load only for the engine actually used.
ENGINE_FACTORIES: dict[EngineName, Callable[[Settings], OcrEngine | None]] = {
    "rapid": _rapid,
    "unlimited-remote": _unlimited_remote,
    "unlimited-local": _unlimited_local,
}


def build_engine(settings: Settings) -> OcrEngine | None:
    return ENGINE_FACTORIES[settings.ocr.engine](settings)
