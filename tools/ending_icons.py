"""Icons for the endings menu (32x32): the four lights and Honcho's head.

Run: python tools/ending_icons.py
Writes src/main/resources/assets/lotusblight/textures/gui/endings/{moon,star,mischief,glitch,four,honcho}.png
The first three follow the three lights of the old finale animation (blue crescent, yellow four-point star, red diamond with a
spiral); the glitch light is the fourth: the purple Architect of the early ArchitectScene prototype (colour B25CFF, an upward triangle), torn into shifted slices.
"""
import math
import os
import random
from PIL import Image, ImageDraw, ImageFilter

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


def finish(img, name):
    img = img.resize((OUT_S, OUT_S), Image.LANCZOS)
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
    """The skin's head is plain black: a black head over the pale suit with the red tie."""
    img = canvas()
    d = ImageDraw.Draw(img)
    poly(d, [(3, 31.5), (4.5, 23), (11, 21), (21, 21), (27.5, 23), (29, 31.5)], (190, 196, 244, 255), (70, 74, 130, 255), 0.5)
    poly(d, [(12, 21), (20, 21), (16, 26)], (255, 255, 255, 255), (150, 150, 190, 255), 0.35)
    poly(d, [(15.2, 24.5), (16.8, 24.5), (17.6, 31), (14.4, 31)], (206, 62, 62, 255), (110, 24, 24, 255), 0.3)
    poly(d, [(9, 2.5), (23, 2.5), (23, 20.5), (9, 20.5)], (10, 10, 14, 255), (70, 70, 84, 255), 0.5)
    line(d, 9.8, 3.4, 22.2, 3.4, (46, 46, 58, 255), 0.5)
    return img


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
    for name, fn in [('war', war), ('alliance', alliance), ('neutral', neutral)]:
        finish(fn(), name)
    # a preview strip, enlarged
    names = ['moon', 'star', 'mischief', 'glitch', 'four', 'honcho', 'war', 'alliance', 'neutral']
    strip = Image.new('RGBA', (S * 9 * 4, S * 4), (28, 28, 34, 255))
    for i, n in enumerate(names):
        im = Image.open(os.path.join(OUT, n + '.png')).resize((S * 4, S * 4), Image.LANCZOS)
        strip.paste(im, (i * S * 4, 0), im)
    strip.save(os.path.join(os.environ.get('TEMP', '.'), 'ending_icons_preview.png'))
    print('ok')
