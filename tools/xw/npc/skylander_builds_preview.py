from PIL import Image, ImageDraw
C={'pillar':(235,230,220),'quartz':(240,236,228),'calcite':(225,228,226),'glass':(140,200,240),'slab':(230,226,218),'ore':(90,200,240),
'lantern':(90,210,230),'stairs':(228,224,214),'flower':(120,140,240),'flower2':(200,120,220),'azalea':(90,160,70),'table':(150,100,60),'grass':(100,170,70)}
def shrine():
    b=[]
    for dx in(-1,0,1):
        for dz in(-1,0,1): b.append((dx,-1,dz,'quartz'))
    for x,z in((-1,-1),(1,-1),(-1,1),(1,1)): b+= [(x,0,z,'pillar'),(x,1,z,'stairs')]
    b+=[(0,0,0,'calcite'),(0,1,0,'ore'),(0,2,0,'lantern')]; return b
def lookout():
    b=[(0,y,0,'pillar') for y in range(5)]+[(dx,5,dz,'slab') for dx,dz in((1,0),(-1,0),(0,1),(0,-1))]+[(0,5,0,'quartz'),(0,6,0,'lantern')]
    b+=[(dx,0,dz,'stairs') for dx,dz in((1,0),(-1,0),(0,1),(0,-1))]; return b
def garden():
    b=[]
    for dx in range(-2,3):
        for dz in range(-2,3):
            if abs(dx)==2 or abs(dz)==2: b.append((dx,0,dz,'calcite'))
            elif dx or dz: b.append((dx,0,dz,'flower' if (dx+dz)%2 else 'flower2'))
    b.append((0,0,0,'azalea')); b.append((0,1,0,'azalea')); return b
def home():
    b=[(dx,-1,dz,'quartz') for dx in range(-2,3) for dz in range(-2,3)]
    for y in range(3):
        for dx in range(-2,3):
            for dz in range(-2,3):
                if abs(dx)!=2 and abs(dz)!=2: continue
                corner=abs(dx)==2 and abs(dz)==2; door=dz==2 and dx==0 and y<2; win=y==1 and not corner and (dx==0 or dz==0)
                if door: continue
                b.append((dx,y,dz,'pillar' if corner else 'glass' if win else 'calcite'))
    b+=[(dx,3,dz,'glass' if abs(dx)<=1 and abs(dz)<=1 else 'quartz') for dx in range(-2,3) for dz in range(-2,3)]
    b+=[(dx,4,dz,'slab') for dx in(-1,0,1) for dz in(-1,0,1)]; return b
def draw(blocks,title):
    W,H=300,300; im=Image.new('RGB',(W,H),(150,200,240)); d=ImageDraw.Draw(im); s=14
    for dx in range(-4,5):
        for dz in range(-4,5): blocks=[(dx,-2,dz,'grass')]+blocks
    blocks=sorted(blocks,key=lambda b:(b[0]+b[2],b[1]))
    ox,oy=W//2,H//2+40
    for x,y,z,k in blocks:
        c=C[k]; px=ox+(x-z)*s; py=oy+(x+z)*s//2-y*s
        top=[(px,py-s//2),(px+s,py),(px,py+s//2),(px-s,py)]
        l=[(px-s,py),(px,py+s//2),(px,py+s*3//2),(px-s,py+s)]
        r=[(px+s,py),(px,py+s//2),(px,py+s*3//2),(px+s,py+s)]
        d.polygon(top,fill=c,outline=(60,60,60)); d.polygon(l,fill=tuple(int(v*.75) for v in c),outline=(60,60,60)); d.polygon(r,fill=tuple(int(v*.6) for v in c),outline=(60,60,60))
    d.text((8,8),title,fill=(20,20,40)); return im
ims=[draw(shrine(),'Crystal shrine'),draw(lookout(),'Lookout tower'),draw(garden(),'Flower garden'),draw(home(),'Quartz home (if no house)')]
sheet=Image.new('RGB',(600,620),(30,30,40))
for i,im in enumerate(ims): sheet.paste(im,((i%2)*300,(i//2)*300))
ImageDraw.Draw(sheet).text((8,604),"Island Skylanders: dig Sky Ore (refilled w/ stone), plant trees/flowers, forge sky sword/boots/helmet, then build one of these",fill=(230,230,230))
sheet.save('/mnt/user-data/outputs/skylander_builds.png')
