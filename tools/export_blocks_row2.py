# -*- coding: utf-8 -*-
"""Row 2 of the v2 sheet: blossom_grass, glow_berries, liana_barrier,
liana_weak_point, infected_water still/flow. All cube_all or direct fluid
textures - safe 1:1 swaps."""
from PIL import Image

SRC = r"C:\Users\PC\AppData\Local\Temp\lotus_art_work\art_v2_raw.png"
DEST = "src/main/resources/assets/lotusblight/textures/block"

im = Image.open(SRC).convert("RGBA")

# (box, name, text_cutoff_y) - text_cutoff_y trims the label out before bbox-trim.
entries = [
    ((162, 202, 280, 350), "blossom_grass", 335),
    ((285, 202, 395, 350), "glow_berries", 335),
    ((409, 202, 532, 350), "liana_barrier", 335),
    ((540, 202, 659, 351), "liana_weak_point", 335),
    ((670, 202, 791, 367), "infected_water_still", 335),
    ((798, 204, 926, 367), "infected_water_flow", 335),
]

TARGET = 32
for (x0, y0, x1, y1), name, ycut in entries:
    crop = im.crop((x0, y0, x1, min(y1, ycut)))
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
