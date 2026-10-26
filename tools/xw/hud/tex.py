from PIL import Image, ImageDraw
import os
OUT='/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/gui/hud/'
os.makedirs(OUT, exist_ok=True)
COL={'mikiko':(235,70,60),'rhalix':(170,90,255),'ultima':(31,224,122)}
N=128; K=4
for ch in ('mikiko','rhalix','ultima'):
    for ex in ('', '_blink', '_hurt', '_damage1', '_exhausted', '_shout', '_rage'):
        big=Image.new('RGBA',(N*K,N*K),(0,0,0,0)); d=ImageDraw.Draw(big)
        d.ellipse((0,0,N*K-1,N*K-1),fill=(12,12,20,255))
        d.ellipse((5*K,5*K,(N-5)*K,(N-5)*K),fill=COL[ch]+(255,))
        d.ellipse((11*K,11*K,(N-11)*K,(N-11)*K),fill=(30,34,52,255))
        p=Image.open(f'/tmp/portrait_{ch}{ex}.png').convert('RGBA')
        R=(N-22)*K/2
        s=(2*R*1.18)/p.width; p=p.resize((int(p.width*s),int(p.height*s)),Image.NEAREST)
        layer=Image.new('RGBA',big.size,(0,0,0,0)); layer.paste(p,(int(N*K/2-p.width/2),int(N*K/2-p.height*0.6)),p)
        mask=Image.new('L',big.size,0); ImageDraw.Draw(mask).ellipse((11*K,11*K,(N-11)*K,(N-11)*K),fill=255)
        la=layer.split()[3]; from PIL import ImageChops; la=ImageChops.multiply(la,mask); layer.putalpha(la)
        big.alpha_composite(layer)
        name=ex[1:] if ex else 'normal'
        big.resize((N,N),Image.LANCZOS).save(OUT+f'portrait_{ch}_{name}.png')
# level badge and KA orb
for name,fill,ring in (('badge',(255,205,70),(12,12,20)),('orb',(40,20,60),(160,90,255))):
    big=Image.new('RGBA',(32*K,32*K),(0,0,0,0)); d=ImageDraw.Draw(big)
    d.ellipse((0,0,32*K-1,32*K-1),fill=(12,12,20,255)); d.ellipse((3*K,3*K,29*K,29*K),fill=ring+(255,) if name=='orb' else fill+(255,))
    if name=='orb': d.ellipse((6*K,6*K,26*K,26*K),fill=fill+(255,))
    big.resize((32,32),Image.LANCZOS).save(OUT+name+'.png')
print(sorted(os.listdir(OUT)))
