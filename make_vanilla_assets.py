from pathlib import Path
from PIL import Image, ImageDraw
import math

ROOT = Path(__file__).resolve().parent / 'src' / 'main' / 'resources' / 'assets' / 'lotusblight' / 'textures'
BLOCK = ROOT / 'block'
ITEM = ROOT / 'item'
BLOCK.mkdir(parents=True, exist_ok=True)
ITEM.mkdir(parents=True, exist_ok=True)

def new(name, size=(16,16)):
    return Image.new('RGBA', size, (0,0,0,0))

def save(im, where, name):
    im.save(where / f'{name}.png')

# ---------------------------------------------------------------------------
# Shared gradient / noise helpers
# ---------------------------------------------------------------------------

def _hash01(x, y, seed):
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xffffffff
    n = (n ^ (n >> 13)) * 1274126177 & 0xffffffff
    n = (n ^ (n >> 16)) & 0xffffffff
    return n / 0xffffffff

def lerp(a, b, t):
    return a + (b - a) * t

def lerp_color(c1, c2, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(lerp(c1[i], c2[i], t))) for i in range(3))

def jitter(color, amount, x, y, seed):
    n = (_hash01(x, y, seed) - 0.5) * 2 * amount
    return tuple(max(0, min(255, int(round(c + n)))) for c in color)

# ---------------------------------------------------------------------------
# Infected lotus flower (cross sprite used by infected_lotus_crown.json)
# Real Nelumbo-nucifera-inspired silhouette: teardrop petals radiating from a
# centre, pink -> magenta gradient toward the tips, small yellow stamen cluster.
# ---------------------------------------------------------------------------

def make_infected_lotus(size=32, seed=41):
    im = new('infected_lotus', (size, size))
    d = ImageDraw.Draw(im)
    cx, cy = size / 2, size / 2 + 2
    petal_base = (232, 78, 142)
    petal_tip = (168, 21, 96)
    petal_hi = (255, 168, 205)
    n_petals = 8
    outer_r = size * 0.46
    inner_r = size * 0.12
    for i in range(n_petals):
        ang = (2 * math.pi / n_petals) * i - math.pi / 2
        spread = 0.34
        tip = (cx + math.cos(ang) * outer_r, cy + math.sin(ang) * outer_r)
        left_a = ang - spread
        right_a = ang + spread
        base_l = (cx + math.cos(left_a) * inner_r, cy + math.sin(left_a) * inner_r)
        base_r = (cx + math.cos(right_a) * inner_r, cy + math.sin(right_a) * inner_r)
        mid_l = (cx + math.cos(ang - spread * 0.5) * outer_r * 0.6,
                  cy + math.sin(ang - spread * 0.5) * outer_r * 0.6)
        mid_r = (cx + math.cos(ang + spread * 0.5) * outer_r * 0.6,
                  cy + math.sin(ang + spread * 0.5) * outer_r * 0.6)
        poly = [base_l, mid_l, tip, mid_r, base_r]
        d.polygon(poly, fill=petal_base + (255,))
    # gradient + highlight pass, pixel by pixel within drawn silhouette
    px = im.load()
    for y in range(size):
        for x in range(size):
            a = px[x, y][3]
            if a == 0:
                continue
            dx, dy = x - cx, y - cy
            dist = math.hypot(dx, dy) / outer_r
            base = lerp_color(petal_base, petal_tip, min(1.0, dist))
            # brighten a streak down the centre of each petal for a vein highlight
            ang = math.atan2(dy, dx) + math.pi / 2
            rel = (ang % (2 * math.pi / n_petals)) - (math.pi / n_petals)
            streak = max(0.0, 1 - abs(rel) * 6)
            base = lerp_color(base, petal_hi, streak * 0.45)
            base = jitter(base, 6, x, y, seed)
            px[x, y] = base + (255,)
    # stamen / seed-pod cluster at centre
    d.ellipse((cx - 5, cy - 5, cx + 5, cy + 5), fill=(214, 156, 40, 255))
    d.ellipse((cx - 4, cy - 4, cx + 4, cy + 4), fill=(255, 214, 84, 255))
    for i in range(9):
        ang = (2 * math.pi / 9) * i
        px_, py_ = cx + math.cos(ang) * 2.4, cy + math.sin(ang) * 2.4
        d.point((px_, py_), fill=(150, 92, 24, 255))
    save(im, BLOCK, 'infected_lotus')

# ---------------------------------------------------------------------------
# Lotus log / rhizome bark (cube_column end + side) — organic vertical grain
# with soft gradient shading instead of a flat fill.
# ---------------------------------------------------------------------------

def make_lotus_log(size=32, seed=11):
    im = Image.new('RGBA', (size, size), (0, 0, 0, 255))
    px = im.load()
    dark = (38, 69, 54)
    base = (83, 128, 79)
    light = (152, 189, 100)
    for y in range(size):
        row_t = 0.5 + 0.5 * math.sin(y * 0.35)
        for x in range(size):
            # vertical bark ridges via low-frequency sine + hash grain
            ridge = math.sin((x + row_t * 2) * 0.9) * 0.5 + 0.5
            c = lerp_color(dark, base, ridge)
            n = _hash01(x, y, seed)
            if n > 0.94:
                c = light
            elif n > 0.85:
                c = lerp_color(c, light, 0.5)
            elif n < 0.06:
                c = lerp_color(c, dark, 0.6)
            c = jitter(c, 5, x, y, seed)
            px[x, y] = c + (255,)
    save(im, BLOCK, 'lotus_log')

# ---------------------------------------------------------------------------
# Flower parts used by the rich multi-part model (block/infected_lotus.json):
# stem, leaf, petal, animated center (center handled by make_lotus_animation.py)
# ---------------------------------------------------------------------------

def make_lotus_petal(size=32, seed=51):
    im = new('lotus_petal', (size, size))
    d = ImageDraw.Draw(im)
    d.polygon([
        (size*0.5, size*0.03), (size*0.74, size*0.22), (size*0.82, size*0.58),
        (size*0.62, size*0.94), (size*0.5, size*1.0), (size*0.38, size*0.94),
        (size*0.18, size*0.58), (size*0.26, size*0.22),
    ], fill=(238, 83, 145, 255))
    px = im.load()
    tip = (168, 21, 96)
    base = (255, 182, 209)
    for y in range(size):
        for x in range(size):
            if px[x, y][3] == 0:
                continue
            t = y / size
            c = lerp_color(base, tip, t)
            d_center = abs(x - size / 2) / (size / 2)
            c = lerp_color(c, (255, 210, 226), max(0, 0.35 - d_center) )
            c = jitter(c, 5, x, y, seed)
            px[x, y] = c + (255,)
    save(im, BLOCK, 'lotus_petal')

def make_lotus_leaf(size=32, seed=61):
    im = new('lotus_leaf', (size, size))
    d = ImageDraw.Draw(im)
    d.ellipse((size*0.06, size*0.14, size*0.94, size*0.86), fill=(29, 154, 86, 255))
    px = im.load()
    dark = (12, 88, 61)
    light = (108, 225, 128)
    cx, cy = size / 2, size / 2
    for y in range(size):
        for x in range(size):
            if px[x, y][3] == 0:
                continue
            dist = math.hypot(x - cx, y - cy) / (size * 0.45)
            c = lerp_color(light, dark, min(1.0, dist))
            c = jitter(c, 6, x, y, seed)
            px[x, y] = c + (255,)
    # radiating veins
    for ang_deg in range(0, 360, 40):
        ang = math.radians(ang_deg)
        for r in range(0, int(size*0.42)):
            x = int(cx + math.cos(ang) * r)
            y = int(cy + math.sin(ang) * r)
            if 0 <= x < size and 0 <= y < size and px[x, y][3]:
                px[x, y] = (98, 214, 116, 255)
    save(im, BLOCK, 'lotus_leaf')

def make_lotus_stem(size=32, seed=71):
    im = new('lotus_stem', (size, size))
    px = im.load()
    mid = size / 2
    for y in range(size):
        for x in range(size):
            d = abs(x - mid)
            if d > size * 0.14:
                continue
            t = 1 - (d / (size * 0.14))
            c = lerp_color((25, 93, 51), (110, 205, 96), t)
            c = jitter(c, 5, x, y, seed)
            px[x, y] = c + (255,)
    save(im, BLOCK, 'lotus_stem')

# ---------------------------------------------------------------------------
# Ground / plant blocks
# ---------------------------------------------------------------------------

def make_infected_soil(size=32, seed=81):
    im = Image.new('RGBA', (size, size), (0, 0, 0, 255))
    px = im.load()
    dark = (24, 61, 46)
    base = (42, 100, 66)
    light = (86, 165, 96)
    for y in range(size):
        for x in range(size):
            n = _hash01(x, y, seed)
            n2 = _hash01(x // 2, y // 2, seed + 7)
            t = (n * 0.6 + n2 * 0.4)
            c = lerp_color(dark, base, 0.4 + t * 0.4)
            if n > 0.9:
                c = lerp_color(c, light, 0.7)
            if n2 > 0.95:
                c = (214, 96, 156)  # rare pink infection speck
            c = jitter(c, 6, x, y, seed)
            px[x, y] = c + (255,)
    save(im, BLOCK, 'infected_soil')

def make_lotus_roots(size=32, seed=91):
    im = new('lotus_roots', (size, size))
    d = ImageDraw.Draw(im)
    px = im.load()
    trunk = [(size*0.5, size*0.98), (size*0.47, size*0.55), (size*0.5, size*0.1)]
    d.line(trunk, fill=(64, 96, 58, 255), width=3)
    branches = [
        [(size*0.49, size*0.75), (size*0.22, size*0.6), (size*0.1, size*0.38)],
        [(size*0.5, size*0.7), (size*0.78, size*0.52), (size*0.92, size*0.3)],
        [(size*0.48, size*0.4), (size*0.3, size*0.22), (size*0.16, size*0.05)],
        [(size*0.5, size*0.4), (size*0.7, size*0.2), (size*0.86, size*0.06)],
    ]
    for b in branches:
        d.line(b, fill=(58, 87, 53, 255), width=2)
    for bx, by in [(size*0.1, size*0.38), (size*0.92, size*0.3), (size*0.16, size*0.05), (size*0.86, size*0.06)]:
        d.line([(bx, by), (bx - 2, by - 3)], fill=(214, 96, 156, 255), width=1)
    for y in range(size):
        for x in range(size):
            a = px[x, y][3]
            if a == 0:
                continue
            c = px[x, y][:3]
            c = jitter(c, 8, x, y, seed)
            px[x, y] = c + (255,)
    save(im, BLOCK, 'lotus_roots')

def make_lotus_leaves(size=32, seed=101):
    im = Image.new('RGBA', (size, size), (0, 0, 0, 255))
    px = im.load()
    dark = (17, 73, 54)
    base = (35, 132, 70)
    light = (99, 210, 119)
    for y in range(size):
        for x in range(size):
            n = _hash01(x, y, seed)
            t = _hash01(x // 3, y // 3, seed + 3)
            c = lerp_color(dark, base, 0.35 + t * 0.5)
            if n > 0.88:
                c = lerp_color(c, light, 0.8)
            elif n < 0.04:
                c = (215, 92, 152)  # infected berry fleck
            c = jitter(c, 5, x, y, seed)
            px[x, y] = c + (255,)
    save(im, BLOCK, 'lotus_leaves')

def make_blossom_grass(size=32, seed=111):
    im = new('blossom_grass', (size, size))
    d = ImageDraw.Draw(im)
    blades = [
        ([(4,31),(8,7),(11,31)], (49,165,80)),
        ([(10,31),(15,3),(17,31)], (95,206,91)),
        ([(16,31),(22,11),(24,31)], (44,135,72)),
        ([(21,31),(27,16),(29,31)], (223,66,128)),
    ]
    scale = size / 32
    for points, base_c in blades:
        pts = [(x*scale, y*scale) for x, y in points]
        d.polygon(pts, fill=base_c + (255,))
    d.line((3*scale, 30*scale, 29*scale, 30*scale), fill=(24,68,50,255), width=max(1,int(2*scale)))
    px = im.load()
    for y in range(size):
        for x in range(size):
            a = px[x, y][3]
            if a == 0:
                continue
            c = px[x, y][:3]
            t = 1 - (y / size)
            c = lerp_color(c, tuple(min(255, v+40) for v in c), t*0.35)
            c = jitter(c, 6, x, y, seed)
            px[x, y] = c + (255,)
    save(im, BLOCK, 'blossom_grass')

def make_lotus_heart(size=32, seed=121):
    im = Image.new('RGBA', (size, size), (0, 0, 0, 255))
    px = im.load()
    cx, cy = size/2, size/2
    core = (255, 120, 190)
    mid = (196, 40, 122)
    edge = (74, 18, 54)
    for y in range(size):
        for x in range(size):
            dist = math.hypot(x-cx, y-cy) / (size*0.72)
            if dist < 0.35:
                c = lerp_color(core, mid, dist/0.35)
            else:
                c = lerp_color(mid, edge, min(1.0, (dist-0.35)/0.65))
            n = _hash01(x, y, seed)
            if n > 0.93:
                c = lerp_color(c, (255, 214, 235), 0.6)  # vein glint
            c = jitter(c, 5, x, y, seed)
            px[x, y] = c + (255,)
    # bright pulsing centre highlight
    d = ImageDraw.Draw(im)
    d.ellipse((cx-3, cy-3, cx+3, cy+3), fill=(255, 226, 240, 255))
    save(im, BLOCK, 'lotus_heart')

# ---------------------------------------------------------------------------
# Legacy simple blocks kept at their existing resolution/style (unchanged
# scope for this pass) — noise-shaded materials.
# ---------------------------------------------------------------------------

def material(name, base, shadow, hi, accents):
    im=Image.new('RGBA',(16,16),base+(255,)); p=im.load()
    for y in range(16):
        for x in range(16):
            if (x+y*3)%17==0: p[x,y]=shadow+(255,)
            elif (x*5+y)%23==0: p[x,y]=hi+(255,)
    for x,y,c in accents: p[x,y]=c+(255,)
    save(im,BLOCK,name)
material('blessing_nodule',(173,196,190),(78,105,115),(225,239,210),[(3,4,(201,64,87)),(4,5,(201,64,87)),(5,6,(128,39,74)),(10,8,(89,215,187)),(11,9,(89,215,187))])
material('blessing_soil',(99,106,109),(53,61,70),(153,147,133),[(2,12,(152,79,89)),(11,3,(188,193,166))])
material('blessing_log',(70,75,85),(36,40,53),(174,193,177),[(5,3,(130,164,169)),(10,12,(58,65,82))])
material('blessing_leaves',(102,130,137),(55,77,97),(204,228,195),[(3,8,(189,228,203)),(12,5,(67,103,123))])
material('infected_water_still',(31,121,112),(12,65,81),(83,204,168),[(3,5,(226,83,161)),(4,5,(226,83,161)),(12,11,(73,192,183))])
material('infected_water_flow',(25,105,106),(9,54,73),(80,190,170),[(1,5,(231,83,165)),(6,10,(231,83,165)),(11,4,(231,83,165))])

im=new('glow_berries'); d=ImageDraw.Draw(im)
d.line((7,15,8,5,13,2), fill=(41,87,55,255), width=2); d.line((8,8,3,5), fill=(51,117,64,255), width=1); d.line((9,9,14,7), fill=(51,117,64,255), width=1)
for x,y in [(3,5),(13,2),(14,7),(8,4)]: d.rectangle((x-1,y-1,x+1,y+1), fill=(189,232,104,255)); d.point((x-1,y-1),fill=(239,255,175,255))
save(im,BLOCK,'glow_berries')

im=new('infected_water_bucket'); d=ImageDraw.Draw(im)
d.rectangle((3,5,12,13), fill=(49,112,123,255)); d.rectangle((5,7,10,12), fill=(38,147,128,255)); d.line((5,8,10,8), fill=(230,83,163,255), width=1); d.arc((2,1,13,9),180,350,fill=(188,193,189,255),width=1); save(im,ITEM,'infected_water_bucket')

# ---------------------------------------------------------------------------
# Run the upgraded 32x32 generators
# ---------------------------------------------------------------------------

if __name__ == '__main__':
    make_infected_lotus()
    make_lotus_log()
    make_lotus_petal()
    make_lotus_leaf()
    make_lotus_stem()
    make_infected_soil()
    make_lotus_roots()
    make_lotus_leaves()
    make_blossom_grass()
    make_lotus_heart()
    print('make_vanilla_assets.py: wrote upgraded 32x32 textures to', BLOCK)
