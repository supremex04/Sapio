"""Figure hygiene: re-encoding, decorative-image filtering, and figure detection on scanned pages."""

import io
from collections import Counter

import numpy as np
import pymupdf
from PIL import Image

from ved_inference.parsing.models import BBox, Block, FigureImage

MAX_SIDE = 1600
MIN_SIDE_PX = 100
MIN_PAGE_AREA_FRACTION = 0.012
MAX_ASPECT = 6.0
REPEAT_PAGES = 3  # the same image on this many pages is a logo/background
BACKGROUND_AREA_FRACTION = 0.85
MAX_FIGURES = 400


def encode(image: Image.Image) -> FigureImage:
    """Downscales to ≤1600 px and picks PNG for line art / transparency, JPEG for photos."""
    img = image
    if max(img.size) > MAX_SIDE:
        img = img.copy()
        img.thumbnail((MAX_SIDE, MAX_SIDE))
    line_art = img.mode in ("RGBA", "LA", "P") or img.convert("RGB").getcolors(maxcolors=512) is not None
    buf = io.BytesIO()
    if line_art:
        img.save(buf, format="PNG", optimize=True)
        mime = "image/png"
    else:
        img.convert("RGB").save(buf, format="JPEG", quality=85, optimize=True)
        mime = "image/jpeg"
    return FigureImage(data=buf.getvalue(), mime=mime, width=img.width, height=img.height)


def decode(data: bytes) -> Image.Image | None:
    try:
        img = Image.open(io.BytesIO(data))
        img.load()
        return img
    except (
        OSError,
        ValueError,
        SyntaxError,
        Image.DecompressionBombError,
    ):  # corrupt/unsupported/oversized image
        return None


def average_hash(img: Image.Image) -> int:
    small = np.asarray(img.convert("L").resize((8, 8)), dtype=np.float32)
    bits = (small > small.mean()).flatten()
    return int("".join("1" if b else "0" for b in bits), 2)


def filter_figures(blocks: list[Block], page_areas: dict[int, float], text_pages: set[int]) -> list[Block]:
    """Removes decorative figures (tiny, extreme aspect, repeated logos, page backgrounds)."""
    hashes: dict[int, int] = {}
    pages_per_hash: Counter[int] = Counter()
    for i, b in enumerate(blocks):
        if b.kind == "figure" and b.image:
            img = decode(b.image.data)
            if img is not None:
                h = average_hash(img)
                hashes[i] = h
                pages_per_hash[h] += 1
    repeated = {h for h, n in pages_per_hash.items() if n >= REPEAT_PAGES}

    kept: list[Block] = []
    figures = 0
    for i, b in enumerate(blocks):
        if b.kind != "figure":
            kept.append(b)
            continue
        if b.image is None or figures >= MAX_FIGURES or hashes.get(i) in repeated:
            continue
        if min(b.image.width, b.image.height) < MIN_SIDE_PX:
            continue
        ratio = max(b.image.width, b.image.height) / max(1, min(b.image.width, b.image.height))
        if ratio > MAX_ASPECT:
            continue
        if b.bbox is not None:
            area = (b.bbox[2] - b.bbox[0]) * (b.bbox[3] - b.bbox[1])
            page_area = page_areas.get(b.page, 1.0)
            if area < MIN_PAGE_AREA_FRACTION * page_area:
                continue
            if area > BACKGROUND_AREA_FRACTION * page_area and b.page in text_pages:
                continue  # background image behind a text page
        kept.append(b)
        figures += 1
    return kept


def detect_scanned_figures(page: pymupdf.Page, blocks: list[Block], dpi: int = 150) -> list[Block]:
    """Gap-with-ink heuristic for OCR'd pages, where the layout model finds text but not pictures.

    Renders the page in greyscale, masks every detected text block, and returns tall bands that still
    contain substantial ink (diagrams, photos) as figure blocks.
    """
    # Analyse at 72 dpi so that 1 px == 1 pt (block bboxes are in points).
    pix = page.get_pixmap(matrix=pymupdf.Matrix(1, 1), colorspace=pymupdf.csGRAY, alpha=False)
    gray = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width)
    ink = gray < 160
    for b in blocks:
        if b.bbox:
            x0, y0, x1, y1 = (int(v) for v in b.bbox)
            ink[max(0, y0 - 2) : y1 + 2, max(0, x0 - 2) : x1 + 2] = False
    margin = int(0.05 * pix.width)
    ink[:, :margin] = False
    ink[:, pix.width - margin :] = False

    rows = ink.sum(axis=1) > 0.02 * pix.width
    bands: list[tuple[int, int]] = []
    start, gap = None, 0
    for y, has_ink in enumerate(rows):
        if has_ink:
            start = y if start is None else start
            gap = 0
        elif start is not None:
            gap += 1
            if gap > 8:
                bands.append((start, y - gap))
                start, gap = None, 0
    if start is not None:
        bands.append((start, len(rows) - 1))

    found: list[Block] = []
    for y0, y1 in bands:
        if y1 - y0 < 60:
            continue
        cols = np.where(ink[y0 : y1 + 1].sum(axis=0) > 0)[0]
        if cols.size == 0:
            continue
        bbox: BBox = (float(cols[0]), float(y0), float(cols[-1]), float(y1))
        if (bbox[2] - bbox[0]) * (bbox[3] - bbox[1]) < MIN_PAGE_AREA_FRACTION * pix.width * pix.height:
            continue
        clip = pymupdf.Rect(*bbox) + (-4, -4, 4, 4)
        render = page.get_pixmap(clip=clip, dpi=dpi, alpha=False)
        img = Image.frombytes("RGB", (render.width, render.height), render.samples)
        found.append(Block(page=page.number + 1, kind="figure", bbox=bbox, image=encode(img)))
    return found


def insert_by_position(blocks: list[Block], extra: list[Block]) -> list[Block]:
    """Inserts figure blocks into a page's reading order by vertical position."""
    out = list(blocks)
    for fig in extra:
        y = fig.bbox[1] if fig.bbox else 0
        index = len(out)
        for i, b in enumerate(out):
            if b.bbox and b.bbox[1] > y:
                index = i
                break
        out.insert(index, fig)
    return out
