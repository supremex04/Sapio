import io

import pymupdf
import pytest
from PIL import Image, ImageDraw, ImageFont

from ved_inference.config import get_settings


@pytest.fixture(autouse=True)
def _isolated_settings(monkeypatch):
    monkeypatch.setenv("VED_INFERENCE_API_KEY", "test-key")
    monkeypatch.setenv("VED_INFERENCE_ENGINE", "rapid")
    get_settings.cache_clear()
    yield
    get_settings.cache_clear()


def make_pdf(pages: list[str]) -> bytes:
    doc = pymupdf.open()
    for text in pages:
        page = doc.new_page()
        page.insert_text((72, 100), text, fontsize=24)
    data = doc.tobytes()
    doc.close()
    return data


def make_text_image(lines: list[str]) -> bytes:
    image = Image.new("RGB", (900, 120 + 70 * len(lines)), "white")
    draw = ImageDraw.Draw(image)
    font = ImageFont.load_default(size=40)
    for i, line in enumerate(lines):
        draw.text((40, 40 + 70 * i), line, fill="black", font=font)
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()
