"""Normal Rhalix touch-ups from the colored reference: fully yellow undershirt (skin), black stand-up collar and
lapel facings, yellow wrist bands, sleeve seams, pocket flaps with gold buttons, extra strap rivets.
Idempotent: removes its own 'xwplus' nodes/cubes, then re-adds them to the Forge rhalix.json."""
import json, math
import numpy as np
from PIL import Image
A = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
CH = A + 'textures/entity/character/'
MJ = A + 'models/detail/rhalix.json'
BLACK, BLACK2 = (24, 20, 30), (34, 28, 42)
YEL, YEL_D, YEL_L = (246, 222, 46), (214, 186, 30), (252, 236, 96)
COAT_D, GOLD, RIVET = (122, 30, 152), (230, 200, 74), (110, 84, 20)
STRAP = (204, 158, 24)
ORIG = {'body': [0, 24, 0], 'right_arm': [5, 22, 0], 'left_arm': [-5, 22, 0]}

def r4(v): return [round(x, 4) for x in v]
def box(o, lo, hi): return r4([o[0] - hi[0], o[1] - hi[1], lo[2] - o[2]]), r4([hi[i] - lo[i] for i in range(3)])
def rel(o, p): return r4([o[0] - p[0], o[1] - p[1], p[2] - o[2]])
def rot(r): return r4([-math.radians(r[0]), -math.radians(r[1]), math.radians(r[2])])

CUBES = []   # (bone, lo, hi, color, rot or None, pivot or None)
def add(bone, lo, hi, col, r=None, p=None):
    lo2 = [min(lo[i], hi[i]) for i in range(3)]; hi2 = [max(lo[i], hi[i]) for i in range(3)]
    CUBES.append((bone, lo2, hi2, col, r, p))

for s in (1, -1):
    add('body', [s * 2.0, 15.4, -3.03], [s * 3.25, 24.45, -2.87], BLACK)                              # lapel facings
    add('body', [s * 2.92, 22.75, -2.52], [s * 4.62, 26.0, 3.02], BLACK2, [0, 0, s * -7], [s * 3.6, 23, 0])  # stand-up collar
    add('body', [s * 2.2, 9.7, -3.08], [s * 4.25, 10.35, -2.97], COAT_D)                              # pocket flaps
    add('body', [s * 3.15, 9.55, -3.16], [s * 3.45, 9.85, -3.05], GOLD)                              # flap button
    for y in (17.2, 19.4):                                                                         # extra strap rivets
        add('body', [s * 1.35 - .22, y, -2.72], [s * 1.35 + .22, y + .44, -2.6], RIVET)
add('body', [-3.05, 23.35, 2.45], [3.05, 25.95, 3.08], BLACK2)
for s in (1, -1):                                                                                  # X straps: deeper gold so they read on the yellow shirt
    add('body', [-4.0, 17.85, -2.72], [4.0, 18.7, -2.6], STRAP, [0, 0, s * 38], [0, 18.3, -2.5])
    for x in (2.9, -2.9):
        pass                                       # collar back
for arm, s in (('right_arm', 1), ('left_arm', -1)):
    add(arm, [s * 3.68, 11.55, -2.38], [s * 8.32, 12.35, 2.38], YEL)                                 # yellow wrist band
    add(arm, [s * 8.41, 14.4, -0.15], [s * 8.5, 22.5, 0.15], COAT_D)                                 # outer sleeve seam
    add(arm, [s * 3.5, 14.4, -2.52], [s * 8.5, 14.65, 2.52], COAT_D)                                 # cuff seam line

def strip(n):
    n['children'] = [c for c in n.get('children', []) if not str(c.get('name', '')).startswith('xwplus')]
    n['cubes'] = [c for c in n['cubes'] if not c.get('xwplus')]
    for c in n['children']: strip(c)

def main():
    m = json.load(open(MJ)); strip_all = [strip(b) for b in m['bones'].values()]
    tex = Image.open(CH + 'rhalix_details.png').convert('RGBA')
    # wipe previous plus regions (kept in a sidecar so re-runs don't leak atlas space)
    side = A + 'models/detail/rhalix_plus_uv.json'
    try:
        for (u, v, w, h) in json.load(open(side)):
            for y in range(v, v + h):
                for x in range(u, u + w): tex.putpixel((x, y), (0, 0, 0, 0))
    except FileNotFoundError: pass
    alpha = np.array(tex)[:, :, 3] > 0
    used = []
    def alloc(w, h):
        for v in range(0, 256 - h, 1):
            for u in range(150, 256 - w, 1):
                if not alpha[v:v + h, u:u + w].any():
                    alpha[v:v + h, u:u + w] = True; used.append((u, v, w, h)); return u, v
        raise RuntimeError('atlas full')
    for i, (bone, lo, hi, col, r, p) in enumerate(CUBES):
        o = ORIG[bone]
        if r is not None:
            frm, size = box(p, lo, hi)
            node = {'name': f'xwplus{i}', 'pivot': rel(o, p), 'rot': rot(r), 'cubes': [], 'children': []}
            m['bones'][bone]['children'].append(node); target = node['cubes']
        else:
            frm, size = box(o, lo, hi); target = m['bones'][bone]['cubes']
        w, h, d = [max(1, math.ceil(x)) for x in size]
        U, V = 2 * d + 2 * w, d + h
        u, v = alloc(U, V)
        for y in range(V):
            for x in range(U):
                c = col
                if (x + y) % 5 == 0 and col not in (GOLD, RIVET, STRAP): c = tuple(min(255, int(k * 1.08)) for k in col)
                tex.putpixel((u + x, v + y), c + (255,))
        target.append({'uv': [u, v], 'from': frm, 'size': size, 'xwplus': 1})
    json.dump(m, open(MJ, 'w')); json.dump(used, open(side, 'w'))
    tex.save(CH + 'rhalix_details.png')
    # skin: the whole undershirt yellow (front strip + the bit below the straps)
    sk = Image.open(CH + 'rhalix.png').convert('RGBA'); px = sk.load()
    for y in range(20, 32):
        for x in range(20, 28):
            r_, g_, b_, a_ = px[x, y]
            if a_ and min(r_, g_, b_) > 120 and max(r_, g_, b_) - min(r_, g_, b_) < 40:      # white/gray shirt
                lum = (r_ + g_ + b_) / 3
                px[x, y] = YEL_L + (255,) if lum > 225 else (YEL + (255,) if lum > 185 else YEL_D + (255,))
    sk.save(CH + 'rhalix.png')
    print('cubes', len(CUBES))

if __name__ == '__main__':
    main()
