from PIL import Image
from pathlib import Path

src = Path('/home/ubuntu/lotus-forge-mod/lotus_bloom_generated.png')
dst = Path('/home/ubuntu/lotus-forge-mod/src/main/resources/assets/lotusblight/textures/block/infected_lotus.png')
img = Image.open(src).convert('RGBA')
# Remove near-black background while preserving the dark green pad.
pixels = img.load()
for y in range(img.height):
    for x in range(img.width):
        r, g, b, a = pixels[x, y]
        if r < 8 and g < 8 and b < 8:
            pixels[x, y] = (r, g, b, 0)
# Pixel-art downscale: nearest keeps hard Minecraft-style edges.
img = img.resize((16, 16), Image.Resampling.NEAREST)
dst.parent.mkdir(parents=True, exist_ok=True)
img.save(dst)
