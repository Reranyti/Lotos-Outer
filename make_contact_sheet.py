from pathlib import Path
from PIL import Image, ImageDraw
root = Path('/home/ubuntu/lotus-forge-mod/src/main/resources/assets/lotusblight/textures/block')
names = ['lotus_petal','lotus_leaf','lotus_stem','lotus_center','blossom_grass','glow_berries','blessing_nodule','blessing_soil','lotus_log','blessing_log','infected_water_still','infected_water_flow']
cell = 128
sheet = Image.new('RGB', (cell*4, cell*3), (38, 38, 42))
draw = ImageDraw.Draw(sheet)
for i, name in enumerate(names):
    img = Image.open(root / f'{name}.png').convert('RGBA')
    bg = Image.new('RGBA', (cell, cell), (70, 70, 76, 255))
    for y in range(0, cell, 16):
        for x in range(0, cell, 16):
            if (x//16 + y//16) % 2 == 0:
                draw.rectangle((i%4*cell+x, i//4*cell+y, i%4*cell+x+15, i//4*cell+y+15), fill=(82,82,88))
            else:
                draw.rectangle((i%4*cell+x, i//4*cell+y, i%4*cell+x+15, i//4*cell+y+15), fill=(62,62,68))
    img = img.resize((96,96), Image.Resampling.NEAREST)
    sheet.alpha_composite(img, (i%4*cell+16, i//4*cell+12)) if sheet.mode == 'RGBA' else sheet.paste(img, (i%4*cell+16, i//4*cell+12), img)
    draw.text((i%4*cell+5, i//4*cell+108), name, fill='white')
sheet.save('/home/ubuntu/lotus-forge-mod/texture_contact_sheet.png')
