"""Rhalix's hair, rebuilt with DMZ-style strands: messy purple on top and in the fringe, black underneath and at the ends."""
import sys, json, math, copy
sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/dmzhair'); sys.path.insert(0, '/home/claude/xw/ultima')
import dmz_hair as H
from dmz_hair import Strand
from PIL import Image
import random

RES = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
CH = RES + 'textures/entity/character/'
P1, P2, P3, B1, B2 = '#8a2ec4', '#a64ae0', '#6e1e9e', '#1a161e', '#2a2430'
TOPC = (P2, P1, P1, B2)          # purple, going dark at the very tip
UNDER = (B2, B1)

def strands():
    rnd = random.Random(7)
    out = []
    # fringe: short thick locks falling over the brow (stopping above the eyes), longer on his right
    for i, (dx, ln, h) in enumerate(((-0.25, 2, 1.5), (-0.05, 2, 1.6), (0.2, 2, 1.8), (0.4, 3, 1.6))):
        out.append(Strand('front', i, (dx, -0.95, -0.3), ln, w=2.4, h=h, d=1.0, curve=(-10, 0, dx * 12), colors=(P2, P1, B2)))
    # top: the messy crown - locks lean out and back, then curve down onto the head
    for i in range(16):
        row, col = divmod(i, 4)
        gx, gz = H.GRID[col], H.GRID[row]
        d = (gx * 0.32 + rnd.uniform(-0.08, 0.08), 0.32 + rnd.uniform(-0.05, 0.1), 0.35 + gz * 0.12 + rnd.uniform(-0.08, 0.08))
        if row == 0: d = (gx * 0.25, 0.45, -0.6)
        out.append(Strand('top', i, d, 3, w=2.4, h=1.7, d=1.8, curve=(34 if row else -32, 0, -gx * 12), colors=(P2, P1, P3)))
    # back: purple at the crown, black underneath down to the nape
    for i in range(16):
        row, col = divmod(i, 4)
        gx = H.GRID[col]
        cols = (P1, P3) if row == 0 else UNDER
        out.append(Strand('back', i, (gx * 0.05, -0.96, 0.25), 2, w=2.2, h=1.6, d=1.2, curve=(10, 0, gx * 2), colors=cols))
    # sides: short and black underneath, purple on the top row
    for face, sx in (('left', -1), ('right', 1)):
        for i in range(16):
            row, col = divmod(i, 4)
            gz = H.GRID[col] * (1 if face == 'left' else -1)
            cols = (P1, P3) if row == 0 else UNDER
            ln = 2 if gz < 2 else 1
            if gz < -2 and row > 1: continue                               # leave his face/ears clear at the front-bottom
            out.append(Strand(face, i, (sx * 0.2, -0.97, 0.15), ln, w=1.2, h=1.6, d=2.2, curve=(6, 0, -sx * 8), colors=cols))
    # the little ahoge sticking up
    out.append(Strand('top', 5, (0.2, 0.95, 0.2), 3, w=0.8, h=1.5, d=0.5, curve=(-35, 0, 15), colors=(P2, P2, B2)))
    return out

def build(apply=False):
    mj = json.load(open('/tmp/rhalix_json_backup.json'))
    atlas = Image.open('/tmp/rhalix_details_backup.png').convert('RGBA')
    # solid colour swatches in free atlas space (any cube's box UV fits inside 16x16)
    spots = [(208, 144), (224, 144), (240, 144), (0, 160), (16, 160), (32, 160)]
    sw = {}
    for (u, v), col in zip(spots, (P1, P2, P3, B1, B2)):
        c = tuple(int(col[i:i + 2], 16) for i in (1, 3, 5))
        for y in range(v, v + 16):
            for x in range(u, u + 16): atlas.putpixel((x, y), c + (255,))
        sw[col] = (u, v)
    st = strands()
    nodes = H.bake(st, sw)
    mj['bones']['head']['children'] = [c for c in mj['bones']['head']['children'] if c.get('name') not in ('hair', 'bangs')] + nodes
    if apply:
        json.dump(mj, open(RES + 'models/detail/rhalix.json', 'w'), separators=(',', ':'))
        atlas.save(CH + 'rhalix_details.png')
    return mj, atlas, sum(len(s.cubes()) for s in st)

if __name__ == '__main__':
    import ultima as U
    mj, atlas, n = build('--apply' in sys.argv)
    skin = Image.open(CH + 'rhalix.png').convert('RGBA'); face = Image.open(CH + 'rhalix_face.png').convert('RGBA')
    mj['bones']['body']['children'] = [c for c in mj['bones']['body']['children'] if c.get('name') not in ('right_wing', 'left_wing')]
    im = U.preview(mj, skin, atlas, face, yaws=(0, 35, 90, 200), scale=10, wide=24, tall=22, top=7)
    im.save('/tmp/rh_hair.png'); print(n, 'cubes', im.size)
