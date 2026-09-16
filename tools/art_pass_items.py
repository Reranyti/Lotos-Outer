# -*- coding: utf-8 -*-
"""Item icon pass, reusing the palette/dither toolkit from full_art_pass.py."""
import importlib.util, sys
from pathlib import Path
from PIL import Image, ImageDraw
import math, random

spec = importlib.util.spec_from_file_location("fap", r"D:\Claude_scratch\full_art_pass.py")
fap = importlib.util.module_from_spec(spec)
sys.modules["fap"] = fap
spec.loader.exec_module(fap)

PAL, S, ITEM, ARMOR = fap.PAL, fap.S, fap.ITEM, fap.ARMOR
new, dither_edges, jitter_points, px = fap.new, fap.dither_edges, fap.jitter_points, fap.px

random.seed(555)

def icon():
    return new((S, S))

def outline_fill(im, points, ramp4, jitter=1.0):
    pts = jitter_points(points, jitter)
    ImageDraw.Draw(im).polygon(pts, fill=ramp4[1])
    mask = im.split()[3]
    data = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            if mask.getpixel((x, y)) == 0:
                continue
            r = random.random()
            if r < 0.14:
                data[x, y] = ramp4[0]
            elif r < 0.26:
                data[x, y] = ramp4[2]
            elif r < 0.3:
                data[x, y] = ramp4[3]
    dither_edges(im, 1)

def save(im, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)

def tool(head_shape, head_ramp, name, length=0.72):
    im = icon()
    w = h = S
    # handle
    handle = [(w * 0.12, h * 0.92), (w * 0.2, h * 0.84), (w * (0.2 + length), h * (0.84 - length)),
              (w * (0.12 + length), h * (0.76 - length))]
    outline_fill(im, handle, PAL["lotus_wood"], 0.6)
    hx, hy = w * (0.16 + length), h * (0.8 - length)
    if head_shape == "pick":
        pts = [(hx - 2, hy + 9), (hx + 3, hy - 6), (hx + 10, hy - 9), (hx + 13, hy - 2),
               (hx + 4, hy + 4), (hx - 2, hy + 9)]
    elif head_shape == "axe":
        pts = [(hx - 1, hy - 2), (hx + 9, hy - 10), (hx + 14, hy - 6), (hx + 10, hy + 2),
               (hx + 2, hy + 6)]
    elif head_shape == "sword":
        pts = [(hx + 2, hy - 14), (hx + 6, hy - 14), (hx + 5, hy + 2), (hx + 3, hy + 2)]
    elif head_shape == "hoe":
        pts = [(hx - 2, hy - 8), (hx + 10, hy - 8), (hx + 10, hy - 4), (hx - 2, hy - 4)]
    outline_fill(im, pts, head_ramp, 0.6)
    save(im, ITEM / f"{name}.png")

tool("pick", PAL["alloy"], "lotus_pickaxe", 0.55)
tool("axe", PAL["alloy"], "lotus_axe", 0.5)
tool("sword", PAL["alloy"], "lotus_sword", 0.62)
tool("hoe", PAL["alloy"], "lotus_hoe", 0.55)

def grafting_rod():
    im = icon()
    w = h = S
    outline_fill(im, [(w * 0.42, h * 0.95), (w * 0.5, h * 0.9), (w * 0.56, h * 0.3), (w * 0.48, h * 0.28)],
                 PAL["lotus_wood"], 0.6)
    petal = fap.flower_petal((14, 14), PAL["lotus_petal"])
    im.paste(petal, (int(w * 0.36), 0), petal)
    save(im, ITEM / "lotus_grafting_rod.png")

grafting_rod()

def seer_lens():
    im = icon()
    w = h = S
    cx, cy = w * 0.42, h * 0.42
    ram = PAL["blessing_ice"]
    ImageDraw.Draw(im).ellipse([cx - 9, cy - 9, cx + 9, cy + 9], outline=ram[0], width=2)
    for r, tone in ((7, ram[1]), (4, ram[2])):
        ImageDraw.Draw(im).ellipse([cx - r, cy - r, cx + r, cy + r], fill=tone)
    px(im, int(cx - 2), int(cy - 2), ram[3])
    outline_fill(im, [(cx + 6, cy + 6), (w * 0.92, h * 0.92), (w * 0.85, h * 0.98), (cx + 3, cy + 9)],
                 PAL["lotus_wood"], 0.5)
    dither_edges(im, 1)
    save(im, ITEM / "lotus_seer_lens.png")

seer_lens()

def armor_icon(kind):
    im = icon()
    w = h = S
    ram = PAL["lotus_petal"]
    shapes = {
        "helmet": [(w*0.28,h*0.22),(w*0.72,h*0.22),(w*0.78,h*0.42),(w*0.72,h*0.5),(w*0.28,h*0.5),(w*0.22,h*0.42)],
        "chestplate": [(w*0.24,h*0.16),(w*0.76,h*0.16),(w*0.8,h*0.62),(w*0.6,h*0.7),(w*0.4,h*0.7),(w*0.2,h*0.62)],
        "leggings": [(w*0.24,h*0.14),(w*0.76,h*0.14),(w*0.76,h*0.4),(w*0.58,h*0.42),(w*0.56,h*0.86),(w*0.44,h*0.86),(w*0.42,h*0.42),(w*0.24,h*0.4)],
        "boots": [(w*0.22,h*0.14),(w*0.42,h*0.14),(w*0.42,h*0.56),(w*0.6,h*0.6),(w*0.62,h*0.82),(w*0.2,h*0.82)],
    }
    outline_fill(im, shapes[kind], ram, 0.9)
    save(im, ITEM / f"lotus_{kind}.png")

for k in ("helmet", "chestplate", "leggings", "boots"):
    armor_icon(k)

def armor_layer(name, ram):
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    ImageDraw.Draw(im).rectangle([0, 0, 63, 31], fill=ram[1])
    data = im.load()
    for y in range(32):
        for x in range(64):
            r = random.random()
            if r < 0.2:
                data[x, y] = ram[0]
            elif r < 0.35:
                data[x, y] = ram[2]
    save(im, ARMOR / name)

armor_layer("lotus_layer_1.png", PAL["lotus_petal"])
armor_layer("lotus_layer_2.png", PAL["lotus_mineral"])

def cleansing_powder():
    im = icon()
    w = h = S
    outline_fill(im, [(w*0.22,h*0.9),(w*0.3,h*0.55),(w*0.7,h*0.55),(w*0.78,h*0.9)], PAL["blessing_soil"], 0.8)
    # spilled powder on top
    for _ in range(30):
        x = w*0.5 + random.uniform(-14, 14)
        y = h*0.5 + random.uniform(-10, 4)
        if 0 <= x < w and 0 <= y < h:
            tone = PAL["blessing_ice"][random.choice([1, 2, 3])]
            px(im, int(x), int(y), tone)
    save(im, ITEM / "cleansing_powder.png")

cleansing_powder()

def lotus_map_icon():
    im = icon()
    w = h = S
    outline_fill(im, [(w*0.16,h*0.14),(w*0.84,h*0.14),(w*0.8,h*0.9),(w*0.2,h*0.86)], PAL["lotus_sand"], 0.8)
    for _ in range(6):
        x0 = w*0.3 + random.uniform(-2, 2)
        y0 = h*0.3 + random.uniform(0, 30)
        ImageDraw.Draw(im).point((x0, y0), fill=PAL["lotus_petal"][2])
    ImageDraw.Draw(im).line([(w*0.3,h*0.3),(w*0.5,h*0.5),(w*0.4,h*0.7)], fill=PAL["lotus_terra"][0], width=1)
    dither_edges(im, 1)
    save(im, ITEM / "lotus_map.png")

lotus_map_icon()

def lotus_wiki_icon():
    im = icon()
    w = h = S
    outline_fill(im, [(w*0.2,h*0.12),(w*0.78,h*0.12),(w*0.78,h*0.9),(w*0.2,h*0.9)], PAL["lotus_petal"], 0.7)
    ImageDraw.Draw(im).rectangle([w*0.28,h*0.2,w*0.7,h*0.82], fill=PAL["lotus_sand"][2])
    for ly in (0.3, 0.4, 0.5, 0.6, 0.7):
        ImageDraw.Draw(im).line([(w*0.33,h*ly),(w*0.65,h*ly)], fill=PAL["lotus_sand"][0])
    dither_edges(im, 1)
    save(im, ITEM / "lotus_wiki.png")

lotus_wiki_icon()

def glowing_berry_icon():
    im = icon()
    w = h = S
    for dx, dy in ((-4, -2), (4, -2), (0, 4)):
        cx, cy = w*0.5+dx, h*0.5+dy
        ram = fap.ramp(["3a2f10", "b5851f", "ffce4d", "fff2b0"])
        for r, tone in ((4, ram[2]), (2, ram[3])):
            ImageDraw.Draw(im).ellipse([cx-r,cy-r,cx+r,cy+r], fill=tone)
    ImageDraw.Draw(im).line([(w*0.5,h*0.3),(w*0.5,h*0.15)], fill=PAL["lotus_wood"][1], width=1)
    dither_edges(im, 1)
    save(im, ITEM / "glowing_berry.png")

glowing_berry_icon()

print("items done")
