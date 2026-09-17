# -*- coding: utf-8 -*-
from PIL import Image

SRC = r"C:\Users\PC\AppData\Local\Temp\lotus_art_work\art_v2_raw.png"
DEST = "src/main/resources/assets/lotusblight/textures/block"

im = Image.open(SRC).convert("RGBA")

entries = [
    ((479, 44, 574, 152), "infected_soil"),
    ((579, 43, 677, 152), "lotus_dirt"),
    ((684, 43, 782, 152), "lotus_sand"),
    ((787, 43, 887, 152), "lotus_gravel"),
]

TARGET = 32
for (x0, y0, x1, y1), name in entries:
    crop = im.crop((x0, y0, x1, y1))
    bbox = crop.getchannel("A").point(lambda a: 255 if a > 40 else 0).getbbox()
    if bbox:
        crop = crop.crop(bbox)
    w, h = crop.size
    scale = TARGET / max(w, h)
    nw, nh = max(1, round(w * scale)), max(1, round(h * scale))
    resized = crop.resize((nw, nh), Image.LANCZOS)
    canvas = Image.new("RGBA", (TARGET, TARGET), (0, 0, 0, 0))
    canvas.paste(resized, ((TARGET - nw) // 2, (TARGET - nh) // 2), resized)
    canvas.save(f"{DEST}/{name}.png")
    print("wrote", name, canvas.size)
