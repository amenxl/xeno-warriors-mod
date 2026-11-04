"""Kaeru's hood, up: adds a 'hood_up' part (head) and moves her bunched hood into 'hood_down' (body) in
models/detail/kaeru.json, and paints the hood's texture into the free rows of every kaeru_details*.png.
Orange outside like the hood when it's down, the darker orange lining inside and on the rim. Re-runnable.
Run: python3 tools/xw/kaeru/kaeru_hood.py
"""
import json, math, os, glob, random
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), '../../../src/main/resources/assets/xenowarriors')
MODEL = os.path.join(ROOT, 'models/detail/kaeru.json')
TEX = [t for t in sorted(glob.glob(os.path.join(ROOT, 'textures/entity/character/kaeru_details*.png'))) if 'damage' not in t]   # (the damage ones are overlays)

OR, OR2, OR3 = (0xd4, 0x66, 0x1e), (0xa8, 0x4a, 0x14), (0xf0, 0x8a, 0x3a)

# (name, from, size, uv, inner face(s) painted as lining, everything lining?)
Y0 = 58
CUBES = [
    ('top',    [-5.45, -10.45, -4.7], [10.9, 1.1, 10.35], [0, Y0],     ['down'], False),
    ('side_r', [-5.45, -9.35, -4.7],  [0.5, 8.75, 10.35], [44, Y0],    ['east'], False),
    ('side_l', [4.95, -9.35, -4.7],   [0.5, 8.75, 10.35], [67, Y0],    ['west'], False),
    ('back',   [-5.45, -9.35, 5.1],   [10.9, 8.75, 0.55], [90, Y0],    ['north'], False),
    ('rim_t',  [-5.5, -10.5, -5.15],  [11.0, 2.5, 0.5],   [114, Y0],   [], True),
    ('rim_r',  [-5.5, -8.0, -5.15],   [1.5, 7.6, 0.5],    [138, Y0],   [], True),
    ('rim_l',  [4.0, -8.0, -5.15],    [1.5, 7.6, 0.5],    [143, Y0],   [], True),
    ('ridge',  [-0.5, -10.8, -3.6],   [1.0, 0.4, 8.6],    [148, Y0],   [], False),
]


def faces(u, v, w, h, d):
    """Box-UV face rectangles (x0, y0, x1, y1) as ModelPart.Cube lays them out."""
    return {
        'up': (u + d, v, u + d + w, v + d),            # (top of the head, world up)
        'down': (u + d + w, v, u + d + w + w, v + d),
        'west': (u, v + d, u + d, v + d + h),
        'north': (u + d, v + d, u + d + w, v + d + h),
        'east': (u + d + w, v + d, u + d + w + d, v + d + h),
        'south': (u + d + w + d, v + d, u + d + w + d + w, v + d + h),
    }


def paint(im):
    px = im.load()
    rnd = random.Random(7)
    for name, f, s, uv, inner, lining in CUBES:
        w, h, d = s
        u, v = uv
        fr = faces(u, v, w, h, d)
        for face, (x0, y0, x1, y1) in fr.items():
            base = OR2 if lining or face in inner else OR
            for y in range(math.floor(y0), math.ceil(y1)):
                for x in range(math.floor(x0), math.ceil(x1)):
                    k = rnd.randint(-7, 7)
                    c = base
                    # a lighter edge along the hood's front (rim) faces, a faint hem at the bottom of the panels
                    if lining and face == 'north': c = tuple(min(255, int(a * 1.08)) for a in OR2)
                    if not lining and face in ('west', 'east', 'south') and name.startswith(('side', 'back')) and y >= math.ceil(y1) - 1: c = OR2
                    px[x, y] = tuple(max(0, min(255, a + k)) for a in c) + (255,)


def model():
    m = json.load(open(MODEL))
    head, body = m['bones']['head'], m['bones']['body']
    head['children'] = [c for c in head['children'] if c['name'] != 'hood_up']
    head['children'].append({'name': 'hood_up', 'pivot': [0, 0, 0], 'rot': [0, 0, 0],
                             'cubes': [{'uv': uv, 'from': f, 'size': s} for _, f, s, uv, _, _ in CUBES], 'children': []})
    down = next((c for c in body['children'] if c['name'] == 'hood_down'), None)
    if down is None:
        # the bunched orange hood behind her neck (it goes away while the hood is up)
        idx = next(i for i, c in enumerate(body['cubes']) if c['uv'] == [62, 21])
        cube = body['cubes'].pop(idx)
        body['children'].insert(0, {'name': 'hood_down', 'pivot': [0, 0, 0], 'rot': [0, 0, 0], 'cubes': [cube], 'children': []})
    json.dump(m, open(MODEL, 'w'), separators=(',', ':'))


if __name__ == '__main__':
    model()
    for t in TEX:
        im = Image.open(t).convert('RGBA')
        paint(im)
        im.save(t)
        print('painted', os.path.basename(t))
