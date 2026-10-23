"""Mikiko's belt buckle: a square silver plate with two rivets and a raised star (was a plain grey diamond)."""
import json, glob
from PIL import Image
RES = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
SW = {'silver': (0, 116), 'sdark': (36, 116), 'shi': (72, 116)}
COL = {'silver': (176, 182, 194), 'sdark': (92, 96, 108), 'shi': (236, 240, 248)}
for a in glob.glob(RES + 'textures/entity/character/mikiko*_details*.png'):
    if 'hairmask' in a or 'damage' in a: continue
    im = Image.open(a).convert('RGBA')
    for k, (u, v) in SW.items():
        c = COL[k]
        for y in range(v, v + 12):
            for x in range(u, u + 32):
                n = ((x * 5 + y * 11) % 5) - 2
                im.putpixel((x, y), tuple(max(0, min(255, c[i] + n * 2)) for i in range(3)) + (255,))
    im.save(a)

def cube(f, s, sw):
    return {'uv': list(SW[sw]), 'from': [round(x, 4) for x in f], 'size': [round(x, 4) for x in s]}

for name in ('mikiko', 'mikiko_foxking'):
    p = RES + f'models/detail/{name}.json'
    d = json.load(open(p))
    if d.get('buckle_v2'): continue
    body = d['bones']['body']
    # the old diamond buckle: rotated 45 degrees, centred on the belt front around y 11.3
    body['children'] = [c for c in body['children']
                        if not (abs(c['pivot'][1] - 11.3) < 0.02 and abs(c['pivot'][0]) < 0.01 and abs(c['rot'][2]) > 0.7)]
    cx, cy, z = 0.0, 11.3, -2.62          # z: belt front
    body['cubes'] += [
        cube([cx - 0.9, cy - 0.78, z - 0.24], [1.8, 1.56, 0.2], 'sdark'),         # backing / edge
        cube([cx - 0.8, cy - 0.68, z - 0.34], [1.6, 1.36, 0.12], 'silver'),       # the plate
        cube([cx - 0.64, cy - 0.58, z - 0.38], [0.2, 0.2, 0.06], 'shi'),          # rivets, top corners
        cube([cx + 0.44, cy - 0.58, z - 0.38], [0.2, 0.2, 0.06], 'shi'),
        # raised star in the middle
        cube([cx - 0.17, cy - 0.17, z - 0.40], [0.34, 0.34, 0.06], 'shi'),        # centre
        cube([cx - 0.08, cy - 0.49, z - 0.40], [0.16, 0.34, 0.06], 'shi'),        # top point
        cube([cx - 0.52, cy - 0.12, z - 0.40], [0.38, 0.14, 0.06], 'shi'),        # left / right arms
        cube([cx + 0.14, cy - 0.12, z - 0.40], [0.38, 0.14, 0.06], 'shi'),
        cube([cx - 0.36, cy + 0.15, z - 0.40], [0.16, 0.30, 0.06], 'shi'),        # the two feet
        cube([cx + 0.20, cy + 0.15, z - 0.40], [0.16, 0.30, 0.06], 'shi'),
        cube([cx - 0.17, cy + 0.17, z - 0.43], [0.34, 0.06, 0.03], 'sdark'),      # a shadow line under the star
    ]
    d['buckle_v2'] = True
    json.dump(d, open(p, 'w'), separators=(',', ':'))
    print(name, 'buckle done')
