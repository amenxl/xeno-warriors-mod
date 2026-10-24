import sys,math; sys.path.insert(0,'/home/claude/xw/outfits')
from kit import CHAR
from PIL import Image, ImageDraw
D='/tmp/dmzx/assets/dragonminez/textures/armor/'
PIV={'head':(0,0,0),'body':(0,0,0),'right_arm':(-5,2,0),'left_arm':(5,2,0),'right_leg':(-1.9,12,0),'left_leg':(1.9,12,0)}
BASE=[('head',0,0,-4,-8,-4,8,8,8),('body',16,16,-4,0,-2,8,12,4),('right_arm',40,16,-3,-2,-2,4,12,4),('left_arm',32,48,-1,-2,-2,4,12,4),('right_leg',0,16,-2,0,-2,4,12,4),('left_leg',16,48,-2,0,-2,4,12,4)]
OV=[('head',32,0,-4,-8,-4,8,8,8),('body',16,32,-4,0,-2,8,12,4),('right_arm',40,32,-3,-2,-2,4,12,4),('left_arm',48,48,-1,-2,-2,4,12,4),('right_leg',0,32,-2,0,-2,4,12,4),('left_leg',0,48,-2,0,-2,4,12,4)]
def boxes(img,table,inf,dep,parts):
    s=max(1,img.width//64); out=[]
    for name,u,v,x0,y0,z0,w,h,d in table:
        if name not in parts: continue
        x1,y1,z1=x0+w,y0+h,z0+d; P=PIV[name]
        for f in ((u+d,v+d,w,h,0),(u+2*d+w,v+d,w,h,1),(u,v+d,d,h,2),(u+d+w,v+d,d,h,3),(u+d,v,w,d,4)):
            for i in range(f[2]):
                for j in range(f[3]):
                    px=img.getpixel(((f[0]+i)*s+s//2,(f[1]+j)*s+s//2))
                    if px[3]<=24: continue
                    b={0:[x0+i,y0+j,z0-inf-dep,x0+i+1,y0+j+1,z0-inf],1:[x1-i-1,y0+j,z1+inf,x1-i,y0+j+1,z1+inf+dep],2:[x0-inf-dep,y0+j,z1-i-1,x0-inf,y0+j+1,z1-i],3:[x1+inf,y0+j,z0+i,x1+inf+dep,y0+j+1,z0+i+1],4:[x0+i,y0-inf-dep,z1-j-1,x0+i+1,y0-inf,z1-j]}[f[4]]
                    b=[b[0]+P[0],b[1]+P[1],b[2]+P[2],b[3]+P[0],b[4]+P[1],b[5]+P[2]]
                    out.append((b,px[:3]))
    return out
def render(bxs,yaw=200,S=9):
    cy,sy=math.cos(math.radians(yaw)),math.sin(math.radians(yaw)); polys=[]
    L=(0.4,-0.7,-0.5)
    for b,c in bxs:
        x0,y0,z0,x1,y1,z1=b
        faces=[((x0,y0,z0),(x1,y0,z0),(x1,y1,z0),(x0,y1,z0),(0,0,-1)),((x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1),(0,0,1)),
               ((x0,y0,z0),(x0,y1,z0),(x0,y1,z1),(x0,y0,z1),(-1,0,0)),((x1,y0,z0),(x1,y1,z0),(x1,y1,z1),(x1,y0,z1),(1,0,0)),
               ((x0,y0,z0),(x1,y0,z0),(x1,y0,z1),(x0,y0,z1),(0,-1,0))]
        for *pts,n in faces:
            nz=n[0]*sy+n[2]*cy
            if nz>=0: continue
            sh=0.6+0.4*max(0,-(n[0]*L[0]+n[1]*L[1]+n[2]*L[2]))
            q=[(p[0]*cy-p[2]*sy, p[1], p[0]*sy+p[2]*cy) for p in pts]
            polys.append((sum(p[2] for p in q)/4,[(x,y) for x,y,_ in q],tuple(int(v*sh) for v in c)))
    polys.sort(key=lambda p:-p[0])
    W,H=26*S,36*S; im=Image.new('RGB',(W,H),(36,38,46)); d=ImageDraw.Draw(im)
    for _,pts,c in polys: d.polygon([(W/2-x*S,(y+9)*S) for x,y in pts],fill=c)
    return im
