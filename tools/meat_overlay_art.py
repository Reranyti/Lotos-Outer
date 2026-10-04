"""
Draws the art for the "Психически не здоров." effect: the two walls of meat that close in on the screen from the left and the right
(the left one is the right one flipped, but made apart so the veins differ), and the effect's icon.

Run:  python tools/meat_overlay_art.py
Writes src/main/resources/assets/lotusblight/textures/misc/meat_wall_left.png, meat_wall_right.png and textures/mob_effect/mentally_unwell.png
"""
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
RES = os.path.join(ROOT, 'src/main/resources/assets/lotusblight/textures')
W, H = 128, 512


def periodic(w, h, seed, beta=2.0):
    g = np.random.RandomState(seed)
    spec = np.fft.fft2(g.randn(h, w))
    fy = np.fft.fftfreq(h)[:, None]
    fx = np.fft.fftfreq(w)[None, :]
    r = np.sqrt(fx ** 2 + fy ** 2)
    r[0, 0] = 1
    spec = spec / r ** (beta / 2)
    spec[0, 0] = 0
    n = np.real(np.fft.ifft2(spec))
    return (n - n.min()) / (n.max() - n.min())


def wall(seed, flip):
    lumps = periodic(W, H, seed, 2.6)
    fine = periodic(W, H, seed + 1, 1.4)
    veins = np.abs(periodic(W, H, seed + 2, 3.4) - 0.5)
    vein = np.exp(-(veins / 0.02) ** 2)
    lo, hi = np.array([34, 8, 10], float), np.array([128, 30, 28], float)
    col = lo + (hi - lo) * (0.7 * lumps + 0.3 * fine)[..., None]
    col += vein[..., None] * np.array([120, 14, 10])              # glowing cracks
    # the wall is solid at the screen's edge (x = 0) and dissolves into lumps towards the middle
    x = np.linspace(0, 1, W)[None, :].repeat(H, axis=0)
    edge = 0.55 + 0.35 * (lumps - 0.5) * 2                        # where this row's flesh ends: lumpy
    alpha = np.clip((edge - x) / 0.22, 0, 1)
    alpha = np.where(x < 0.08, 1.0, alpha)
    img = np.dstack([np.clip(col, 0, 255), alpha * 255]).astype(np.uint8)
    out = Image.fromarray(img, 'RGBA').filter(ImageFilter.GaussianBlur(0.6))
    if flip:
        out = out.transpose(Image.FLIP_LEFT_RIGHT)
    return out


def icon():
    im = Image.new('RGBA', (18, 18), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse([2, 3, 15, 14], fill=(110, 110, 116, 255), outline=(40, 40, 46, 255))      # a grey head
    d.ellipse([5, 6, 8, 9], fill=(20, 20, 24, 255))
    d.ellipse([10, 6, 13, 9], fill=(20, 20, 24, 255))
    d.ellipse([6, 7, 7, 8], fill=(220, 40, 30, 255))
    d.ellipse([11, 7, 12, 8], fill=(220, 40, 30, 255))
    d.line([(5, 12), (13, 12)], fill=(60, 14, 16, 255), width=1)
    d.line([(3, 2), (6, 4)], fill=(150, 30, 28, 255))                                     # a vein over the top
    d.line([(14, 2), (11, 4)], fill=(150, 30, 28, 255))
    return im


def main():
    os.makedirs(os.path.join(RES, 'misc'), exist_ok=True)
    os.makedirs(os.path.join(RES, 'mob_effect'), exist_ok=True)
    wall(5, False).save(os.path.join(RES, 'misc/meat_wall_left.png'))
    wall(17, True).save(os.path.join(RES, 'misc/meat_wall_right.png'))
    icon().save(os.path.join(RES, 'mob_effect/mentally_unwell.png'))
    print('ok')


if __name__ == '__main__':
    main()
