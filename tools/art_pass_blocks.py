# -*- coding: utf-8 -*-
"""
Full art pass for Lotus Blight. Still procedural (Pillow, no hand-painted art),
but replaces the earlier flat-noise fills with deliberate pixel-art shapes:
consistent top-left light source, 4-tone shading ramps per material, and
actual silhouettes (petals, grain, veins, weave, ore clusters) instead of
random speckle. One shared palette/shape toolkit, applied to every texture.
"""
import math
import random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(r"D:\The LOTOS\src\main\resources\assets\lotusblight")
BLOCK = ROOT / "textures" / "block"
ITEM = ROOT / "textures" / "item"
ARMOR = ROOT / "textures" / "models" / "armor"
S = 32  # base texture size

random.seed(20260916)

# ---------------------------------------------------------------- palettes
def ramp(hexes):
    return [tuple(int(h[i:i+2], 16) for i in (0, 2, 4)) + (255,) for h in hexes]

PAL = {
    "lotus_wood":   ramp(["0f3a1e", "1c5c30", "2f8a4a", "78d69a"]),
    "lotus_leaf":   ramp(["123a20", "1f5c33", "3a9459", "8fe0a8"]),
    "lotus_petal":  ramp(["7a1f4a", "d6336b", "ef5da8", "ffc2e0"]),
    "lotus_stamen": ramp(["a86a00", "e0a020", "ffce4d", "fff2b0"]),
    "lotus_mineral":ramp(["241c3a", "463a66", "7462a3", "b7abe0"]),
    "lotus_sand":   ramp(["7a4a4f", "b5717a", "d9a0a5", "f2d4d6"]),
    "lotus_terra":  ramp(["5c1f33", "8f2f4f", "b5566b", "e0a0b0"]),
    "lotus_gravel": ramp(["2c3b30", "445c48", "6c8a6e", "a9c4a8"]),
    "lotus_dirt":   ramp(["2a1f14", "4a3520", "6e5230", "a3854f"]),
    "lotus_soil":   ramp(["1b3324", "2c5a3c", "4a8a5e", "8fd1a4"]),
    "lotus_heart":  ramp(["4a0f33", "8f1f5c", "e04a9a", "ffb3e0"]),
    "liquid":       ramp(["0d2e28", "1b5c4d", "2f9478", "7fe0c0"]),
    "blessing_ice": ramp(["3a4f52", "6f9aa3", "b0d9df", "eef8f9"]),
    "blessing_soil":ramp(["2f3538", "51595c", "7d8689", "bcc4c6"]),
    "blessing_wood":ramp(["2a2f26", "4a5240", "747d63", "b0b89c"]),
    "blessing_leaf":ramp(["1a3540", "2e5866", "4f8a99", "9fd3de"]),
    "blessing_red": ramp(["4a1414", "7a2222", "b23a3a", "e07070"]),
    "alloy":        ramp(["1f4a42", "35786a", "5fae9c", "b6e8d8"]),
    "vine":         ramp(["102a12", "1f4a22", "3a7a3f", "7fc287"]),
    "weakpoint":    ramp(["5c1030", "9c1f52", "e0448c", "ffb3d9"]),
    "ash_grey":     ramp(["222222", "3d3d3d", "5c5c5c", "8a8a8a"]),
}

def compress_contrast(ramp4, factor=0.55):
    """Vanilla textures (dirt.png etc.) have much narrower light/dark swings
    than a typical 'designed' palette — shrink each ramp toward its own mean
    so shading reads as texture noise, not a poster/logo gradient."""
    mean = [sum(c[i] for c in ramp4) / len(ramp4) for i in range(3)]
    out = []
    for c in ramp4:
        out.append(tuple(int(mean[i] + (c[i] - mean[i]) * factor) for i in range(3)) + (255,))
    return out

for _k in list(PAL.keys()):
    PAL[_k] = compress_contrast(PAL[_k], 0.5)

def new(size=(S, S)):
    return Image.new("RGBA", size, (0, 0, 0, 0))

def fill_base(im, color):
    ImageDraw.Draw(im).rectangle([0, 0, im.width - 1, im.height - 1], fill=color)

def px(im, x, y, c):
    if 0 <= x < im.width and 0 <= y < im.height:
        im.putpixel((x, y), c)

BAYER2 = [[0, 2], [3, 1]]  # ordered-dither threshold matrix, keeps banding hard-edged not smooth

def top_left_light(im, ramp4, jitter=0.12):
    """Fake top-left lighting WITHOUT a smooth gradient: nudge each pixel one
    shade brighter/darker only where an ordered-dither threshold says so, so
    the result reads as rough hand-shaded pixel banding instead of a vector
    drop-shadow/glow (that smooth-gradient look is what makes procedural art
    read as 'cartoon' instead of a game texture)."""
    w, h = im.size
    src = im.copy()
    for y in range(h):
        for x in range(w):
            r, g, b, a = src.getpixel((x, y))
            if a == 0:
                continue
            bias = ((w - x) + (h - y)) / (w + h) - 0.5
            thresh = BAYER2[y % 2][x % 2] / 4.0 - 0.5
            if bias + thresh * 0.3 > 0.22:
                r, g, b = [min(255, int(c * 1.15)) for c in (r, g, b)]
            elif bias + thresh * 0.3 < -0.22:
                r, g, b = [int(c * 0.82) for c in (r, g, b)]
            im.putpixel((x, y), (r, g, b, a))

def dither_edges(im, passes=1):
    """Break up perfectly smooth vector-drawn silhouettes: for every opaque
    pixel touching a transparent/different-colour neighbour, randomly eat
    into or spit out a pixel so edges look hand-placed, not anti-alias-free
    vector output."""
    w, h = im.size
    for _ in range(passes):
        src = im.copy()
        edits = []
        for y in range(h):
            for x in range(w):
                c = src.getpixel((x, y))
                neigh = [src.getpixel((nx, ny)) for nx, ny in
                         ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)) if 0 <= nx < w and 0 <= ny < h]
                if not neigh:
                    continue
                if any(n != c for n in neigh) and random.random() < 0.22:
                    edits.append(((x, y), random.choice(neigh)))
        for (x, y), c in edits:
            im.putpixel((x, y), c)

def jitter_points(points, amt=1.0):
    return [(x + random.uniform(-amt, amt), y + random.uniform(-amt, amt)) for x, y in points]

def speckle_clusters(im, ramp4, count, radius_range=(1, 3), tones=(0, 1, 3)):
    w, h = im.size
    for _ in range(count):
        cx, cy = random.randint(0, w - 1), random.randint(0, h - 1)
        r = random.randint(*radius_range)
        c = ramp4[random.choice(tones)]
        for dy in range(-r, r + 1):
            for dx in range(-r, r + 1):
                if dx * dx + dy * dy <= r * r and random.random() < 0.7:
                    px(im, cx + dx, cy + dy, c)

def vertical_grain(im, ramp4, plank_count=4, knot=True):
    w, h = im.size
    fill_base(im, ramp4[1])
    plank_w = w / plank_count
    for p in range(plank_count):
        x0 = int(p * plank_w)
        x1 = int((p + 1) * plank_w)
        shade = ramp4[1] if p % 2 == 0 else ramp4[2]
        ImageDraw.Draw(im).rectangle([x0, 0, x1 - 1, h - 1], fill=shade)
        for gx in range(x0 + 1, x1 - 1):
            if random.random() < 0.35:
                tone = ramp4[0] if random.random() < 0.5 else ramp4[3]
                for gy in range(h):
                    if random.random() < 0.6:
                        px(im, gx, gy, tone)
    if knot:
        for _ in range(plank_count // 2 + 1):
            cx, cy = random.randint(2, w - 3), random.randint(4, h - 5)
            for rr, tone in ((2, ramp4[0]), (1, ramp4[3])):
                ImageDraw.Draw(im).ellipse([cx - rr, cy - rr, cx + rr, cy + rr], outline=tone)

def cross_end_grain(im, ramp4):
    """Log end-grain rings, but irregular: each ring gets its own noisy
    radius offset per angle instead of a perfect circle, so it reads as
    grown wood rather than a target/logo."""
    w, h = im.size
    cx, cy = w / 2 + random.uniform(-1, 1), h / 2 + random.uniform(-1, 1)
    fill_base(im, ramp4[1])
    ring_count = 5
    ring_noise = [[random.uniform(-1.2, 1.2) for _ in range(24)] for _ in range(ring_count)]
    for y in range(h):
        for x in range(w):
            ang = math.atan2(y - cy, x - cx)
            bucket = int((ang + math.pi) / (2 * math.pi) * 24) % 24
            d = math.hypot(x - cx, y - cy)
            ring_idx = int(d) % ring_count
            noisy_d = d + ring_noise[ring_idx][bucket]
            band = int(noisy_d) % 4
            if band == 0:
                px(im, x, y, ramp4[2])
            elif band == 2:
                px(im, x, y, ramp4[0])
    ImageDraw.Draw(im).ellipse([cx - 2, cy - 2, cx + 2, cy + 2], fill=ramp4[3])
    dither_edges(im, passes=1)

def leafy_blotches(im, ramp4, count=10):
    fill_base(im, ramp4[1])
    w, h = im.size
    for _ in range(count):
        cx, cy = random.randint(2, w - 3), random.randint(2, h - 3)
        r = random.randint(2, 5)
        tone = ramp4[2] if random.random() < 0.6 else ramp4[0]
        ImageDraw.Draw(im).ellipse([cx - r, cy - r * 0.7, cx + r, cy + r * 0.7], fill=tone)
    for _ in range(6):
        x0, y0 = random.randint(0, w - 1), random.randint(0, h - 1)
        ImageDraw.Draw(im).line([x0, y0, x0 + random.randint(-3, 3), y0 + random.randint(-3, 3)], fill=ramp4[3])
    dither_edges(im, passes=1)

def granular(im, ramp4, grain=1):
    """Matches vanilla dirt.png: pure per-pixel jitter between adjacent
    ramp tones, no blobs/clusters — that per-pixel randomness (not blob
    noise) is what reads as a 'real' ground texture instead of digital static."""
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r = random.random()
            if r < 0.55:
                tone = ramp4[1]
            elif r < 0.8:
                tone = ramp4[2]
            elif r < 0.94:
                tone = ramp4[0]
            else:
                tone = ramp4[3]
            px(im, x, y, tone)

def ore_veins(im, base_ramp, vein_ramp, clusters=4):
    """Matches vanilla iron_ore.png: small soft circular spots on a plain
    granular stone base, not high-contrast connected veins."""
    granular(im, base_ramp)
    w, h = im.size
    for _ in range(clusters):
        cx, cy = random.randint(3, w - 4), random.randint(3, h - 4)
        r = random.randint(1, 2)
        for dy in range(-r - 1, r + 2):
            for dx in range(-r - 1, r + 2):
                d = math.hypot(dx, dy)
                if d <= r and random.random() < 0.85:
                    px(im, cx + dx, cy + dy, vein_ramp[2])
                elif d <= r + 1 and random.random() < 0.3:
                    px(im, cx + dx, cy + dy, vein_ramp[1])
        if random.random() < 0.6:
            px(im, cx, cy, vein_ramp[3])

def liquid_bands(im, ramp4, flowing=False, frames=1):
    w = im.width
    h_frame = S
    for f in range(frames):
        for y in range(h_frame):
            fy = f * h_frame + y
            wave = math.sin((y / h_frame) * math.pi * 3 + f * 1.4) * 2
            for x in range(w):
                v = (math.sin((x + wave * (3 if flowing else 0)) / 5.0 + y / 6.0) + 1) / 2
                tone = ramp4[1] if v < 0.55 else (ramp4[2] if v < 0.85 else ramp4[3])
                if y < 2:
                    tone = ramp4[3]
                px(im, x, fy, tone)

def flower_petal(size, ramp4):
    """A single lotus petal, drawn with a jittered outline and grainy fill
    (like pink_petals.png) instead of a clean flat-shaded polygon."""
    im = new(size)
    w, h = size
    outline = jitter_points(
        [(w * 0.5, h * 0.04), (w * 0.82, h * 0.4), (w * 0.62, h * 0.97),
         (w * 0.5, h * 0.8), (w * 0.38, h * 0.97), (w * 0.18, h * 0.4)], 1.1)
    ImageDraw.Draw(im).polygon(outline, fill=ramp4[1])
    mask = im.split()[3]
    px_data = im.load()
    for y in range(h):
        for x in range(w):
            if mask.getpixel((x, y)) == 0:
                continue
            r = random.random()
            if r < 0.15:
                tone = ramp4[0]
            elif r < 0.3:
                tone = ramp4[2]
            elif r < 0.36:
                tone = ramp4[3]
            else:
                tone = ramp4[1]
            px_data[x, y] = tone
    dither_edges(im, passes=1)
    return im

def flower_center(size, frame):
    im = new(size)
    w, h = size
    ram = PAL["lotus_stamen"]
    ImageDraw.Draw(im).ellipse([2, 2, w - 3, h - 3], fill=ram[1])
    random.seed(7 + frame)
    mask = im.split()[3]
    px_data = im.load()
    for y in range(h):
        for x in range(w):
            if mask.getpixel((x, y)) == 0:
                continue
            r = random.random()
            if r < 0.2:
                px_data[x, y] = ram[0]
            elif r < 0.3:
                px_data[x, y] = ram[2] if frame == 0 else ram[3]
    dither_edges(im, passes=1)
    return im

def woven_vine(im, ramp4, weak=False):
    w, h = im.size
    fill_base(im, ramp4[0])
    for i in range(-h, w, 4):
        ImageDraw.Draw(im).line([i, 0, i + h, h], fill=ramp4[2], width=2)
        ImageDraw.Draw(im).line([i, h, i + h, 0], fill=ramp4[1], width=2)
    speckle_clusters(im, ramp4, 14, (1, 2), tones=(3,))
    dither_edges(im, passes=1)
    if weak:
        cx, cy = w / 2, h / 2
        for r, tone in ((6, ramp4[3]), (3, (255, 255, 255, 220))):
            ImageDraw.Draw(im).ellipse([cx - r, cy - r, cx + r, cy + r], outline=tone, width=1)

def root_strands(im, ramp4, underwater=False):
    w, h = im.size
    fill_base(im, (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for i in range(5):
        x = 3 + i * (w - 6) / 4
        pts = [(x, h)]
        for step in range(6):
            x += random.uniform(-2, 2)
            pts.append((x, h - step * h / 6))
        d.line(pts, fill=ramp4[1], width=2)
        d.line(pts, fill=ramp4[2], width=1)
    if underwater:
        for _ in range(8):
            px(im, random.randint(0, w - 1), random.randint(0, h - 1), (120, 200, 220, 120))

def heart_glow(im, ramp4):
    """Glowing core, but banded with per-pixel dither at each ring boundary
    (like a hand-shaded sprite) instead of a smooth radial gradient, and an
    irregular per-angle radius so it isn't a perfect circle."""
    w, h = im.size
    cx, cy = w / 2, h / 2
    angle_noise = [random.uniform(-1.5, 1.5) for _ in range(36)]
    for y in range(h):
        for x in range(w):
            ang = math.atan2(y - cy, x - cx)
            bucket = int((ang + math.pi) / (2 * math.pi) * 36) % 36
            d = math.hypot(x - cx, y - cy) + angle_noise[bucket] - (w / 2 - 3)
            if d > 2:
                tone = ramp4[0]
            elif d > -1:
                tone = ramp4[1]
            elif d > -5:
                tone = ramp4[2]
            else:
                tone = ramp4[3]
            px(im, x, y, tone)
    dither_edges(im, passes=1)

def mimic_face(im, ramp4):
    vertical_grain(im, ramp4, plank_count=5, knot=False)
    w, h = im.size
    d = ImageDraw.Draw(im)
    for ex in (w * 0.32, w * 0.68):
        d.ellipse([ex - 3, h * 0.35 - 3, ex + 3, h * 0.35 + 3], fill=(20, 10, 20, 255))
        d.ellipse([ex - 1, h * 0.35 - 1, ex + 1, h * 0.35 + 1], fill=PAL["lotus_petal"][3])
    d.arc([w * 0.25, h * 0.5, w * 0.75, h * 0.85], start=20, end=160, fill=(20, 10, 20, 255), width=2)

# ---------------------------------------------------------------- blocks
def save(im, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)

def block(name, painter, size=(S, S), light=True):
    im = new(size)
    painter(im)
    if light:
        top_left_light(im, None)
    save(im, BLOCK / f"{name}.png")

block("lotus_log", lambda im: cross_end_grain(im, PAL["lotus_wood"]))
block("lotus_dirt", lambda im: (granular(im, PAL["lotus_dirt"]), speckle_clusters(im, PAL["lotus_dirt"], 10, (1, 2))))
block("lotus_soil_fallback", lambda im: None) if False else None
block("infected_soil", lambda im: (granular(im, PAL["lotus_soil"]), speckle_clusters(im, PAL["lotus_soil"], 12, (1, 2), tones=(3,))))
block("lotus_gravel", lambda im: granular(im, PAL["lotus_gravel"]))
block("lotus_sand", lambda im: granular(im, PAL["lotus_sand"]))
block("lotus_terracotta", lambda im: granular(im, PAL["lotus_terra"]))
block("lotus_stone", lambda im: ore_veins(im, PAL["lotus_mineral"], PAL["lotus_mineral"], clusters=3))
block("lotus_ore", lambda im: ore_veins(im, PAL["lotus_mineral"], PAL["lotus_petal"], clusters=5))
block("lotus_leaves", lambda im: leafy_blotches(im, PAL["lotus_leaf"], 14))
block("lotus_leaf", lambda im: leafy_blotches(im, PAL["lotus_leaf"], 10))
block("blossom_grass", lambda im: (leafy_blotches(im, PAL["lotus_leaf"], 8), speckle_clusters(im, PAL["lotus_petal"], 6, (0, 1), tones=(2, 3))))
block("lotus_roots", lambda im: root_strands(im, PAL["lotus_wood"]), light=False)
block("tangled_roots", lambda im: root_strands(im, PAL["lotus_wood"], underwater=True), light=False)
block("lotus_heart", lambda im: heart_glow(im, PAL["lotus_heart"]), light=False)
block("lotus_mimic", lambda im: mimic_face(im, PAL["lotus_wood"]), light=False)
block("infected_lotus", lambda im: (fill_base(im, (0, 0, 0, 0)), im.paste(flower_petal((S, S), PAL["lotus_petal"]), (0, 0), flower_petal((S, S), PAL["lotus_petal"]))), light=False)
block("lotus_stem", lambda im: cross_end_grain(im, PAL["lotus_wood"]), light=False)
block("lotus_petal", lambda im: im.paste(flower_petal((S, S), PAL["lotus_petal"]), (0, 0), flower_petal((S, S), PAL["lotus_petal"])), light=False)
block("liana_barrier", lambda im: woven_vine(im, PAL["vine"]))
block("liana_weak_point", lambda im: woven_vine(im, PAL["weakpoint"], weak=True))

# Blessing biome
block("blessing_soil", lambda im: granular(im, PAL["blessing_soil"]))
block("blessing_log", lambda im: cross_end_grain(im, PAL["blessing_wood"]))
block("blessing_leaves", lambda im: leafy_blotches(im, PAL["blessing_leaf"], 12))
block("blessing_nodule", lambda im: (ore_veins(im, PAL["blessing_ice"], PAL["blessing_red"], clusters=3)))
block("glow_berries", lambda im: (fill_base(im, (0, 0, 0, 0)), speckle_clusters(im, ramp(["3a2f10", "b5851f", "ffce4d", "fff2b0"]), 10, (1, 2))), light=False)

# Liquid still/flow (16-frame vertical sheets, matching vanilla water convention)
def liquid_sheet(name, flowing):
    frames = 32
    sheet = new((S, S * frames))
    for f in range(frames):
        frame = new((S, S))
        liquid_bands(frame, PAL["liquid"], flowing=flowing, frames=1)
        sheet.paste(frame, (0, f * S))
    save(sheet, BLOCK / f"{name}.png")
    mcmeta = BLOCK / f"{name}.png.mcmeta"
    mcmeta.write_text('{"animation": {"frametime": 3}}', encoding="utf-8")

liquid_sheet("infected_water_still", flowing=False)
liquid_sheet("infected_water_flow", flowing=True)
frame0 = new((S, S))
liquid_bands(frame0, PAL["liquid"], flowing=False)
save(frame0, BLOCK / "infected_water.png")

# ---------------------------------------------------------------- animated lotus center (32x64, 2 frames)
sheet = new((S, S * 2))
sheet.paste(flower_center((S, S), 0), (0, 0))
sheet.paste(flower_center((S, S), 1), (0, S))
save(sheet, BLOCK / "lotus_center.png")

print("blocks done")
