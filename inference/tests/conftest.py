import io

import pymupdf
import pytest
from PIL import Image, ImageDraw, ImageFont

from ved_inference.config import get_settings


@pytest.fixture(autouse=True)
def _isolated_settings(monkeypatch, tmp_path):
    # Tests never read the developer's settings.yaml or .env.
    monkeypatch.chdir(tmp_path)
    monkeypatch.setenv("VED_INFERENCE_CONFIG", str(tmp_path / "absent.yaml"))
    monkeypatch.setenv("VED_INFERENCE_API_KEY", "test-key")
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


def make_mixed_pdf() -> bytes:
    """Page 1 has a text layer; page 2 is an image only (a 'scan')."""
    doc = pymupdf.open()
    page = doc.new_page()
    page.insert_text(
        (72, 100), "Photosynthesis converts light energy into chemical energy in plants.", fontsize=12
    )
    scan = doc.new_page()
    scan.insert_image(scan.rect, stream=make_text_image(["Scanned page"]))
    data = doc.tobytes()
    doc.close()
    return data


def png_bytes(color: str = "green", size: tuple[int, int] = (400, 260)) -> bytes:
    image = Image.new("RGB", size, "white")
    draw = ImageDraw.Draw(image)
    draw.rectangle((10, 10, size[0] - 10, size[1] - 10), outline=color, width=8)
    draw.ellipse((size[0] // 4, size[1] // 4, 3 * size[0] // 4, 3 * size[1] // 4), fill=color)
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()


def textbook_pdf() -> bytes:
    """Two chapters with an outline, body text, a raster figure, a vector diagram, a repeated logo."""
    doc = pymupdf.open()
    logo = png_bytes("red", (160, 160))
    p1 = doc.new_page()
    p1.insert_image(pymupdf.Rect(500, 20, 560, 80), stream=logo)
    p1.insert_text((72, 90), "Chapter 1 The Cell", fontsize=24)
    p1.insert_text((72, 125), "1.1 Cell theory", fontsize=15)
    p1.insert_textbox(
        pymupdf.Rect(72, 135, 520, 230), "All living things are made of cells. " * 6, fontsize=11
    )
    p1.insert_image(pymupdf.Rect(150, 240, 450, 440), stream=png_bytes())
    p1.insert_text((150, 460), "Figure 1.1: A typical plant cell", fontsize=10)
    p2 = doc.new_page()
    p2.insert_image(pymupdf.Rect(500, 20, 560, 80), stream=logo)
    p2.insert_text((72, 90), "Chapter 2 Energy", fontsize=24)
    p2.insert_textbox(
        pymupdf.Rect(72, 110, 520, 230), "Mitochondria convert glucose into ATP. " * 8, fontsize=11
    )
    shape = p2.new_shape()
    shape.draw_rect(pymupdf.Rect(150, 250, 450, 450))
    shape.draw_circle((300, 350), 60)
    shape.finish(color=(0, 0, 1), width=2)
    shape.commit()
    p2.insert_text((150, 470), "Figure 2.1: Energy flow", fontsize=10)
    p3 = doc.new_page()
    p3.insert_image(pymupdf.Rect(500, 20, 560, 80), stream=logo)
    p3.insert_text((72, 90), "2.1 Respiration", fontsize=15)
    p3.insert_textbox(
        pymupdf.Rect(72, 110, 520, 300), "Cells release energy from glucose in respiration. " * 8, fontsize=11
    )
    doc.set_toc(
        [
            [1, "Chapter 1 The Cell", 1],
            [2, "1.1 Cell theory", 1],
            [1, "Chapter 2 Energy", 2],
            [2, "2.1 Respiration", 3],
        ]
    )
    data = doc.tobytes()
    doc.close()
    return data


def scanned(pdf: bytes) -> bytes:
    """The same pages as images only (no text layer)."""
    src = pymupdf.open(stream=pdf, filetype="pdf")
    out = pymupdf.open()
    for page in src:
        pix = page.get_pixmap(dpi=150)
        new = out.new_page(width=page.rect.width, height=page.rect.height)
        new.insert_image(new.rect, pixmap=pix)
    return out.tobytes()
