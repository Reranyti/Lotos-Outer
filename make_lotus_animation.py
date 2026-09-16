from pathlib import Path
from PIL import Image, ImageDraw
import math

ROOT = Path(__file__).resolve().parent / 'src' / 'main' / 'resources' / 'assets' / 'lotusblight' / 'textures' / 'block'
out = ROOT / 'lotus_center.png'

FRAME = 32
sheet = Image.new('RGBA', (FRAME, FRAME * 2), (0, 0, 0, 0))
px = sheet.load()

def lerp_color(c1, c2, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))

def hash01(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xffffffff
    n = (n ^ (n >> 13)) * 1274126177 & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffffffff) / 0xffffffff

# Two animation frames: dim glow -> bright pulse, matching the existing
# frametime/frames metadata in lotus_center.png.mcmeta (unchanged).
for frame, (rim, mid, bright) in enumerate([
    ((177, 73, 34), (225, 130, 38), (255, 224, 104)),
    ((196, 88, 40), (255, 190, 62), (255, 240, 150)),
]):
    y0 = frame * FRAME
    cx, cy = FRAME / 2, FRAME / 2
    radius = FRAME * 0.42
    d = ImageDraw.Draw(sheet)
    d.ellipse((cx - radius, y0 + cy - radius, cx + radius, y0 + cy + radius), fill=rim + (255,))
    for y in range(FRAME):
        for x in range(FRAME):
            dist = math.hypot(x - cx, y - cy) / radius
            if dist > 1.0:
                continue
            if dist > 0.55:
                c = lerp_color(mid, rim, (dist - 0.55) / 0.45)
            else:
                c = lerp_color(bright, mid, dist / 0.55)
            n = hash01(x, y, frame * 17 + 5)
            c = tuple(max(0, min(255, int(v + (n - 0.5) * 14))) for v in c)
            px[x, y0 + y] = c + (255,)
    # stamen flecks for texture
    for ang_deg in range(0, 360, 40):
        ang = math.radians(ang_deg)
        fx = cx + math.cos(ang) * radius * 0.55
        fy = cy + math.sin(ang) * radius * 0.55
        d.point((fx, y0 + fy), fill=(154, 57, 32, 255))

sheet.save(out)
print('make_lotus_animation.py: wrote', out, sheet.size)
