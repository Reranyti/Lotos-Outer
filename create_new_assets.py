from pathlib import Path
import json
root = Path('/home/ubuntu/lotus-forge-mod/src/main/resources/assets/lotusblight')
blocks = {
    'blessing_nodule': 'blessing_nodule',
    'blessing_soil': 'blessing_soil',
    'blossom_grass': 'blossom_grass',
    'glow_berries': 'glow_berries',
    'lotus_log': 'lotus_log',
    'lotus_leaves': 'lotus_leaves',
    'blessing_log': 'blessing_log',
    'blessing_leaves': 'blessing_leaves',
}
for name, texture in blocks.items():
    (root / 'blockstates').mkdir(parents=True, exist_ok=True)
    (root / 'models' / 'block').mkdir(parents=True, exist_ok=True)
    (root / 'models' / 'item').mkdir(parents=True, exist_ok=True)
    (root / 'blockstates' / f'{name}.json').write_text(json.dumps({'variants': {'': {'model': f'lotusblight:block/{name}'}}}, indent=2), encoding='utf-8')
    (root / 'models' / 'block' / f'{name}.json').write_text(json.dumps({'parent':'minecraft:block/cube_all','textures':{'all':f'lotusblight:block/{texture}'}}, indent=2), encoding='utf-8')
    (root / 'models' / 'item' / f'{name}.json').write_text(json.dumps({'parent':f'lotusblight:block/{name}'}, indent=2), encoding='utf-8')
(root / 'blockstates' / 'infected_water.json').write_text(json.dumps({'variants': {'': {'model': 'minecraft:block/water'}}}, indent=2), encoding='utf-8')
(root / 'models' / 'item' / 'infected_water_bucket.json').write_text(json.dumps({'parent':'minecraft:item/generated','textures':{'layer0':'lotusblight:item/infected_water_bucket'}}, indent=2), encoding='utf-8')
