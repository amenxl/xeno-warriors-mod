"""Torn clothes (explosions): holes in the clothing showing skin underneath, frayed dark edges. 3 stages, each adding to the last."""
from PIL import Image
import random

TEX = '/home/claude/xw/tex/'
OUT = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'
SKIN = {'mikiko': ((0x96, 0x62, 0x40), (0x84, 0x55, 0x3a)), 'rhalix': ((0x5a, 0x36, 0x21), (0x4c, 0x2d, 0x1c))}

def near(c, ref, tol=38):
    return sum(abs(a - b) for a, b in zip(c[:3], ref)) < tol * 3

import json, math

def cloth_mask(name, size):
    """Pixels of the details texture that belong to clothing (not hair, head gear, goggles or eyes)."""
    spec = json.load(open(f'/home/claude/xw/detail/{name}_spec.json'))
    ok = set()
    for c in spec['cubes']:
        n = c['name'].lower()
        if c['bone'].lower() == 'head' or 'wing' in c['bone'].lower() or any(k in n for k in ('hair', 'goggle', 'lens', 'eye', 'wing', 'horn', 'ear', 'feather', 'blade')):
            continue
        w, h, d = (math.ceil(abs(c['to'][i] - c['frm'][i]) - 1e-6) or 1 for i in range(3))
        u, v = c['uv']
        for y in range(v, v + d + h):
            for x in range(u, u + 2 * (d + w)):
                if 0 <= x < size[0] and 0 <= y < size[1]: ok.add((x, y))
    return ok

def holes(img, name, seed, count, rmax, mask=None):
    """Cumulative list of hole sets (one per stage) on clothing pixels."""
    w, h = img.size; px = img.load(); rnd = random.Random(seed)
    cloth = [(x, y) for y in range(h) for x in range(w) if px[x, y][3] > 0 and not near(px[x, y], SKIN[name][0])
             and (mask is None or (x, y) in mask)]
    stages, cur = [], set()
    for stage in range(3):
        for _ in range(count * (stage + 1)):
            cx, cy = rnd.choice(cloth)
            r = rnd.uniform(0.8, rmax)
            for y in range(int(cy - r - 1), int(cy + r + 2)):
                for x in range(int(cx - r - 1), int(cx + r + 2)):
                    if 0 <= x < w and 0 <= y < h and (x - cx) ** 2 + ((y - cy) * 1.3) ** 2 <= r * r + rnd.uniform(-0.6, 0.6):
                        if px[x, y][3] > 0 and not near(px[x, y], SKIN[name][0]) and (mask is None or (x, y) in mask):
                            cur.add((x, y))
        stages.append(set(cur))
    return stages

def edges(hole, w, h):
    out = set()
    for x, y in hole:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            q = (x + dx, y + dy)
            if q not in hole and 0 <= q[0] < w and 0 <= q[1] < h: out.add(q)
    return out

for name in ('mikiko', 'rhalix'):
    # skin texture: an overlay of skin showing through the holes, with frayed edges
    skin = Image.open(TEX + f'{name}.png').convert('RGBA'); sp = skin.load()
    body = {(x, y) for y in range(16, 64) for x in range(64)}       # not the head / hair
    for i, hole in enumerate(holes(skin, name, 7 + len(name), 7, 2.2, body), 1):
        o = Image.new('RGBA', skin.size, (0, 0, 0, 0)); op = o.load()
        rnd = random.Random(i)
        for x, y in hole:
            op[x, y] = SKIN[name][0] + (255,) if rnd.random() < 0.8 else SKIN[name][1] + (255,)
        for x, y in edges(hole, *skin.size):
            if sp[x, y][3] > 0 and not near(sp[x, y], SKIN[name][0]):
                r, g, b, a = sp[x, y]; op[x, y] = (int(r * 0.55), int(g * 0.55), int(b * 0.55), 255)
        o.save(OUT + f'{name}_torn{i}.png')
    # 3D clothing: the details texture with real holes punched through
    det = Image.open(TEX + f'{name}_details.png').convert('RGBA'); dp = det.load()
    for i, hole in enumerate(holes(det, name, 31 + len(name), 10, 3.0, cloth_mask(name, det.size)), 1):
        t = det.copy(); tp = t.load()
        for x, y in hole: tp[x, y] = (0, 0, 0, 0)
        for x, y in edges(hole, *det.size):
            r, g, b, a = tp[x, y]
            if a: tp[x, y] = (int(r * 0.5), int(g * 0.5), int(b * 0.5), a)
        t.save(OUT + f'{name}_details_torn{i}.png')

prev = Image.new('RGBA', (64 * 3 * 2 * 3, 64 * 3), (70, 70, 80, 255))
for j, name in enumerate(('mikiko', 'rhalix')):
    for i in range(4):
        b = Image.open(TEX + f'{name}.png').convert('RGBA')
        if i: b.alpha_composite(Image.open(OUT + f'{name}_torn{i}.png'))
        prev.paste(b.resize((144, 144), Image.NEAREST), ((j * 4 + i) * 148, 0))
prev.save('/tmp/claude-0/-home-claude/b2fe60cb-fd40-5a94-8ee2-7d1a501fd998/scratchpad/torn.png')
print('torn ok')
