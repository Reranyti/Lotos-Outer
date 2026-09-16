from pathlib import Path
from PIL import Image

root = Path('/home/ubuntu/lotus-forge-mod')
assets = {
    'lotus_ore_reference.png': ('src/main/resources/assets/lotusblight/textures/block/lotus_ore.png', False),
    'lotus_alloy_asset.png': ('src/main/resources/assets/lotusblight/textures/item/lotus_alloy.png', True),
    'lotus_pickaxe_asset.png': ('src/main/resources/assets/lotusblight/textures/item/lotus_pickaxe.png', True),
    'lotus_roots_asset.png': ('src/main/resources/assets/lotusblight/textures/block/lotus_roots.png', True),
    'lotus_log_asset.png': ('src/main/resources/assets/lotusblight/textures/block/lotus_log.png', False),
    'lotus_leaves_asset.png': ('src/main/resources/assets/lotusblight/textures/block/lotus_leaves.png', False),
}
for source, (target, alpha) in assets.items():
    image = Image.open(root / source).convert('RGBA')
    if alpha:
        pixels = image.load()
        for y in range(image.height):
            for x in range(image.width):
                r, g, b, a = pixels[x, y]
                if r > 238 and g > 238 and b > 238:
                    pixels[x, y] = (r, g, b, 0)
    image = image.resize((16, 16), Image.Resampling.NEAREST)
    destination = root / target
    destination.parent.mkdir(parents=True, exist_ok=True)
    image.save(destination)
