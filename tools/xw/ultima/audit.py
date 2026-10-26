"""Audit every detail model: cubes whose texture area runs off the atlas or is fully transparent (invisible/black
faces), zero-size cubes, and identical overlapping cubes (z-fighting)."""
import json, glob, os, math, sys
from PIL import Image
A = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
def cubes(node, path=''):
    for c in node.get('cubes', []): yield path, c
    for ch in node.get('children', []): yield from cubes(ch, path + '/' + ch.get('name', '?'))
def box(u, v, w, h, d):
    w, h, d = max(1, math.floor(w + .001)), max(1, math.floor(h + .001)), max(1, math.floor(d + .001))
    return {'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d), 'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h)}
report = {}
for mf in sorted(glob.glob(A + 'models/detail/**/*.json', recursive=True)):
    rel = os.path.relpath(mf, A + 'models/detail/')
    base = rel[:-5]
    tex = A + ('textures/entity/npc/' + os.path.basename(base) if base.startswith('npc/') else 'textures/entity/character/' + base) + '_details.png'
    if not os.path.exists(tex): continue
    im = Image.open(tex).convert('RGBA'); W, H = im.size; px = im.load()
    mj = json.load(open(mf)); issues = []; seen = {}
    for bone, b in mj['bones'].items():
        for path, c in cubes(b, bone):
            u, v = c['uv']; w, h, d = c['size']
            if min(w, h, d) <= 0: issues.append(f'zero-size cube in {path}'); continue
            key = (path, tuple(c['from']), tuple(c['size']))
            if key in seen: issues.append(f'duplicate cube (z-fighting) in {path}')
            seen[key] = 1
            faces = box(int(u), int(v), w, h, d); off = []; clear = []
            for k, (x0, y0, fw, fh) in faces.items():
                if x0 + fw > W or y0 + fh > H or x0 < 0 or y0 < 0: off.append(k); continue
                if all(px[x, y][3] == 0 for y in range(y0, y0 + fh) for x in range(x0, x0 + fw)): clear.append(k)
            if off: issues.append(f'{path}: texture off the atlas on {",".join(off)} (uv {u},{v} size {w},{h},{d})')
            if len(clear) == 6: issues.append(f'{path}: cube fully transparent (uv {u},{v})')
            elif 'front' in clear and 'back' in clear and w > 1.5 and h > 1.5: issues.append(f'{path}: front/back faces see-through (uv {u},{v})')
    if issues: report[rel] = issues
tot = sum(len(v) for v in report.values())
for k, v in report.items():
    print(f'== {k} ({len(v)})')
    for i in v[:6]: print('   ', i)
print('models with issues:', len(report), 'total issues:', tot)
