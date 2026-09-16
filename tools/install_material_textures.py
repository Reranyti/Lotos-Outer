from pathlib import Path
from PIL import Image

root = Path('/home/ubuntu/lotus-forge-mod')
source_to_target = {
    'lotus_stone_texture.png': 'src/main/resources/assets/lotusblight/textures/block/lotus_stone.png',
    'lotus_sand_texture.png': 'src/main/resources/assets/lotusblight/textures/block/lotus_sand.png',
    'lotus_terracotta_texture.png': 'src/main/resources/assets/lotusblight/textures/block/lotus_terracotta.png',
    'lotus_gravel_texture.png': 'src/main/resources/assets/lotusblight/textures/block/lotus_gravel.png',
    'lotus_dirt_texture.png': 'src/main/resources/assets/lotusblight/textures/block/lotus_dirt.png',
}
for source, target in source_to_target.items():
    image = Image.open(root / source).convert('RGBA').resize((16, 16), Image.Resampling.NEAREST)
    destination = root / target
    destination.parent.mkdir(parents=True, exist_ok=True)
    image.save(destination)
