from pathlib import Path
from PIL import Image, ImageDraw
out = Path('/home/ubuntu/lotus-forge-mod/src/main/resources/assets/lotusblight/textures/block/lotus_center.png')
sheet = Image.new('RGBA', (16, 32), (0, 0, 0, 0))
for frame, bright in enumerate([(225, 130, 38), (255, 190, 62)]):
    y0 = frame * 16
    d = ImageDraw.Draw(sheet)
    d.rectangle((4, y0+4, 11, y0+11), fill=(177, 73, 34, 255))
    d.rectangle((5, y0+3, 10, y0+12), fill=bright + (255,))
    d.rectangle((6, y0+5, 9, y0+10), fill=(255, 224, 104, 255))
    for x, y in [(5,5),(8,4),(10,6),(6,9),(9,9)]:
        d.point((x, y0+y), fill=(154, 57, 32, 255))
sheet.save(out)
