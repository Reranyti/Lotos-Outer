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


def lotus_flower(d, cx, cy, scale=1):
    """A small pink lotus (three petals over a green cup), in the palette of the lotus seed."""
    P = [(255, 120, 190, 255), (222, 60, 150, 255), (150, 20, 100, 255)]
    for dx, dy, w, h, c in [(-4, -2, 3, 5, 1), (4, -2, 3, 5, 1), (0, -4, 4, 7, 0), (-2, -1, 3, 5, 0), (2, -1, 3, 5, 0)]:
        d.ellipse((cx + (dx - w) * scale, cy + (dy - h / 2) * scale, cx + (dx + w) * scale, cy + (dy + h / 2) * scale), fill=P[c], outline=P[2])
    d.ellipse((cx - 5 * scale, cy + 2 * scale, cx + 5 * scale, cy + 5 * scale), fill=(40, 120, 90, 255))


def sword(d, x0, y0, x1, y1):
    """A pixel sword from the hilt (x0, y0) to the tip (x1, y1)."""
    d.line((x0, y0, x1, y1), fill=(205, 215, 225, 255), width=2)
    d.line((x0, y0, x0 + (x1 - x0) * 0.15, y0 + (y1 - y0) * 0.15), fill=(110, 70, 40, 255), width=2)
    gx, gy = x0 + (x1 - x0) * 0.22, y0 + (y1 - y0) * 0.22
    d.line((gx - 3, gy + 3, gx + 3, gy - 3) if (x1 - x0) * (y1 - y0) < 0 else (gx - 3, gy - 3, gx + 3, gy + 3), fill=(230, 190, 70, 255), width=2)


def war():
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    sword(d, 4, 28, 27, 5)      # two swords crossed
    sword(d, 27, 28, 4, 5)
    lotus_flower(d, 16, 14)
    return img


def heart(d, cx, cy, c=(230, 40, 60, 255)):
    d.polygon([(cx - 5, cy - 2), (cx - 3, cy - 4), (cx - 1, cy - 4), (cx, cy - 2), (cx + 1, cy - 4), (cx + 3, cy - 4), (cx + 5, cy - 2),
               (cx + 5, cy), (cx, cy + 5), (cx - 5, cy)], fill=c)


def mini_star(d, cx, cy, r=4):
    d.polygon(star_points(cx, cy, r, r * 0.3), fill=(255, 235, 150, 255))
    d.ellipse((cx - 1, cy - 1, cx + 1, cy + 1), fill=(255, 255, 230, 255))


# The face of the player is drawn into the transparent square (FACE) by the game, from the player's own skin.
FACE = (10, 1, 12)   # x, y, size


def alliance():
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    mini_star(d, 6, 25, 5)
    mini_star(d, 26, 25, 5)
    heart(d, 16, 24)
    return img


def neutral():
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle((8, 15, 23, 31), fill=(60, 90, 160, 255))                 # the body under the face
    d.rectangle((23, 18, 25, 26), fill=(190, 150, 120, 255))              # the arm that holds the knife
    d.line((24, 18, 28, 8), fill=(215, 220, 230, 255), width=2)           # blade
    d.line((24, 18, 24, 20), fill=(110, 70, 40, 255), width=2)
    d.line((22, 18, 26, 18), fill=(230, 190, 70, 255), width=1)
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
    for name, fn in [('war', war), ('alliance', alliance), ('neutral', neutral)]:
        fn().save(os.path.join(OUT, name + '.png'))
    # a preview strip, enlarged
    names = ['moon', 'star', 'mischief', 'glitch', 'four', 'honcho', 'war', 'alliance', 'neutral']
    strip = Image.new('RGBA', (S * 9 * 4, S * 4), (10, 10, 14, 255))
    for i, n in enumerate(names):
        im = Image.open(os.path.join(OUT, n + '.png')).resize((S * 4, S * 4), Image.NEAREST)
        strip.paste(im, (i * S * 4, 0), im)
    strip.save(os.path.join(os.environ.get('TEMP', '.'), 'ending_icons_preview.png'))
    print('ok')
