from PIL import Image
import os

def box(u,v,w,h,d):
    return {'top':(u+d,v,w,d),'bottom':(u+d+w,v,w,d),'right':(u,v+d,d,h),
            'front':(u+d,v+d,w,h),'left':(u+d+w,v+d,d,h),'back':(u+2*d+w,v+d,w,h)}
HEAD=box(0,0,8,8,8); HAT=box(32,0,8,8,8)
BODY=box(16,16,8,12,4); JACKET=box(16,32,8,12,4)
RARM=box(40,16,4,12,4); RSLEEVE=box(40,32,4,12,4)
LARM=box(32,48,4,12,4); LSLEEVE=box(48,48,4,12,4)
RLEG=box(0,16,4,12,4); RPANT=box(0,32,4,12,4)
LLEG=box(16,48,4,12,4); LPANT=box(0,48,4,12,4)

def put(img, rect, grid, pal):
    u,v,w,h=rect
    assert len(grid)==h, (rect,len(grid))
    for y,row in enumerate(grid):
        assert len(row)==w, (rect,row)
        for x,c in enumerate(row):
            if c!='.': img.putpixel((u+x,v+y),pal[c]+(255,))
def mirror(g): return [r[::-1] for r in g]
def fill(ch,w,h): return [ch*w]*h

M={'K':(22,18,20),'k':(44,36,38),'R':(196,30,36),'r':(140,20,26),'B':(210,34,40),'b':(150,22,28),
'L':(70,150,235),'l':(175,220,255),'S':(150,98,64),'s':(122,78,50),'W':(240,240,240),'A':(232,145,30),
'M':(70,36,30),'J':(26,24,28),'j':(44,42,48),'O':(240,150,30),'o':(200,110,20),'Y':(252,205,60),
'w':(236,236,236),'D':(205,30,38),'d':(150,20,26),'G':(185,188,195),'g':(130,132,140),'i':(228,230,235),
'U':(60,110,205),'u':(40,70,150),'P':(24,22,26),'p':(38,36,42),'X':(30,28,28)}
img=Image.new('RGBA',(64,64),(0,0,0,0))
put(img,HEAD['front'],["KRKKRKKR","BLlBBlLB","bBBbbBBb","KkkSSkkK","KWASSAWK","SSSssSSS","SSSMMSSS","ssSSSSss"],M)
hr=["KKRKKRKK","BBBBBBBB","bbbbbbbb","KRKKRKKK","KKRKsSKK","RKKKSSSS","KRKKSSSS","KKRKssss"]
put(img,HEAD['right'],hr,M); put(img,HEAD['left'],mirror(hr),M)
put(img,HEAD['back'],["KRKKRKKR","BBBddBBB","bbBddBbb","KRKDDKRK","RKKDdKKR","KRKKKKRK","KKRKKRKK","RKKRKKRK"],M)
put(img,HEAD['top'],["KKRKKRKK","KRKKkKRK","KKKRKKKR","RKkKKRKK","KKRKKKkK","KRKKRKKR","KkKKKRKK","KRKKRKkK"],M)
put(img,HEAD['bottom'],fill('s',8,8),M)
put(img,HAT['front'],["RKRKKRKR","R......R","K......K","R......R","K......K","R......R","........","........"],M)
hs=["RKRKRKRK","........","........","R.K.R.K.","K.R.K...","R.K.R...","K.R.....","R......."]
put(img,HAT['right'],hs,M); put(img,HAT['left'],mirror(hs),M)
put(img,HAT['back'],["KRKRKRKR","........","........","R.KR.K.R","K.RK.R.K","R.K..K.R","K.R..R.K","R......R"],M)
put(img,HAT['top'],["R.K.R.K.",".R.K.R.K","K.R.K.R.",".K.R.K.R"]*2,M)
put(img,BODY['front'],["JJwOOwJJ","JjwOOwjJ","JJwOOwJJ","JJwGOwJJ","JjwOOwjJ","JJwOOwJJ","JJwoowJJ","JjwOOwjJ","DDDGGDDD","dDDGgDDd","PPPPPDDP","PpPPPdDP"],M)
put(img,BODY['back'],["JJJJJJJJ","JOOOOOOJ","JYOYOYOJ","JOYOYOYJ","JOOOOOOJ","JOOOOOOJ","JooOOooJ","JJJJJJJJ","DDDDDDDD","dDDDDDDd","PPPPPPPP","PpPPPPpP"],M)
bs=["JJJJ","JjJJ","JJJJ","JJjJ","JJJJ","JjJJ","JJJJ","JJJJ","DDDD","dDDd","PPPP","PPpP"]
put(img,BODY['right'],bs,M); put(img,BODY['left'],mirror(bs),M)
put(img,BODY['top'],["JJJJJJJJ","JJJJJJJJ","JJwOOwJJ","JJwOOwJJ"],M)
put(img,BODY['bottom'],fill('P',8,4),M)
arm=["JJJJ","JjJJ","JJJJ","JJjJ","JJJJ","wwww","JJJJ","JJJJ","DDDD","dDDd","SSSS","ssSs"]
for A in (RARM,LARM):
    for f in ('front','back','left','right'): put(img,A[f],arm,M)
    put(img,A['top'],fill('J',4,4),M); put(img,A['bottom'],fill('s',4,4),M)
pad=["GiGG","GGGg","gGGg","gggg"]+["...."]*8
for f in ('front','back','left'): put(img,LSLEEVE[f],pad,M)
put(img,LSLEEVE['top'],["iGGG","GGiG","GGGG","GgGG"],M)
leg=["PPPP","PpPP","PPPP","PPPP","PPPP","PPPP","PPpP","OPOP","OOOO","wwww","DDwD","XXXX"]
legb=leg[:10]+["DDDD","XXXX"]
patch=["PPPP","PpPP","PPPP","PPUP","PUUP","UUuP","PPpP","OPOP","OOOO","wwww","DDwD","XXXX"]
for L in (RLEG,LLEG):
    for f in ('front','left','right'): put(img,L[f],leg,M)
    put(img,L['back'],legb,M); put(img,L['top'],fill('P',4,4),M); put(img,L['bottom'],fill('X',4,4),M)
put(img,RLEG['front'],patch,M); put(img,RLEG['right'],patch,M)
os.makedirs('tex',exist_ok=True); img.save('tex/mikiko.png')

R={'H':(165,40,210),'q':(118,22,158),'I':(208,95,242),'S':(90,54,33),'s':(70,41,25),'t':(108,68,43),
'E':(225,30,40),'e':(130,10,20),'W':(240,240,240),'M':(60,30,26),'C':(150,30,170),'c':(105,18,125),
'L':(185,60,205),'Y':(238,182,40),'y':(180,130,20),'P':(24,22,28),'p':(38,36,44),'B':(110,24,30),
'G':(190,192,198),'g':(130,132,140),'O':(130,40,170),'o':(90,24,120),'X':(45,20,55),'w':(236,236,236)}
img=Image.new('RGBA',(64,64),(0,0,0,0))
put(img,HEAD['front'],["HIHHqHIH","HHqHHIHH","HqHSHqHH","qHSSSSHq","HeESSEeH","HSSssSSH","SSSMMSSS","sSSSSSSs"],R)
hr=["HHIHHqHH","HqHHIHHH","HHqHHHqH","qHHIHHHq","HqHHsSHH","qHHqSSSH","HqHHSSSS","qHHqssss"]
put(img,HEAD['right'],hr,R); put(img,HEAD['left'],mirror(hr),R)
put(img,HEAD['back'],["HIHHqHIH","HHqHHIHq","qHHIHqHH","HqHHHHqH","HHIqHHHq","qHHHqHIH","HqHIHHqH","qHqHHqHq"],R)
put(img,HEAD['top'],["IHqHIHqH","HqIHqHIq","qHHIqHHI","IqHHqIHH"]*2,R)
put(img,HEAD['bottom'],fill('s',8,8),R)
put(img,HAT['front'],["IHqHIHqH","H.H..H.H","q......q","H......H","q......q","........","........","........"],R)
hs=["HIHqHIHq","HqHHqH..","qH.Hq...","H.q.H...","q..H....","........","........","........"]
put(img,HAT['right'],hs,R); put(img,HAT['left'],mirror(hs),R)
put(img,HAT['back'],["IHqHIHqH","HqHIHqHI","qH.qH.Hq","H.q.H.q.",".q...q..","........","........","........"],R)
put(img,HAT['top'],["IHqHIHqH","HqIHqHIq","qHHIqHHI","IqHHqIHH"]*2,R)
put(img,BODY['front'],["CLSSSSLC","CLYSSYLC","CCSYYSCC","CCYSSYCC","CCSYYSCC","CCYSSYCC","CCsSSsCC","CCSSsSCC","CBBGGBBC","CBBgGBBC","CPPPPPPC","cPpPPpPc"],R)
put(img,BODY['back'],["cCCCCCCc","CcCCCCcC","CCcCCcCC","CCCccCCC"]+["CCCYCCCC","CcCYCCcC"]*4,R)
bs=["CCCC","CcCC","CCCC","CCcC"]*3
put(img,BODY['right'],bs,R); put(img,BODY['left'],mirror(bs),R)
put(img,BODY['top'],["CCCCCCCC","CCCCCCCC","CCSSSSCC","CLSSSSLC"],R)
put(img,BODY['bottom'],["CPPPPPPC"]*4,R)
arm=["CCCC","CLCC","CCCC","CCCc","CCCC","cCCC","CCCC","CCcC","wwww","cccc","SSSS","sSSs"]
armx=["CCCC","YCCY","CYYC","YCCY"]+arm[4:]
for A,outer in ((RARM,'right'),(LARM,'left')):
    for f in ('front','back','left','right'): put(img,A[f],arm,R)
    put(img,A[outer],armx,R); put(img,A['front'],armx,R)
    put(img,A['top'],fill('C',4,4),R); put(img,A['bottom'],fill('s',4,4),R)
leg=["PPPP","PpPP","PPPP","PPpP","PPPP","PpPP","PPPP","OOOO","YOOY","OYYO","YooY","XXXX"]
legs=leg[:8]+["OOOO","OoOO","oOOo","XXXX"]
for L in (RLEG,LLEG):
    for f in ('back','left','right'): put(img,L[f],legs,R)
    put(img,L['front'],leg,R); put(img,L['top'],fill('P',4,4),R); put(img,L['bottom'],fill('X',4,4),R)
tail=["CCCC","CcCC","CCCC","cCCc","CCCC","CC.C","C..C",".C.."]+["...."]*4
for P,outer,fx in ((RPANT,'right',0),(LPANT,'left',3)):
    put(img,P[outer],tail,R); put(img,P['back'],tail,R)
    strip=[]
    for y in range(12):
        row=['.']*4
        if y<6: row[fx]='C' if y%3 else 'c'
        strip.append(''.join(row))
    put(img,P['front'],strip,R)
img.save('tex/rhalix.png')

F={'K':(18,15,22),'k':(32,28,38),'g':(52,46,60),'h':(70,62,80)}
img=Image.new('RGBA',(64,64),(0,0,0,0))
for x in range(44):
    for y in range(3):
        img.putpixel((x,y),F['k' if (x+y)%3 else 'g']+(255,))
LENS=[9,11,13,15,17,19,21]
for i,Ln in enumerate(LENS):
    u=i*8; v=4
    r=box(u,v,3,Ln,1)
    for f,(fu,fv,fw,fh) in r.items():
        for y in range(fh):
            for x in range(fw):
                c='K'
                if fw==3 and f in('front','back'):
                    c='h' if x==1 and y<fh-2 else ('k' if (y//3)%2 else 'K')
                    if y==fh-1 and x!=1: continue
                img.putpixel((fu+x,fv+y),F[c]+(255,))
img.save('tex/rhalix_wings.png')
print("ok")
