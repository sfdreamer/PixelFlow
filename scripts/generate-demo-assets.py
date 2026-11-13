"""Generate PixelFlow demo assets (covers, preset results, sample uploads).

All artwork is drawn programmatically with Pillow: no third-party imagery, no network
access. Output is deterministic. License: CC0-1.0 (self-authored).
"""

from __future__ import annotations

import os
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEMO_DIR = os.path.join(ROOT, "backend", "src", "main", "resources", "demo")
SAMPLE_DIR = os.path.join(ROOT, "scripts", "sample-assets")

FONT_CANDIDATES = [
    r"C:\Windows\Fonts\msyh.ttc",
    r"C:\Windows\Fonts\segoeui.ttf",
    r"C:\Windows\Fonts\arial.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
]


def font(size: int) -> ImageFont.FreeTypeFont:
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return ImageFont.load_default()


def gradient(size, top, bottom) -> Image.Image:
    w, h = size
    base = Image.new("RGB", (1, h))
    for y in range(h):
        t = y / max(h - 1, 1)
        base.putpixel(
            (0, y),
            (
                int(top[0] + (bottom[0] - top[0]) * t),
                int(top[1] + (bottom[1] - top[1]) * t),
                int(top[2] + (bottom[2] - top[2]) * t),
            ),
        )
    return base.resize((w, h))


def soft_circles(size, seed, palette, count=7) -> Image.Image:
    rnd = random.Random(seed)
    layer = Image.new("RGB", size, (0, 0, 0))
    draw = ImageDraw.Draw(layer)
    w, h = size
    for _ in range(count):
        r = rnd.randint(int(w * 0.12), int(w * 0.34))
        cx = rnd.randint(0, w)
        cy = rnd.randint(0, h)
        color = palette[rnd.randrange(len(palette))]
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color)
    return layer.filter(ImageFilter.GaussianBlur(radius=int(w * 0.06)))


def blend(base: Image.Image, overlay: Image.Image, alpha: float) -> Image.Image:
    return Image.blend(base, overlay, alpha)


def label(draw: ImageDraw.ImageDraw, xy, text, size, fill, anchor="la"):
    draw.text(xy, text, font=font(size), fill=fill, anchor=anchor)


def vignette(img: Image.Image, strength: int = 110) -> Image.Image:
    w, h = img.size
    mask = Image.new("L", (w, h), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse([-w * 0.15, -h * 0.15, w * 1.15, h * 1.15], fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(radius=int(w * 0.08)))
    dark = Image.new("RGB", (w, h), (0, 0, 0))
    return Image.composite(img, Image.blend(img, dark, strength / 255), mask)


SIZE = (1200, 900)


def pet_soft(cover: bool) -> Image.Image:
    img = gradient(SIZE, (247, 239, 228), (216, 198, 178))
    img = blend(img, soft_circles(SIZE, 11, [(255, 236, 214), (232, 205, 180), (250, 246, 240)]), 0.55)
    draw = ImageDraw.Draw(img)
    if cover:
        draw.ellipse([360, 200, 840, 680], fill=(252, 248, 243))
        draw.ellipse([430, 300, 510, 400], fill=(70, 62, 56))
        draw.ellipse([690, 300, 770, 400], fill=(70, 62, 56))
        draw.ellipse([520, 470, 680, 560], fill=(120, 104, 92))
        label(draw, (600, 740), "柔光宠物肖像", 58, (58, 48, 42), "mm")
        label(draw, (600, 800), "SOFT PET PORTRAIT", 26, (120, 104, 92), "mm")
    else:
        img = vignette(img)
        draw = ImageDraw.Draw(img)
        draw.rectangle([70, 70, 1130, 830], outline=(255, 255, 255), width=3)
        label(draw, (600, 430), "PIXELFLOW", 96, (58, 48, 42), "mm")
        label(draw, (600, 520), "STUDIO PORTRAIT", 34, (110, 96, 86), "mm")
        label(draw, (600, 780), "模拟生成结果 · SIMULATED RESULT", 24, (110, 96, 86), "mm")
    return img


def pet_magazine(cover: bool) -> Image.Image:
    img = gradient(SIZE, (32, 28, 30), (72, 24, 30))
    draw = ImageDraw.Draw(img)
    draw.rectangle([0, 0, 1200, 900], fill=None)
    draw.polygon([(0, 620), (1200, 470), (1200, 900), (0, 900)], fill=(178, 38, 48))
    draw.ellipse([420, 170, 800, 620], fill=(242, 236, 230))
    draw.ellipse([500, 300, 570, 390], fill=(40, 34, 32))
    draw.ellipse([650, 300, 720, 390], fill=(40, 34, 32))
    label(draw, (60, 80), "PIXEL", 108, (255, 255, 255))
    label(draw, (60, 200), "FLOW", 108, (255, 216, 120))
    label(draw, (60, 330), "ISSUE 04 / 2026", 26, (240, 230, 226))
    if cover:
        label(draw, (600, 720), "宠物杂志封面", 60, (255, 255, 255), "mm")
    else:
        label(draw, (600, 700), "COVER STORY", 66, (255, 255, 255), "mm")
        label(draw, (600, 790), "模拟生成结果 · SIMULATED RESULT", 24, (240, 220, 220), "mm")
    return img


def travel_island(cover: bool) -> Image.Image:
    img = gradient(SIZE, (146, 214, 226), (250, 238, 210))
    draw = ImageDraw.Draw(img)
    draw.ellipse([840, 110, 1010, 280], fill=(255, 226, 150))
    draw.polygon([(0, 620), (1200, 560), (1200, 900), (0, 900)], fill=(38, 150, 158))
    draw.polygon([(0, 690), (1200, 640), (1200, 900), (0, 900)], fill=(24, 118, 132))
    draw.polygon([(0, 760), (1200, 720), (1200, 900), (0, 900)], fill=(16, 92, 108))
    for x in range(0, 1200, 90):
        draw.arc([x, 700, x + 160, 780], 200, 340, fill=(220, 246, 248), width=6)
    label(draw, (70, 90), "海岛旅行明信片", 64, (18, 62, 74))
    label(draw, (70, 180), "ISLAND POSTCARD", 30, (28, 92, 104))
    if not cover:
        draw.rectangle([70, 70, 1130, 830], outline=(255, 255, 255), width=3)
        label(draw, (600, 820), "模拟生成结果 · SIMULATED RESULT", 24, (18, 62, 74), "mm")
    return img


def travel_mountain(cover: bool) -> Image.Image:
    img = gradient(SIZE, (58, 62, 116), (222, 178, 156))
    draw = ImageDraw.Draw(img)
    draw.ellipse([870, 90, 1030, 250], fill=(255, 236, 200))
    draw.polygon([(0, 640), (280, 330), (520, 640)], fill=(44, 48, 92))
    draw.polygon([(330, 640), (660, 280), (980, 640)], fill=(32, 38, 78))
    draw.polygon([(760, 640), (1030, 380), (1200, 640)], fill=(26, 30, 64))
    draw.polygon([(0, 640), (1200, 640), (1200, 900), (0, 900)], fill=(22, 26, 48))
    label(draw, (70, 90), "山野旅行明信片", 64, (246, 240, 232))
    label(draw, (70, 180), "MOUNTAIN POSTCARD", 30, (214, 206, 224))
    if not cover:
        draw.rectangle([70, 70, 1130, 830], outline=(255, 255, 255), width=3)
        label(draw, (600, 820), "模拟生成结果 · SIMULATED RESULT", 24, (230, 224, 236), "mm")
    return img


def sample_pet() -> Image.Image:
    img = gradient((1200, 900), (250, 246, 238), (224, 210, 196))
    draw = ImageDraw.Draw(img)
    draw.ellipse([330, 140, 870, 760], fill=(255, 252, 246))
    draw.ellipse([420, 300, 500, 400], fill=(66, 58, 52))
    draw.ellipse([700, 300, 780, 400], fill=(66, 58, 52))
    draw.ellipse([540, 470, 660, 560], fill=(140, 118, 104))
    draw.arc([500, 520, 700, 660], 20, 160, fill=(90, 76, 68), width=8)
    label(draw, (600, 820), "示例素材 · SAMPLE UPLOAD", 26, (110, 96, 86), "mm")
    return img


def sample_travel(name: str) -> Image.Image:
    img = gradient((1200, 900), (120, 190, 210), (246, 236, 216))
    draw = ImageDraw.Draw(img)
    rnd = random.Random(name)
    for _ in range(9):
        x = rnd.randint(0, 1200)
        y = rnd.randint(0, 700)
        r = rnd.randint(30, 120)
        draw.ellipse([x - r, y - r, x + r, y + r], outline=(255, 255, 255), width=6)
    draw.polygon([(0, 700), (1200, 660), (1200, 900), (0, 900)], fill=(28, 130, 142))
    label(draw, (600, 800), f"示例素材 · {name}", 26, (250, 250, 246), "mm")
    return img


def main() -> None:
    os.makedirs(DEMO_DIR, exist_ok=True)
    os.makedirs(SAMPLE_DIR, exist_ok=True)
    outputs = {
        "cover-pet-soft.png": pet_soft(True),
        "result-pet-soft.png": pet_soft(False),
        "cover-pet-magazine.png": pet_magazine(True),
        "result-pet-magazine.png": pet_magazine(False),
        "cover-travel-island.png": travel_island(True),
        "result-travel-island.png": travel_island(False),
        "cover-travel-mountain.png": travel_mountain(True),
        "result-travel-mountain.png": travel_mountain(False),
    }
    for name, image in outputs.items():
        image.save(os.path.join(DEMO_DIR, name), "PNG", optimize=True)

    sample_pet().save(os.path.join(SAMPLE_DIR, "pet-sample.png"), "PNG", optimize=True)
    sample_travel("海岛 A").save(os.path.join(SAMPLE_DIR, "travel-island-a.png"), "PNG", optimize=True)
    sample_travel("海岛 B").save(os.path.join(SAMPLE_DIR, "travel-island-b.png"), "PNG", optimize=True)
    print("generated", len(outputs) + 3, "images")


if __name__ == "__main__":
    main()
