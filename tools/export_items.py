# -*- coding: utf-8 -*-
"""Crop the 12 item-row blobs from the v2 sheet, trim tightly to real content
via alpha bbox, resize into a 32x32 canvas (nearest-neighbor to keep pixel
edges crisp), and write directly into the mod's item texture folder."""
from PIL import Image

SRC = "art_v2_raw.png"
DEST = "src/main/resources/assets/lotusblight/textures/item"

im = Image.open(SRC).convert("RGBA")

boxes = [
    (28, 449, 83, 512), (123, 430, 225, 530), (249, 419, 356, 530), (388, 424, 483, 536),
    (514, 435, 615, 524), (657, 424, 750, 522), (775, 424, 863, 527), (882, 418, 992, 532),
    (1010, 423, 1102, 526), (1112, 409, 1238, 560), (1294, 431, 1383, 526), (1406, 425, 1507, 530),
]
names = [
    "lotus_seed", "lotus_map", "lotus_wiki", "cleansing_powder", "lotus_alloy",
    "lotus_pickaxe", "lotus_axe", "lotus_sword", "lotus_hoe", "lotus_grafting_rod",
    "lotus_seer_lens", "glowing_berry",
]

PAD = 5
TARGET = 32
for name, (x0, y0, x1, y1) in zip(names, boxes):
    crop = im.crop((max(0, x0 - PAD), max(0, y0 - PAD), min(im.width, x1 + PAD), min(im.height, y1 + PAD)))
    bbox = crop.getchannel("A").point(lambda a: 255 if a > 40 else 0).getbbox()
    if bbox:
        crop = crop.crop(bbox)
    # Fit into a TARGET x TARGET canvas, preserving aspect ratio, centered.
    w, h = crop.size
    scale = (TARGET - 2) / max(w, h)
    new_w, new_h = max(1, round(w * scale)), max(1, round(h * scale))
    resized = crop.resize((new_w, new_h), Image.LANCZOS)
    canvas = Image.new("RGBA", (TARGET, TARGET), (0, 0, 0, 0))
    canvas.paste(resized, ((TARGET - new_w) // 2, (TARGET - new_h) // 2), resized)
    canvas.save(f"{DEST}/{name}.png")
    print("wrote", name, "->", canvas.size)
