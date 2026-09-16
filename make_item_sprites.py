from pathlib import Path
from PIL import Image, ImageDraw
root=Path(__file__).resolve().parent / 'src' / 'main' / 'resources' / 'assets' / 'lotusblight' / 'textures' / 'item'
root.mkdir(parents=True,exist_ok=True)
im=Image.new('RGBA',(16,16),(0,0,0,0)); d=ImageDraw.Draw(im)
d.line((3,13,8,7,13,10),fill=(35,91,54,255),width=2)
for x,y in [(4,11),(8,6),(12,10)]:
    d.rectangle((x-2,y-2,x+2,y+2),fill=(133,184,77,255),outline=(55,116,63,255))
    d.point((x-1,y-1),fill=(238,255,170,255))
im.save(root/'glowing_berry.png')
