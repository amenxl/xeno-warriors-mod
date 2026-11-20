"""
Hair masks for the transformations (textures <look>_hairmask.png for the skin, <look>_details_hairmask.png for the 3D
details): which pixels are the character's own hair. In game (client/race/FormHair) those pixels are recoloured to
the form's hair colour - their actual hair turns Super Saiyan gold (SSJ4 dark...), shading kept.
  skin:    the head and hat layers (rows 0-16), pixels in the hair's colour
  details: the pixels of the cubes on the head bone (from models/detail/<look>.json), in the hair's colour
The hair colour is the commonest non-skin colour on the top / back of the head.
Mikiko and Rhalix (their own hair groups) are left as they are; Yuri's 3D hair has its own mask.
Run only some: python3 hair_masks.py yuri yuri_c1pjs
Run: python3 hair_masks.py
"""
import json, math, os
from collections import Counter
from PIL import Image
ROOT = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
CH = ROOT + 'textures/entity/character/'
MD = ROOT + 'models/detail/'
LOOKS = ['kaeru', 'keita', 'sam', 'sam_royal', 'sam_s3', 'max', 'max_royal', 'max_s3', 'lexy', 'technica', 'logan', 'garry', 'zane',
         'kill', 'stevie', 'ultima', 'kyrie', 'kyrie_s2', 'lexy_s2', 'yuri', 'yuri_c1pjs', 'kyle', 'zack', 'kaitlyn', 'chase', 'senju', 'ava', 'ava_demon', 'persephone', 'persephone_demon', 'fox_girl', 'fox_girl_sakura']   # (Ai-oni: what's on her head is a hood, not hair)
SKIN_ONLY = {'yuri', 'yuri_c1pjs'}   # (Yuri's 3D hair has its own mask, tools/xw/dmzhair/yuri_ssj_hair.py: only her skin's head here)
TOL = {'kyrie': 0.34, 'kyrie_s2': 0.34}   # (Kyrie's hair has a lighter purple on the 3D strands than on her head)
tol = 0.22
NARROW = {'kaeru'}   # (Kaeru: bright orange bits on her head that aren't hair)
wide = True

def lum(c): return .3 * c[0] + .59 * c[1] + .11 * c[2]

def skin_of(who):
    f = Image.open(CH + who + '_face.png').convert('RGBA'); px = f.load()
    return Counter(px[x, y][:3] for y in range(2, 30) for x in range(2, 30) if px[x, y][3]).most_common(1)[0][0]

def hair_ref(px, skin):
    c = Counter()
    for (x0, y0, x1, y1) in ((8, 0, 16, 8), (40, 0, 48, 8), (24, 8, 32, 16), (56, 8, 64, 16)):
        for y in range(y0, y1):
            for x in range(x0, x1):
                p = px[x, y]
                if p[3] < 200 or sum(abs(p[i] - skin[i]) for i in range(3)) < 50: continue
                c[p[:3]] += 1
    return c.most_common(1)[0][0] if c else None

def match(p, r):
    lr, lp = lum(r), lum(p)
    if lr < 45:
        if sum(abs(p[i] - r[i]) for i in range(3)) <= 40 and lp <= lr * 2 + 25: return True
        # darker / lighter shades of a dark hair (Stevie's navy spikes): same hue, within reason
        if lp < 6 or not (0.35 <= lp / lr <= 3.2): return False       # (Ultima's teal highlights on her near-black hair)
        return sum(abs(p[i] / lp - r[i] / lr) for i in range(3)) <= 0.25
    if lp < 15: return False
    k = lp / lr
    lo, hi, t = (0.4, 1.7, tol) if not wide else (0.28, 2.0, max(tol, 0.30))   # the hair's shading and highlights
    if k < lo or k > hi: return False
    return sum(abs(p[i] / lp - r[i] / lr) for i in range(3)) <= t

def head_cubes(bone, out):
    for c in bone.get('cubes', []): out.append(c)
    for ch in bone.get('children', []): head_cubes(ch, out)

def uv_boxes(c):
    u, v = c['uv']; w, h, d = (math.ceil(s - 1e-6) for s in c['size'])
    return (u, v, u + 2 * d + 2 * w, v + d + h)

n = 0
import sys
for look in (sys.argv[1:] or LOOKS):
    who = 'fox_girl' if look.startswith('fox_girl') else look.split('_')[0]; tol = TOL.get(look, 0.22); wide = look not in NARROW
    sk = CH + look + '.png'
    if not os.path.exists(sk): continue
    im = Image.open(sk).convert('RGBA'); px = im.load()
    s = skin_of(who); ref = hair_ref(px, s)
    if ref is None: print(look, 'no hair colour'); continue
    m = Image.new('RGBA', im.size, (0, 0, 0, 0)); pm = m.load()
    for y in range(0, 16):
        for x in range(0, 64):
            p = px[x, y]
            if p[3] > 100 and match(p[:3], ref): pm[x, y] = (255, 255, 255, 255)
    m.save(CH + look + '_hairmask.png'); n += 1
    dt, dj = CH + look + '_details.png', MD + look + '.json'
    if look not in SKIN_ONLY and os.path.exists(dt) and os.path.exists(dj):
        d = Image.open(dt).convert('RGBA'); pd = d.load()
        model = json.load(open(dj))
        cubes = []
        if 'head' in model['bones']: head_cubes(model['bones']['head'], cubes)
        dm = Image.new('RGBA', d.size, (0, 0, 0, 0)); pdm = dm.load()
        k = d.size[0] / model.get('texture_size', d.size[0])
        hits = 0
        for c in cubes:
            x0, y0, x1, y1 = (int(round(v * k)) for v in uv_boxes(c))
            for y in range(max(0, y0), min(d.size[1], y1)):
                for x in range(max(0, x0), min(d.size[0], x1)):
                    p = pd[x, y]
                    if p[3] > 100 and match(p[:3], ref): pdm[x, y] = (255, 255, 255, 255); hits += 1
        dm.save(CH + look + '_details_hairmask.png'); n += 1
        print(look, 'hair', ref, 'detail hair pixels', hits)
    else:
        print(look, 'hair', ref, '(no details)')
print(n)
