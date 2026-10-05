"""Icons for the endings menu (64x64, with a VHS look): the four lights and Honcho's head.

Run: python tools/ending_icons.py
Writes src/main/resources/assets/lotusblight/textures/gui/endings/{moon,star,mischief,glitch,four,honcho}.png
The first three follow the three lights of the old finale animation (blue crescent, yellow four-point star, red diamond with a
spiral); the glitch light is the fourth: the purple Architect of the early ArchitectScene prototype (colour B25CFF, an upward triangle), torn into shifted slices.
"""
import colorsys
import math
import os
import random
import zlib
from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'lotusblight')
OUT = os.path.join(ROOT, 'textures', 'gui', 'endings')
S = 32
SS = 16  # drawn 16x larger and shrunk, for soft edges
OUT_S = 64   # size of the finished icon


def canvas():
    return Image.new('RGBA', (S * SS, S * SS), (0, 0, 0, 0))


def glow(img, color, radius, strength=1.0):
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    c = S * SS // 2
    r = int(radius * SS)
    d.ellipse((c - r, c - r, c + r, c + r), fill=color + (int(255 * strength),))
    layer = layer.filter(ImageFilter.GaussianBlur(S * SS * 0.06))
    return Image.alpha_composite(img, layer)


def vhs(img, seed):
    """A worn VHS look: the colour channels slip apart, scanlines, a few torn rows, tape noise, a little blur."""
    rnd = random.Random(seed)
    img = img.filter(ImageFilter.GaussianBlur(0.85))
    r, g, b, a = img.split()
    r = r.transform(r.size, Image.AFFINE, (1, 0, 3, 0, 1, 0))      # red slips right, blue left
    b = b.transform(b.size, Image.AFFINE, (1, 0, -3, 0, 1, 0))
    a = Image.merge('RGBA', (r, g, b, a)).split()[3]
    a = ImageChops.lighter(a, Image.merge('RGBA', (r, g, b, a)).split()[3])
    img = Image.merge('RGBA', (r, g, b, a))
    px = img.load()
    w, h = img.size
    # torn rows
    for _ in range(5):
        y = rnd.randrange(4, h - 4)
        sh = rnd.choice([-3, -2, 2, 3])
        row = img.crop((0, y, w, y + 2))
        img.paste((0, 0, 0, 0), (0, y, w, y + 2))
        img.paste(row, (sh, y))
    px = img.load()
    for y in range(h):
        for x in range(w):
            cr, cg, cb, ca = px[x, y]
            if ca == 0:
                continue
            k = 0.66 if y % 2 else 1.0                              # scanlines
            n = rnd.randint(-24, 24)
            px[x, y] = (max(0, min(255, int(cr * k + n))), max(0, min(255, int(cg * k + n))), max(0, min(255, int(cb * k + n))), ca)
    return img


def finish(img, name):
    img = img.resize((OUT_S, OUT_S), Image.LANCZOS)
    img = vhs(img, zlib.crc32(name.encode()))
    img.save(os.path.join(OUT, name + '.png'))


def moon():
    img = glow(canvas(), (30, 70, 255), 13)
    d = ImageDraw.Draw(img)
    c = S * SS // 2
    r = 7 * SS
    d.ellipse((c - r, c - r, c + r, c + r), fill=(190, 240, 255, 255))
    off = 3 * SS
    d.ellipse((c - r + off, c - r - SS, c + r + off, c + r - SS), fill=(25, 60, 220, 255))   # the bite that makes the crescent
    return img


def star_points(cx, cy, outer, inner, n=4):
    pts = []
    for i in range(n * 2):
        a = -math.pi / 2 + i * math.pi / n
        rad = outer if i % 2 == 0 else inner
        pts.append((cx + math.cos(a) * rad, cy + math.sin(a) * rad))
    return pts


def star():
    img = glow(canvas(), (255, 190, 60), 13)
    img = glow(img, (255, 235, 150), 6, 0.9)
    d = ImageDraw.Draw(img)
    c = S * SS // 2
    d.polygon(star_points(c, c, 11 * SS, 2.2 * SS), fill=(255, 245, 190, 255))
    return img


def spiral(d, cx, cy, size, color, width):
    pts = []
    for i in range(60):
        t = i / 59
        a = t * 3.2 * math.pi
        r = size * (0.15 + 0.85 * t)
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    d.line(pts, fill=color, width=width, joint='curve')


def mischief():
    img = glow(canvas(), (255, 20, 30), 13)
    d = ImageDraw.Draw(img)
    c = S * SS // 2
    k = 7.5 * SS
    d.polygon([(c, c - k), (c + k, c), (c, c + k), (c - k, c)], outline=(255, 190, 190, 255), width=SS)
    spiral(d, c, c, 4.3 * SS, (255, 190, 190, 255), SS)
    return img


def glitch():
    random.seed(7)
    img = glow(canvas(), (140, 30, 255), 13)
    d = ImageDraw.Draw(img)
    c = S * SS // 2
    r = 8 * SS
    d.ellipse((c - r, c - r, c + r, c + r), fill=(178, 92, 255, 255))
    k = 5.5 * SS   # the purple Architect's symbol in the early prototype: an upward triangle, outline only
    d.polygon([(c, c - k), (c + k * 0.9, c + k * 0.7), (c - k * 0.9, c + k * 0.7)], outline=(224, 187, 255, 255), width=SS)
    # tear the picture into rows and shift them
    out = Image.new('RGBA', img.size, (0, 0, 0, 0))
    y = 0
    while y < img.size[1]:
        h = random.choice([1, 2, 2, 3, 4]) * SS
        dx = random.choice([0, 0, 0, -3, 3, -5, 5, 8, -8]) * SS // 2
        band = img.crop((0, y, img.size[0], min(y + h, img.size[1])))
        out.paste(band, (dx, y))
        y += h
    return out


def line(d, x0, y0, x1, y1, color, w):
    d.line((x0 * SS, y0 * SS, x1 * SS, y1 * SS), fill=color, width=max(1, int(w * SS)))


def poly(d, pts, fill, outline=None, w=0.6):
    d.polygon([(x * SS, y * SS) for x, y in pts], fill=fill)
    if outline:
        d.line([(x * SS, y * SS) for x, y in pts + [pts[0]]], fill=outline, width=max(1, int(w * SS)), joint='curve')


def ellipse(d, x0, y0, x1, y1, fill, outline=None, w=0.6):
    d.ellipse((x0 * SS, y0 * SS, x1 * SS, y1 * SS), fill=fill, outline=outline, width=max(1, int(w * SS)))


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


def petal(d, cx, cy, ang, length, width, base):
    """One lotus petal: a pointed leaf shape with a lighter middle, turned by ang (0 = up)."""
    dx, dy = math.sin(ang), -math.cos(ang)

    def tr(u, v):
        return (cx + u * dx - v * dy, cy + u * dy + v * dx)

    side = [(i / 20 * length, math.sin(i / 20 * math.pi) ** 0.8 * width) for i in range(21)]
    outline = [tr(u, v) for u, v in side] + [tr(u, -v) for u, v in reversed(side)]
    poly(d, outline, base, shade(base, 0.45), 0.5)
    mid = [tr(u, v * 0.35) for u, v in side] + [tr(u, -v * 0.35) for u, v in reversed(side)]
    poly(d, mid, shade(base, 1.18))


def lotus_flower(d, cx, cy, size):
    pink = (236, 84, 168, 255)
    ellipse(d, cx - size * 0.9, cy + size * 0.1, cx + size * 0.9, cy + size * 0.55, (46, 132, 98, 255), (20, 70, 52, 255), 0.5)
    for ang, ln in [(-1.15, size * 0.8), (1.15, size * 0.8), (-0.62, size * 0.95), (0.62, size * 0.95), (0.0, size * 1.05)]:
        petal(d, cx, cy + size * 0.2, ang, ln, size * 0.26, pink)
    ellipse(d, cx - size * 0.12, cy - size * 0.1, cx + size * 0.12, cy + size * 0.14, (255, 222, 120, 255))


def sword(d, hx, hy, tx, ty):
    """A sword from the pommel (hx, hy) to the tip (tx, ty): leather grip, gold guard, steel blade with an edge shine."""
    dx, dy = tx - hx, ty - hy
    ln = math.hypot(dx, dy)
    ux, uy = dx / ln, dy / ln
    nx, ny = -uy, ux

    def pt(u, v):
        return (hx + ux * u + nx * v, hy + uy * u + ny * v)

    poly(d, [pt(0, -0.7), pt(5, -0.7), pt(5, 0.7), pt(0, 0.7)], (112, 70, 42, 255), (50, 28, 16, 255), 0.4)            # grip
    px, py = pt(0, 0)
    ellipse(d, px - 1, py - 1, px + 1, py + 1, (230, 190, 70, 255), (120, 90, 20, 255), 0.4)                           # pommel
    poly(d, [pt(5, -3.4), pt(6.6, -3.4), pt(6.6, 3.4), pt(5, 3.4)], (232, 192, 76, 255), (120, 90, 20, 255), 0.45)     # guard
    poly(d, [pt(6.6, -1.5), pt(ln - 2.5, -1.5), pt(ln, 0), pt(ln - 2.5, 1.5), pt(6.6, 1.5)], (190, 202, 216, 255), (80, 92, 108, 255), 0.5)   # blade
    a, b = pt(7, -0.2), pt(ln - 3, -0.2)
    line(d, a[0], a[1], b[0], b[1], (245, 250, 255, 255), 0.45)                                                         # shine


def war():
    img = canvas()
    d = ImageDraw.Draw(img)
    sword(d, 3.5, 29, 27.5, 4)
    sword(d, 28.5, 29, 4.5, 4)
    ellipse(d, 8, 7.5, 24, 23.5, (24, 12, 30, 240), (240, 200, 90, 255), 0.55)
    lotus_flower(d, 16, 14.8, 6.6)
    return img


def heart(d, cx, cy, k=1.0):
    pts = []
    for i in range(61):
        t = i / 60 * 2 * math.pi
        x = 16 * math.sin(t) ** 3
        y = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
        pts.append((cx + x * 0.34 * k, cy + y * 0.34 * k + 0.5))
    poly(d, pts, (226, 44, 66, 255), (110, 14, 28, 255), 0.5)
    sh = [(cx - 3.0 * k, cy - 2.4 * k), (cx - 1.6 * k, cy - 3.3 * k), (cx - 0.6 * k, cy - 2.4 * k), (cx - 2.2 * k, cy - 1.4 * k)]
    poly(d, sh, (255, 170, 180, 255))


def mini_star(d, cx, cy, r):
    pts = [(cx + math.cos(-math.pi / 2 + i * math.pi / 4) * (r if i % 2 == 0 else r * 0.28),
            cy + math.sin(-math.pi / 2 + i * math.pi / 4) * (r if i % 2 == 0 else r * 0.28)) for i in range(8)]
    poly(d, pts, (255, 238, 150, 255), (200, 140, 40, 255), 0.35)
    ellipse(d, cx - r * 0.2, cy - r * 0.2, cx + r * 0.2, cy + r * 0.2, (255, 255, 235, 255))


# The face of the player is drawn into the transparent square (FACE) by the game, from the player's own skin.
FACE = (10, 1, 12)   # x, y, size


def alliance():
    img = canvas()
    d = ImageDraw.Draw(img)
    mini_star(d, 6.5, 24, 5.5)
    mini_star(d, 25.5, 24, 5.5)
    heart(d, 16, 23, 1.15)
    return img


def neutral():
    img = canvas()
    d = ImageDraw.Draw(img)
    poly(d, [(8, 15), (23, 15), (24, 31.5), (7, 31.5)], (58, 88, 164, 255), (24, 36, 84, 255), 0.5)      # the body under the face
    poly(d, [(8, 15), (13, 15), (16, 18), (19, 15), (23, 15), (23, 18), (8, 18)], (46, 70, 138, 255))
    poly(d, [(23, 17), (26.5, 17), (27, 25), (23.5, 25)], (205, 160, 126, 255), (110, 76, 56, 255), 0.4)  # the arm that holds the knife
    poly(d, [(26, 17), (29.4, 8.6), (30.2, 10.4), (28, 17)], (214, 222, 232, 255), (80, 92, 108, 255), 0.4)   # the blade, pointing up
    line(d, 27.7, 16, 29.4, 10.2, (250, 252, 255, 255), 0.3)
    poly(d, [(25.2, 16.6), (28.6, 16.6), (28.6, 18.0), (25.2, 18.0)], (230, 190, 70, 255), (120, 90, 20, 255), 0.35)
    return img


def honcho():
    """From the render: a head that is a black blob shedding square pixels, a lavender suit, brown braces, a dark purple tie."""
    img = canvas()
    d = ImageDraw.Draw(img)
    rnd = random.Random(5)
    poly(d, [(3, 31.5), (4.5, 22.5), (11, 20.5), (21, 20.5), (27.5, 22.5), (29, 31.5)], (150, 140, 224, 255), (66, 56, 140, 255), 0.5)    # shoulders and chest
    poly(d, [(3, 31.5), (4.5, 22.5), (8, 21.5), (7, 31.5)], (124, 112, 204, 255))                                                        # shaded side
    poly(d, [(12.5, 20.5), (19.5, 20.5), (16, 24)], (236, 236, 250, 255), (150, 150, 190, 255), 0.3)                                      # collar
    poly(d, [(15, 22), (17.4, 22), (18.6, 29), (16, 31), (14, 29)], (84, 62, 150, 255), (40, 28, 90, 255), 0.3)                           # tie
    poly(d, [(9.5, 21), (11.6, 21), (13, 31.5), (10.8, 31.5)], (136, 66, 40, 255), (70, 30, 16, 255), 0.25)                               # braces
    poly(d, [(20.4, 21), (22.5, 21), (21.2, 31.5), (19, 31.5)], (136, 66, 40, 255), (70, 30, 16, 255), 0.25)
    # the head: a black blot with square pixels flying off it
    ellipse(d, 9.5, 2.5, 22.5, 19.5, (6, 6, 8, 255))
    for _ in range(14):
        x = rnd.uniform(6, 26)
        y = rnd.uniform(0.5, 17)
        if 9.5 < x < 22.5 and 3 < y < 19:
            continue
        sz = rnd.uniform(0.7, 1.6)
        d.rectangle((x * SS, y * SS, (x + sz) * SS, (y + sz) * SS), fill=(6, 6, 8, 255))
    return img


def dismembered():
    """Hijack tearing out of a TV: a CRT with a red static screen and antennae, and a glowing red figure climbing out of it."""
    img = canvas()
    d = ImageDraw.Draw(img)
    rnd = random.Random(21)
    # the cart under the set
    line(d, 3.5, 22, 3.5, 31.5, (30, 12, 14, 255), 1.1)
    line(d, 17.5, 22, 17.5, 31.5, (30, 12, 14, 255), 1.1)
    line(d, 2.5, 23, 18.5, 23, (40, 16, 18, 255), 1.1)
    line(d, 3, 29.5, 18, 29.5, (40, 16, 18, 255), 1.0)
    # the set itself
    line(d, 7, 7.5, 5, 3.2, (50, 20, 22, 255), 0.5)
    line(d, 10, 7.5, 12.5, 3.2, (50, 20, 22, 255), 0.5)
    poly(d, [(2.5, 8), (17.5, 7.2), (18.4, 21.5), (3.5, 22.4)], (36, 14, 16, 255), (96, 34, 36, 255), 0.6)
    scr = [(4.4, 9.4), (16.3, 8.9), (16.9, 19.9), (5.0, 20.6)]
    poly(d, scr, (60, 4, 6, 255), (150, 30, 30, 255), 0.4)
    # red static in the screen
    mask = Image.new('L', img.size, 0)
    ImageDraw.Draw(mask).polygon([(x * SS, y * SS) for x, y in scr], fill=255)
    stat = Image.new('RGBA', img.size, (0, 0, 0, 0))
    sp = stat.load()
    cell = SS // 2
    for gy in range(0, img.size[1], cell):
        for gx in range(0, img.size[0], cell):
            v = rnd.random()
            if v > 0.55:
                col = (255, int(60 + 80 * rnd.random()), int(60 * rnd.random()), 255) if v > 0.9 else (190, 12, 14, 255)
                for yy in range(gy, min(gy + cell, img.size[1])):
                    for xx in range(gx, min(gx + cell, img.size[0])):
                        sp[xx, yy] = col
    img.paste(stat, (0, 0), mask)
    # the figure: a red glow first, then the body
    fig = Image.new('RGBA', img.size, (0, 0, 0, 0))
    fd = ImageDraw.Draw(fig)
    red = (238, 18, 18, 255)
    ellipse(fd, 18.6, 7.5, 25.6, 15.8, red)                                                      # head
    poly(fd, [(19, 16.8), (26, 16.2), (29.2, 22), (28, 31.5), (19.5, 31.5), (19, 24)], red)         # torso and legs
    line(fd, 18, 18, 11.5, 15.5, red, 2.6)                                                       # the arm reaching out of the screen
    for dx, dy in [(-3.2, -1.6), (-3.4, 0.3), (-2.4, 1.8), (-1.2, 2.6)]:                         # the fingers
        line(fd, 11.5, 15.5, 11.5 + dx, 15.5 + dy, red, 1.1)
    line(fd, 27.5, 22, 30.5, 27, red, 2.4)                                                       # the other arm
    rim = fig.split()[3].filter(ImageFilter.MaxFilter(SS * 2 + 1))          # a dark rim keeps the figure apart from the screen
    rim_layer = Image.new('RGBA', img.size, (14, 2, 4, 255))
    rim_layer.putalpha(rim)
    img = Image.alpha_composite(img, rim_layer)
    halo = fig.filter(ImageFilter.GaussianBlur(SS * 0.9))
    halo = Image.merge('RGBA', (halo.split()[0], halo.split()[1].point(lambda v: v // 2), halo.split()[2].point(lambda v: v // 2), halo.split()[3].point(lambda v: int(v * 0.45))))
    img = Image.alpha_composite(img, halo)
    img = Image.alpha_composite(img, fig)
    # white speckle on the body, like the reference
    sp2 = img.load()
    fa = fig.split()[3].load()
    for _ in range(260):
        x = rnd.randrange(img.size[0])
        y = rnd.randrange(img.size[1])
        if fa[x, y] > 200 and rnd.random() < 0.6:
            for yy in range(y, min(y + SS // 3, img.size[1])):
                for xx in range(x, min(x + SS // 3, img.size[0])):
                    sp2[xx, yy] = (255, 235, 235, 255)
    return img


def chromo():
    """A diamond cut in two: rainbow on the left, black-and-white on the right."""
    img = canvas()
    c = S * SS // 2
    k = 12.5 * SS
    mask = Image.new('L', img.size, 0)
    ImageDraw.Draw(mask).polygon([(c, c - k), (c + k, c), (c, c + k), (c - k, c)], fill=255)
    fill = Image.new('RGBA', img.size, (0, 0, 0, 0))
    px = fill.load()
    W, H = img.size
    for y in range(H):
        for x in range(W):
            if x < c:
                h = (y / H * 0.85 + (x / W) * 0.25) % 1.0       # rainbow bands, slanted
                r, g, b = colorsys.hsv_to_rgb(h, 0.95, 1.0)
                px[x, y] = (int(r * 255), int(g * 255), int(b * 255), 255)
            else:
                v = 255 if ((x // (2 * SS)) + (y // (2 * SS))) % 2 == 0 else 20       # black and white squares
                px[x, y] = (v, v, v, 255)
    img.paste(fill, (0, 0), mask)
    d = ImageDraw.Draw(img)
    d.polygon([(c, c - k), (c + k, c), (c, c + k), (c - k, c)], outline=(235, 235, 245, 255), width=int(0.7 * SS))
    d.line((c, c - k, c, c + k), fill=(235, 235, 245, 255), width=int(0.7 * SS))
    return glow(img, (200, 200, 255), 15, 0.35) if False else img


def four():
    # the four lights at once: each in its own quarter
    parts = [moon(), star(), mischief(), glitch()]
    img = canvas()
    half = S * SS // 2
    for i, p in enumerate(parts):
        small = p.resize((half, half), Image.LANCZOS)
        img.paste(small, ((i % 2) * half, (i // 2) * half), small)
    return img


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for name, fn in [('moon', moon), ('star', star), ('mischief', mischief), ('glitch', glitch), ('four', four)]:
        finish(fn(), name)
    finish(honcho(), 'honcho')
    for name, fn in [('war', war), ('alliance', alliance), ('neutral', neutral), ('dismembered', dismembered), ('chromo', chromo)]:
        finish(fn(), name)
    # a preview strip, enlarged
    names = ['moon', 'star', 'mischief', 'glitch', 'four', 'honcho', 'war', 'alliance', 'neutral', 'dismembered', 'chromo']
    strip = Image.new('RGBA', (S * 11 * 4, S * 4), (28, 28, 34, 255))
    for i, n in enumerate(names):
        im = Image.open(os.path.join(OUT, n + '.png')).resize((S * 4, S * 4), Image.LANCZOS)
        strip.paste(im, (i * S * 4, 0), im)
    strip.save(os.path.join(os.environ.get('TEMP', '.'), 'ending_icons_preview.png'))
    print('ok')
