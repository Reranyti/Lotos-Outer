from pathlib import Path
from PIL import Image, ImageDraw

root = Path('/home/ubuntu/lotus-forge-mod/src/main/resources/assets/lotusblight/textures')
(root / 'block').mkdir(parents=True, exist_ok=True)
(root / 'item').mkdir(parents=True, exist_ok=True)

def texture(name, base, accent=None, pattern='noise'):
    size = 16
    img = Image.new('RGBA', (size, size), base + (255,))
    px = img.load()
    for y in range(size):
        for x in range(size):
            if pattern == 'noise' and (x * 7 + y * 11) % 9 == 0:
                px[x, y] = tuple(max(0, min(255, c + (12 if (x + y) % 2 else -10))) for c in base) + (255,)
            if accent and (x + y * 3) % 13 == 0:
                px[x, y] = accent + (255,)
    img.save(root / 'block' / f'{name}.png')

texture('infected_soil', (42, 126, 74), (75, 190, 100))
texture('infected_water', (38, 145, 120), (87, 218, 163))
texture('lotus_roots', (30, 105, 55), (247, 102, 157))
texture('infected_lotus', (49, 165, 89), (255, 133, 190))
texture('lotus_heart', (76, 180, 105), (255, 92, 166))

# simple item icons, matching the block palette
for name, base, accent in [
    ('lotus_seed', (60, 155, 83), (255, 125, 185)),
    ('lotus_map', (61, 170, 125), (255, 110, 170)),
    ('cleansing_powder', (232, 245, 205), (255, 135, 188)),
]:
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((3, 3, 12, 12), fill=base + (255,))
    d.rectangle((7, 1, 8, 14), fill=accent + (255,))
    d.rectangle((1, 7, 14, 8), fill=accent + (255,))
    img.save(root / 'item' / f'{name}.png')

import json
assets = Path('/home/ubuntu/lotus-forge-mod/src/main/resources/assets/lotusblight')
for name in ['infected_lotus', 'infected_water', 'infected_soil', 'lotus_roots', 'lotus_heart']:
    (assets / 'blockstates' / f'{name}.json').parent.mkdir(parents=True, exist_ok=True)
    (assets / 'blockstates' / f'{name}.json').write_text(json.dumps({'variants': {'': {'model': f'lotusblight:block/{name}'}}}, indent=2), encoding='utf-8')
    model = {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'lotusblight:block/{name}'}}
    (assets / 'models' / 'block' / f'{name}.json').parent.mkdir(parents=True, exist_ok=True)
    (assets / 'models' / 'block' / f'{name}.json').write_text(json.dumps(model, indent=2), encoding='utf-8')

for name in ['infected_lotus', 'infected_water', 'infected_soil', 'lotus_roots', 'lotus_heart']:
    item = {'parent': f'lotusblight:block/{name}'}
    (assets / 'models' / 'item' / f'{name}.json').parent.mkdir(parents=True, exist_ok=True)
    (assets / 'models' / 'item' / f'{name}.json').write_text(json.dumps(item, indent=2), encoding='utf-8')
for name in ['lotus_seed', 'lotus_map', 'cleansing_powder']:
    item = {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'lotusblight:item/{name}'}}
    (assets / 'models' / 'item' / f'{name}.json').write_text(json.dumps(item, indent=2), encoding='utf-8')

# Second-pass 32x32 textures for the detailed alpha palette.
def texture32(name, base, accents):
    img = Image.new('RGBA', (32, 32), base + (255,))
    px = img.load()
    for y in range(32):
        for x in range(32):
            h = (x * 17 + y * 29 + (x ^ y) * 3) % 37
            if h < 5:
                c = accents[h % len(accents)]
                px[x, y] = c + (255,)
            elif h == 18:
                px[x, y] = tuple(min(255, max(0, c + 14)) for c in base) + (255,)
    img.save(root / 'block' / f'{name}.png')

texture32('lotus_stem', (49, 139, 70), [(80, 190, 94), (34, 91, 52)])
texture32('lotus_leaf', (26, 151, 85), [(72, 214, 120), (17, 90, 63), (108, 237, 146)])
texture32('lotus_petal', (238, 83, 145), [(255, 164, 207), (194, 36, 112), (255, 207, 227)])
texture32('lotus_center', (255, 171, 58), [(255, 232, 111), (222, 94, 40)])
texture32('blessing_nodule', (190, 218, 216), [(109, 246, 207), (218, 91, 121), (237, 245, 231)])
texture32('blessing_soil', (118, 128, 132), [(185, 194, 187), (71, 83, 91), (155, 93, 102)])
texture32('blossom_grass', (51, 133, 72), [(210, 55, 72), (103, 197, 75), (235, 77, 90)])
texture32('glow_berries', (55, 107, 75), [(232, 242, 125), (186, 255, 146), (245, 130, 169)])
texture32('lotus_log', (87, 123, 77), [(145, 194, 101), (55, 92, 70)])
texture32('lotus_leaves', (39, 142, 74), [(82, 213, 112), (27, 91, 63)])
texture32('blessing_log', (75, 81, 93), [(184, 208, 191), (122, 169, 182), (49, 57, 73)])
texture32('blessing_leaves', (113, 144, 153), [(199, 242, 214), (81, 109, 135)])
texture32('infected_water_still', (50, 165, 137), [(91, 236, 181), (231, 91, 169), (24, 112, 121)])
texture32('infected_water_flow', (39, 141, 129), [(104, 236, 191), (223, 81, 163), (21, 96, 112)])
