"""Icons for the endings menu (32x32): the four lights and Honcho's head.

Run: python tools/ending_icons.py
Writes src/main/resources/assets/lotusblight/textures/gui/endings/{moon,star,mischief,glitch,four,honcho}.png
The first three follow the three lights of the old finale animation (blue crescent, yellow four-point star, red diamond with a
spiral); the glitch light is the fourth: a purple orb torn into shifted slices with the spiral inside.
"""
import math
import os
import random
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'lotusblight')
OUT = os.path.join(ROOT, 'textures', 'gui', 'endings')
S = 32
SS = 8   # drawn 8x larger and shrunk, for soft edges


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
    img = img.resize((S, S), Image.LANCZOS)
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
    d.ellipse((c - r, c - r, c + r, c + r), fill=(170, 60, 255, 255))
    spiral(d, c, c, 6.5 * SS, (40, 0, 90, 255), SS)
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


def four():
    # the four lights at once: each in its own quarter
    parts = [moon(), star(), mischief(), glitch()]
    img = canvas()
    half = S * SS // 2
    for i, p in enumerate(parts):
        small = p.resize((half, half), Image.LANCZOS)
        img.paste(small, ((i % 2) * half, (i // 2) * half), small)
    return img


def honcho():
    # the skin's head is plain black, so the icon is a black head over the pale suit with the red tie
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((3, 22, 28, 31), fill=(196, 200, 244, 255))            # shoulders
    d.rectangle((13, 21, 18, 24), fill=(255, 255, 255, 255))           # collar
    d.rectangle((15, 23, 16, 30), fill=(200, 60, 60, 255))             # tie
    d.rectangle((9, 3, 22, 20), fill=(8, 8, 10, 255))                  # head
    d.rectangle((9, 3, 22, 3), fill=(40, 40, 48, 255))
    return img


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for name, fn in [('moon', moon), ('star', star), ('mischief', mischief), ('glitch', glitch), ('four', four)]:
        finish(fn(), name)
    honcho().save(os.path.join(OUT, 'honcho.png'))
    # a preview strip, enlarged
    names = ['moon', 'star', 'mischief', 'glitch', 'four', 'honcho']
    strip = Image.new('RGBA', (S * 6 * 4, S * 4), (10, 10, 14, 255))
    for i, n in enumerate(names):
        im = Image.open(os.path.join(OUT, n + '.png')).resize((S * 4, S * 4), Image.NEAREST)
        strip.paste(im, (i * S * 4, 0), im)
    strip.save(os.path.join(os.environ.get('TEMP', '.'), 'ending_icons_preview.png'))
    print('ok')
