from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path('/home/ubuntu/lotus-forge-mod/src/main/resources/assets/lotusblight/textures')
BLOCK = ROOT / 'block'
ITEM = ROOT / 'item'
BLOCK.mkdir(parents=True, exist_ok=True)
ITEM.mkdir(parents=True, exist_ok=True)

def new(name, size=(16,16)):
    return Image.new('RGBA', size, (0,0,0,0))

def save(im, where, name):
    im.save(where / f'{name}.png')

# Flower petal: deliberate 16x16 stepped silhouette, dark underside, pale highlight.
im = new('lotus_petal'); p=im.load()
rows={2:(7,8),3:(5,10),4:(4,11),5:(3,12),6:(2,13),7:(2,13),8:(2,13),9:(3,12),10:(4,11),11:(5,10),12:(6,9),13:(7,8)}
for y,(a,b) in rows.items():
    for x in range(a,b+1):
        p[x,y]=(218,45,116,255) if y>8 else (244,91,157,255)
for x,y in [(7,3),(8,3),(6,4),(7,4),(8,4),(9,4),(5,5),(6,5),(7,5),(8,5),(9,5),(10,5)]: p[x,y]=(255,170,205,255)
for x,y in [(3,7),(4,8),(5,9),(6,10),(7,11),(8,11),(9,10),(10,9),(11,8),(12,7)]: p[x,y]=(167,31,91,255)
save(im,BLOCK,'lotus_petal')

# Leaf: pixel ellipse, vein, directional shading.
im=new('lotus_leaf'); p=im.load()
for y in range(3,14):
    width = 1 + min(y-2, 14-y)
    for x in range(8-width, 8+width+1): p[x,y]=(22,112,62,255) if y>8 else (35,153,79,255)
for x,y in [(8,3),(8,4),(8,5),(8,6),(8,7),(8,8),(8,9),(8,10),(8,11),(8,12),(8,13)]: p[x,y]=(98,201,103,255)
for x,y in [(4,7),(5,7),(6,7),(10,7),(11,7),(12,7),(5,10),(6,10),(10,10),(11,10)]: p[x,y]=(54,181,87,255)
for x,y in [(2,8),(3,8),(4,8),(12,8),(13,8)]: p[x,y]=(10,77,48,255)
save(im,BLOCK,'lotus_leaf')

im=new('lotus_stem'); p=im.load()
for y in range(1,16):
    p[6,y]=(25,93,51,255); p[7,y]=(57,161,78,255); p[8,y]=(90,195,92,255); p[9,y]=(36,121,61,255)
save(im,BLOCK,'lotus_stem')

im=new('lotus_center'); d=ImageDraw.Draw(im)
d.rectangle((4,4,11,11), fill=(193,85,35,255)); d.rectangle((5,3,10,12), fill=(230,151,46,255)); d.rectangle((6,5,9,10), fill=(255,214,82,255))
for x,y in [(5,5),(8,4),(10,6),(6,9),(9,9)]: d.point((x,y), fill=(174,69,35,255))
save(im,BLOCK,'lotus_center')

# Materials with clear top/side shading.
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
material('lotus_log',(78,123,74),(38,69,54),(153,181,91),[(4,2,(112,157,81)),(12,13,(48,83,60))])
material('lotus_leaves',(30,122,66),(12,70,48),(94,201,107),[(4,6,(111,220,121)),(12,3,(18,92,58))])
material('blessing_log',(70,75,85),(36,40,53),(174,193,177),[(5,3,(130,164,169)),(10,12,(58,65,82))])
material('blessing_leaves',(102,130,137),(55,77,97),(204,228,195),[(3,8,(189,228,203)),(12,5,(67,103,123))])
material('infected_water_still',(31,121,112),(12,65,81),(83,204,168),[(3,5,(226,83,161)),(4,5,(226,83,161)),(12,11,(73,192,183))])
material('infected_water_flow',(25,105,106),(9,54,73),(80,190,170),[(1,5,(231,83,165)),(6,10,(231,83,165)),(11,4,(231,83,165))])

# Transparent grass and berry bush.
im=new('blossom_grass'); d=ImageDraw.Draw(im)
d.polygon([(1,15),(3,5),(5,15)], fill=(38,129,67,255)); d.polygon([(5,15),(8,2),(10,15)], fill=(72,174,77,255)); d.polygon([(9,15),(12,6),(13,15)], fill=(184,44,65,255)); d.polygon([(12,15),(15,4),(15,15)], fill=(38,112,63,255)); d.line((0,15,15,15),fill=(22,68,50,255),width=1); save(im,BLOCK,'blossom_grass')

im=new('glow_berries'); d=ImageDraw.Draw(im)
d.line((7,15,8,5,13,2), fill=(41,87,55,255), width=2); d.line((8,8,3,5), fill=(51,117,64,255), width=1); d.line((9,9,14,7), fill=(51,117,64,255), width=1)
for x,y in [(3,5),(13,2),(14,7),(8,4)]: d.rectangle((x-1,y-1,x+1,y+1), fill=(189,232,104,255)); d.point((x-1,y-1),fill=(239,255,175,255))
save(im,BLOCK,'glow_berries')

im=new('infected_water_bucket'); d=ImageDraw.Draw(im)
d.rectangle((3,5,12,13), fill=(49,112,123,255)); d.rectangle((5,7,10,12), fill=(38,147,128,255)); d.line((5,8,10,8), fill=(230,83,163,255), width=1); d.arc((2,1,13,9),180,350,fill=(188,193,189,255),width=1); save(im,ITEM,'infected_water_bucket')
