from PIL import Image, ImageDraw

from sapio_inference.parsing.figures import encode, filter_figures
from sapio_inference.parsing.models import Block

PAGE = (0.0, 0.0, 1000.0, 1000.0)
AREAS = {p: 1_000_000.0 for p in range(1, 10)}


def image(seed: int, size=(400, 300)):
    img = Image.new("RGB", size, "white")
    draw = ImageDraw.Draw(img)
    for k in range(6):  # distinct structure per seed so perceptual hashes differ
        x = (seed * 37 + k * 61) % (size[0] - 40)
        draw.rectangle([x, k * 40, x + 40, k * 40 + 30], fill="black")
    return encode(img)


def figure(page, img, box):
    return Block(page=page, kind="figure", bbox=box, image=img)


def test_small_repeated_image_is_a_logo_but_large_reused_figure_is_kept_once_per_section():
    logo, diagram = image(1, (200, 150)), image(2)
    small, large = (10.0, 10.0, 210.0, 160.0), (100.0, 300.0, 900.0, 900.0)  # 3 % and 48 % of the page
    blocks = []
    for page in range(1, 6):
        blocks.append(figure(page, logo, small))
        blocks.append(Block(page=page, kind="heading", text=f"Slide {page}"))
        blocks.append(figure(page, diagram, large))
        if page == 3:
            blocks.append(figure(page, diagram, large))  # shown twice in one section
    kept = [b for b in filter_figures(blocks, AREAS, set()) if b.kind == "figure"]
    assert [b.page for b in kept] == [1, 2, 3, 4, 5]
    assert all(b.image is diagram for b in kept)
