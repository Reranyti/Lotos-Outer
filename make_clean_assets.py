from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path('/home/ubuntu/lotus-forge-mod/src/main/resources/assets/lotusblight/textures')
BLOCK = ROOT / 'block'
ITEM = ROOT / 'item'
BLOCK.mkdir(parents=True, exist_ok=True)
ITEM.mkdir(parents=True, exist_ok=True)

def rgba(name, size=(32, 32)):
    return Image.new('RGBA', size, (0, 0, 0, 0))

def save(img, path):
    img.save(path)

def solid_texture(name, base, dark, light, seed=0):
    img = Image.new('RGBA', (32, 32), base + (255,))
    p = img.load()
    for y in range(32):
        for x in range(32):
            v = (x * 11 + y * 7 + x * y + seed) % 19
            if v == 0:
                p[x, y] = dark + (255,)
            elif v == 1:
                p[x, y] = light + (255,)
    save(img, BLOCK / f'{name}.png')

solid_texture('blessing_nodule', (168, 205, 204), (77, 126, 140), (224, 247, 226), 3)
solid_texture('blessing_soil', (105, 112, 117), (56, 67, 76), (157, 146, 139), 7)
solid_texture('lotus_log', (83, 128, 75), (45, 78, 57), (146, 184, 94), 11)
solid_texture('lotus_leaves', (35, 132, 70), (17, 73, 54), (99, 210, 119), 13)
solid_texture('blessing_log', (72, 78, 89), (39, 44, 58), (164, 190, 183), 17)
solid_texture('blessing_leaves', (108, 137, 147), (57, 83, 105), (204, 237, 210), 19)
solid_texture('infected_water_still', (38, 151, 130), (17, 91, 112), (112, 239, 190), 23)
solid_texture('infected_water_flow', (33, 129, 125), (12, 73, 98), (236, 100, 183), 29)

# Clean transparent lotus textures for the low-poly model.
petal = rgba('lotus_petal')
d = ImageDraw.Draw(petal)
d.polygon([(16, 2), (22, 8), (24, 18), (20, 28), (16, 31), (12, 28), (8, 18), (10, 8)], fill=(246, 92, 159, 255))
d.polygon([(16, 5), (19, 10), (20, 20), (16, 27), (13, 20), (13, 10)], fill=(255, 177, 211, 255))
d.line([(16, 4), (16, 27)], fill=(198, 41, 118, 255), width=2)
save(petal, BLOCK / 'lotus_petal.png')

leaf = rgba('lotus_leaf')
d = ImageDraw.Draw(leaf)
d.ellipse((2, 5, 29, 27), fill=(29, 154, 86, 255), outline=(12, 88, 61, 255), width=2)
d.line([(16, 16), (29, 7)], fill=(113, 225, 124, 255), width=2)
d.line([(16, 16), (6, 8)], fill=(75, 204, 104, 255), width=1)
d.line([(16, 16), (8, 25)], fill=(16, 103, 67, 255), width=1)
save(leaf, BLOCK / 'lotus_leaf.png')

stem = rgba('lotus_stem')
d = ImageDraw.Draw(stem)
d.rectangle((12, 2, 19, 30), fill=(49, 139, 70, 255))
d.rectangle((14, 2, 16, 30), fill=(100, 204, 96, 255))
save(stem, BLOCK / 'lotus_stem.png')

center = rgba('lotus_center')
d = ImageDraw.Draw(center)
d.ellipse((5, 5, 26, 26), fill=(244, 155, 47, 255), outline=(177, 75, 35, 255), width=2)
for x, y in [(10, 11), (16, 9), (21, 12), (12, 18), (18, 19)]:
    d.ellipse((x, y, x + 3, y + 3), fill=(255, 225, 95, 255))
save(center, BLOCK / 'lotus_center.png')

# Transparent grass: green blades with a controlled red accent.
grass = rgba('blossom_grass')
d = ImageDraw.Draw(grass)
for points, color in [([(4,30),(8,7),(11,30)], (49, 165, 80, 255)), ([(10,30),(15,3),(17,30)], (95, 206, 91, 255)), ([(16,30),(22,11),(24,30)], (44, 135, 72, 255)), ([(21,30),(27,16),(29,30)], (202, 51, 75, 255))]:
    d.polygon(points, fill=color)
d.line((3, 30, 29, 30), fill=(31, 77, 55, 255), width=2)
save(grass, BLOCK / 'blossom_grass.png')

# Transparent berry shrub with bright, readable berries.
berries = rgba('glow_berries')
d = ImageDraw.Draw(berries)
d.line((7, 30, 15, 13, 25, 28), fill=(50, 102, 68, 255), width=3)
d.line((15, 18, 10, 9), fill=(79, 140, 76, 255), width=2)
d.line((15, 18, 23, 10), fill=(79, 140, 76, 255), width=2)
for x, y in [(8, 8), (21, 9), (13, 15), (22, 22), (7, 23)]:
    d.ellipse((x-3, y-3, x+3, y+3), fill=(215, 255, 130, 255), outline=(123, 206, 103, 255), width=1)
    d.point((x-1, y-1), fill=(255, 255, 220, 255))
save(berries, BLOCK / 'glow_berries.png')

# Item bucket uses a clean miniature of the fluid palette.
bucket = rgba('infected_water_bucket', (16, 16))
d = ImageDraw.Draw(bucket)
d.polygon([(3, 5), (13, 5), (12, 13), (4, 13)], fill=(91, 182, 177, 255), outline=(35, 78, 86, 255))
d.rectangle((5, 7, 11, 11), fill=(40, 161, 135, 255))
d.line((5, 8, 11, 8), fill=(237, 103, 184, 255), width=1)
d.arc((3, 1, 13, 9), 180, 350, fill=(180, 188, 190, 255), width=1)
save(bucket, ITEM / 'infected_water_bucket.png')
