from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math, random
F='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'; FB='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
def f(s,b=False): return ImageFont.truetype(FB if b else F, s)
K=2  # supersample
W,H=1280,720
COL={'mikiko':(235,70,60),'rhalix':(170,90,255)}
SKILLS={'mikiko':[('Hekublade Scatter',30,0),('Kitsune Attack',35,.6),('Netsu',30,0),('Fire Blitz',25,0)],'rhalix':[('Kishi',30,0),('Earth Wave',25,.3),('Gravity Compress',35,0),('KA Release',30,0)]}
ULT={'mikiko':'Blazing Wind','rhalix':'Gravity Barrage'}

def scene():
    im=Image.new('RGB',(W*K,H*K)); d=ImageDraw.Draw(im)
    for y in range(H*K):
        t=y/(H*K); d.line([(0,y),(W*K,y)],fill=(int(120+60*t),int(170+50*t),int(235+10*t)))
    gy=int(H*K*0.62); d.rectangle((0,gy,W*K,H*K),fill=(95,150,62))
    rnd=random.Random(3)
    for i in range(400):
        x=rnd.randrange(0,W*K); y=rnd.randrange(gy,H*K); s=rnd.randrange(6,20)
        d.rectangle((x,y,x+s,y+s),fill=(rnd.randrange(80,110),rnd.randrange(135,165),rnd.randrange(50,72)))
    for tx in (180,980,1150):
        tx*=K; d.rectangle((tx,gy-260*K//2,tx+28*K,gy),fill=(105,75,45)); d.rectangle((tx-90*K,gy-430*K//2,tx+118*K,gy-240*K//2),fill=(55,120,50))
    # an enemy NPC in the distance
    d.rectangle((760*K,gy-110*K,790*K,gy-70*K),fill=(200,160,130)); d.rectangle((752*K,gy-70*K,798*K,gy-20*K),fill=(70,90,160)); d.rectangle((755*K,gy-20*K,795*K,gy),fill=(50,50,70))
    return im

def rr(d,box,r,fill,outline=None,w=1): d.rounded_rectangle([v*K for v in box],r*K,fill=fill,outline=outline,width=w*K)
def text(d,xy,s,size,fill=(255,255,255),b=False,anchor='la',stroke=2):
    d.text((xy[0]*K,xy[1]*K),s,font=f(size*K,b),fill=fill,anchor=anchor,stroke_width=stroke*K,stroke_fill=(10,10,20))

def bar(d,x,y,w,h,frac,col,back=(25,25,35),chip=None,label=None,val=None,slant=10):
    # KH style slanted bar
    pts=lambda xx,ww:[((xx+slant)*K,y*K),((xx+ww+slant)*K,y*K),((xx+ww)*K,(y+h)*K),(xx*K,(y+h)*K)]
    d.polygon(pts(x-3,w+6),fill=(10,10,16)); d.polygon(pts(x,w),fill=back)
    if chip: d.polygon(pts(x+w-int(w*chip),int(w*chip)),fill=(255,230,120))
    d.polygon(pts(x+w-int(w*frac),int(w*frac)),fill=col)
    hl=tuple(min(255,c+60) for c in col)
    d.polygon([((x+w-int(w*frac)+slant)*K,y*K),((x+w+slant)*K,y*K),((x+w+slant-1)*K,(y+h*0.35)*K),((x+w-int(w*frac)+slant-1)*K,(y+h*0.35)*K)],fill=hl)
    if label: text(d,(x-8,y+h/2),label,12,b=True,anchor='rm',fill=(230,230,240))
    if val: text(d,(x+w-6,y+h/2),val,11,b=True,anchor='rm')

def portrait(im,cx,cy,r,ch,face,ring,low=False,lvl=42,talk=False):
    d=ImageDraw.Draw(im)
    R=r*K
    d.ellipse((cx*K-R-8*K,cy*K-R-8*K,cx*K+R+8*K,cy*K+R+8*K),fill=(12,12,20))
    d.ellipse((cx*K-R-5*K,cy*K-R-5*K,cx*K+R+5*K,cy*K+R+5*K),fill=ring)
    inner=(60,20,20) if low else (30,34,52)
    d.ellipse((cx*K-R,cy*K-R,cx*K+R,cy*K+R),fill=inner)
    p=Image.open(f'/tmp/portrait_{ch}{face}.png').convert('RGBA')
    s=(2*R*1.18)/p.width; p=p.resize((int(p.width*s),int(p.height*s)),Image.NEAREST)
    mask=Image.new('L',im.size,0); ImageDraw.Draw(mask).ellipse((cx*K-R,cy*K-R,cx*K+R,cy*K+R),fill=255)
    layer=Image.new('RGBA',im.size,(0,0,0,0)); layer.paste(p,(int(cx*K-p.width/2),int(cy*K-p.height*0.6)),p)
    im.paste(layer,(0,0),Image.composite(layer,Image.new('RGBA',im.size,(0,0,0,0)),mask).split()[3])
    if low:
        ov=Image.new('RGBA',im.size,(0,0,0,0)); ImageDraw.Draw(ov).ellipse((cx*K-R,cy*K-R,cx*K+R,cy*K+R),fill=(255,0,0,60)); im.paste(ov,(0,0),ov)
    d=ImageDraw.Draw(im)
    # level badge
    if lvl is None: return
    d.ellipse(((cx-r-6)*K,(cy+r-26)*K,(cx-r+26)*K,(cy+r+6)*K),fill=(12,12,20)); d.ellipse(((cx-r-3)*K,(cy+r-23)*K,(cx-r+23)*K,(cy+r+3)*K),fill=(255,205,70))
    text(d,(cx-r+10,cy+r-10),str(lvl),11,fill=(20,15,10),b=True,anchor='mm',stroke=0)
    if talk:
        rr(d,(cx-r-70,cy-r-34,cx-r+30,cy-r-8),8,(250,250,255)); d.polygon([((cx-r+10)*K,(cy-r-9)*K),((cx-r+22)*K,(cy-r+4)*K),((cx-r+20)*K,(cy-r-9)*K)],fill=(250,250,255))
        text(d,(cx-r-20,cy-r-21),'"Let\'s go!"',11,fill=(20,20,30),b=True,anchor='mm',stroke=0)

def ka_orb(d,x,y,label='?',col=(160,90,255)):
    d.ellipse(((x-17)*K,(y-17)*K,(x+17)*K,(y+17)*K),fill=(12,12,20)); d.ellipse(((x-14)*K,(y-14)*K,(x+14)*K,(y+14)*K),fill=(40,20,60),outline=col,width=3*K)
    text(d,(x,y),label,13,fill=col,b=True,anchor='mm'); text(d,(x,y+24),'KA ^6',9,fill=(200,180,255),anchor='mm')

def gauge(im,ch,dbz=False,face='',low=False,talk=False,hp=.72,karma=.55):
    d=ImageDraw.Draw(im)
    cx,cy,r=W-82,H-92,56
    col=COL[ch]
    hpc=(220,60,50) if low else (70,215,90)
    bw=300; bx=cx-r-14-bw
    by=cy-r+18
    bar(d,bx,by,bw,16,hp if not low else .14,hpc,chip=(hp+.08) if not low else .2,label='HP',val=('112 / 156' if not low else '22 / 156'))
    bar(d,bx+22,by+22,bw-22,11,karma,(90,150,255),label='KARMA',val='55 / 100')
    y=by+39
    if dbz:
        bar(d,bx+40,y,bw-40,11,.8,(255,205,60),label='KI',val='2,410 / 3,000'); y+=17
        bar(d,bx+56,y,bw-56,7,.6,(140,230,120),label='STAMINA'); y+=13
    else:
        bar(d,bx+46,y,bw-46,6,.9,(240,170,80),label='FOOD'); y+=12
    portrait(im,cx,cy,r,ch,face,col,low=low,talk=talk)
    d=ImageDraw.Draw(im)
    # XP ring segment around the portrait (KH drive-gauge-ish)
    d.arc(((cx-r-11)*K,(cy-r-11)*K,(cx+r+11)*K,(cy+r+11)*K),200,200+int(140*.65),fill=(120,230,255),width=4*K)
    text(d,(cx+2,cy+r+12),ch.capitalize(),12,b=True,anchor='mm',fill=col)
    if dbz:
        # power release + form
        d.arc(((cx-r-17)*K,(cy-r-17)*K,(cx+r+17)*K,(cy+r+17)*K),-60,-60+int(120*.5),fill=(255,205,60),width=4*K)
        text(d,(cx+r+4,cy-r-14),'50%',10,b=True,anchor='lm',fill=(255,215,90))
        rr(d,(bx+120,by-22,bx+bw-10,by-6),5,(40,30,10),outline=(255,205,60))
        text(d,(bx+(120+bw-10)/2,by-14),'FORM: Base   ·   Power 50%',10,b=True,anchor='mm',fill=(255,225,130),stroke=0)
    # buffs
    ix=bx+10
    for c,t in (((255,90,40),'N'),((230,40,40),'MG')):
        d.ellipse((ix*K,(by-22)*K,(ix+16)*K,(by-6)*K),fill=c); text(d,(ix+8,by-14),t,8,b=True,anchor='mm',stroke=0); ix+=20
    ka_orb(d,cx-r-48,cy+r-8)

def command_menu(im,ch,highlight=1):
    d=ImageDraw.Draw(im)
    x,y=20,H-238
    rr(d,(x,y,x+220,y+26),6,(25,40,90),outline=(140,190,255),w=2)
    text(d,(x+12,y+13),'SKILLS',13,b=True,anchor='lm',fill=(200,225,255))
    text(d,(x+208,y+13),'Karma',9,anchor='rm',fill=(150,190,255),stroke=0)
    y+=30
    rows=SKILLS[ch]+[(ULT[ch]+'  ★',60,0)]
    for i,(name,cost,cd) in enumerate(rows):
        hl=i==highlight; ult=i==4
        fill=(30,30,46) if not hl else (60,90,170)
        rr(d,(x,y,x+220,y+26),5,fill,outline=(255,210,80) if ult else (90,110,160),w=2 if ult else 1)
        if cd: d.rectangle(((x+2)*K,(y+2)*K,(x+2+int(216*cd))*K,(y+24)*K),fill=(0,0,0))
        rr(d,(x+5,y+4,x+27,y+22),4,(15,15,25)); text(d,(x+16,y+13),'^'+str(i+1),9,b=True,anchor='mm',stroke=0,fill=(220,220,220))
        text(d,(x+34,y+13),name,12,b=hl,anchor='lm',fill=(255,240,200) if ult else (255,255,255))
        text(d,(x+212,y+13),str(cost) if not cd else '6s',11,b=True,anchor='rm',fill=(140,200,255) if not cd else (255,110,110))
        y+=30

def hotbar(im):
    d=ImageDraw.Draw(im); x=W//2-182; y=H-44
    rr(d,(x,y,x+364,y+40),3,(30,30,30),outline=(10,10,10),w=2)
    for i in range(9):
        d.rectangle(((x+4+i*40)*K,(y+4)*K,(x+40+i*40)*K,(y+36)*K),fill=(70,70,70) if i else (200,200,200))
    d.rectangle(((x+10)*K,(y+10)*K,(x+30)*K,(y+30)*K),fill=(200,40,40))  # hekublade-ish item

def party(im, compact=False):
    d=ImageDraw.Draw(im)
    mem=[('AmenXL','rhalix',.8,.6,'',False),('Kai','mikiko',.25,.3,'_hurt',False),('Nova','mikiko',0,0,'_exhausted',True)]
    x,y=W-250,16
    text(d,(x,y+6),'PARTY',12,b=True,fill=(200,225,255))
    y+=20
    for name,ch,hp,k,face,down in mem:
        rr(d,(x,y,x+234,y+52),10,(18,20,34),outline=COL[ch],w=2)
        # mini portrait
        r=21; cx,cy=x+28,y+26
        im2=im; portrait(im2,cx,cy,r,ch,face,COL[ch],low=hp<.3,lvl=None)
        d=ImageDraw.Draw(im)
        text(d,(x+56,y+11),name,12,b=True,anchor='lm'); text(d,(x+56+len(name)*8+6,y+11),'Lv '+('17' if ch=='mikiko' else '23'),9,anchor='lm',fill=(255,210,90))
        text(d,(x+228,y+11),('DOWN' if down else ch.capitalize()+'  12m'),10,b=True,anchor='rm',fill=(255,90,90) if down else (190,190,210))
        bar(d,x+58,y+22,164,10,hp,(220,60,50) if hp<.3 else (70,215,90),slant=6)
        bar(d,x+64,y+36,158,6,k,(90,150,255),slant=4)
        if down:
            ov=Image.new('RGBA',im.size,(0,0,0,0)); ImageDraw.Draw(ov).rounded_rectangle((x*K,y*K,(x+234)*K,(y+52)*K),10*K,fill=(120,0,0,70)); im.paste(ov,(0,0),ov); d=ImageDraw.Draw(im)
            text(d,(x+140,y+44),'Revive: hold use near them',8,anchor='mm',fill=(255,200,200),stroke=1)
        y+=58

def chat(im):
    d=ImageDraw.Draw(im)
    ov=Image.new('RGBA',im.size,(0,0,0,0)); ImageDraw.Draw(ov).rectangle((10*K,(H-320)*K,420*K,(H-262)*K),fill=(0,0,0,110)); im.paste(ov,(0,0),ov); d=ImageDraw.Draw(im)
    text(d,(16,H-310),"<AmenXL> Let's go!",11,stroke=0); text(d,(16,H-292),"<Kai> wait for me lol",11,stroke=0); text(d,(16,H-274),"<AmenXL> come on Kai",11,stroke=0)

def compose(ch,dbz,face='',low=False,talk=False,with_party=True,title=''):
    im=scene().convert('RGBA')
    hotbar(im); command_menu(im,ch); gauge(im,ch,dbz=dbz,face=face,low=low,talk=talk)
    if with_party: party(im)
    if talk: chat(im)
    d=ImageDraw.Draw(im)
    if title: text(d,(W/2,22),title,18,b=True,anchor='mm')
    return im.resize((W,H),Image.LANCZOS).convert('RGB')

compose('mikiko',False,title='Without Dragon Ball mod - Mikiko').save('/mnt/user-data/outputs/hud_mikiko_vanilla.png')
compose('rhalix',True,face='',title='With Dragon Ball (DragonMineZ) - Rhalix: Ki, Stamina, Form, Power %').save('/mnt/user-data/outputs/hud_rhalix_dbz.png')
compose('mikiko',True,face='_shout',talk=True,title='Talking in chat: his mouth moves on the portrait').save('/mnt/user-data/outputs/hud_mikiko_dbz_talking.png')
compose('rhalix',False,face='_exhausted',low=True,title='Low health: tired face, red flashing portrait and bar').save('/mnt/user-data/outputs/hud_rhalix_lowhp.png')

# expression sheet
sheet=Image.new('RGBA',(W*K,300*K),(22,22,34,255)); d=ImageDraw.Draw(sheet)
labels=[('','Normal'),('_blink','Blink'),('_hurt','Hit'),('_damage1','Big hit'),('_exhausted','Low health'),('_shout','Talking (open)'),('','Talking (closed)'),('_rage','Ultimate / KA')]
for row,ch in enumerate(('mikiko','rhalix')):
    for i,(fc,lab) in enumerate(labels):
        cx=80+i*150; cy=80+row*130
        portrait(sheet,cx,cy,50,ch,fc,COL[ch],low=fc=='_exhausted',lvl=42)
        d=ImageDraw.Draw(sheet)
        if row==0: text(d,(cx,cy+68),lab,12,b=True,anchor='mm')
sheet.resize((W,300),Image.LANCZOS).convert('RGB').save('/mnt/user-data/outputs/hud_expressions.png')
print('ok')

im=scene().convert('RGBA'); party(im)
im=im.resize((W,H),Image.LANCZOS).crop((W-270,0,W,200)).resize((540,400),Image.LANCZOS).convert('RGB'); im.save('/mnt/user-data/outputs/hud_party_closeup.png')
