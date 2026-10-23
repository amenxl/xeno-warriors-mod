"""Mikiko's zigzag trim (red triangles on the orange band round his top and the hems of his shorts), the band
filled in down his sides, a red flared bracelet, and shoe soles that cover the leg's underside."""
import json, sys
from PIL import Image
RES = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
SW = {'orange': (0, 100), 'red': (36, 100), 'dark': (72, 100), 'hi': (108, 100), 'sole': (144, 100)}
COL = {'orange': (240, 150, 32), 'red': (200, 28, 36), 'dark': (78, 10, 14), 'hi': (255, 196, 70), 'sole': (52, 12, 16)}


def paint(atlas):
    im = Image.open(atlas).convert('RGBA')
    for k, (u, v) in SW.items():
        c = COL[k]
        for y in range(v, v + 14):
            for x in range(u, u + 32):
                n = ((x * 7 + y * 13) % 5) - 2          # a little grain, like the rest of the sheet
                im.putpixel((x, y), (max(0, min(255, c[0] + n * 3)), max(0, min(255, c[1] + n * 2)), max(0, min(255, c[2] + n)), 255))
    im.save(atlas)


def cube(frm, size, sw):
    return {'uv': list(SW[sw]), 'from': [round(a, 4) for a in frm], 'size': [round(a, 4) for a in size]}


def triangles(axis, a0, a1, base_y, face, out, step_h=0.27, width=0.8):
    """Red stepped triangles pointing up, bases on base_y, along [a0, a1] on a face at `face` (x or z),
    standing out from it by `out` (+/-). Tops get a bright tip; returns cubes."""
    res = []
    n = max(1, int((a1 - a0) / width))
    pad = ((a1 - a0) - n * width) / 2
    depth = 0.05
    f0 = face if out > 0 else face - depth
    f0 += 0.01 if out > 0 else -0.01
    for i in range(n):
        c = a0 + pad + width * (i + 0.5)
        for s in range(3):
            w = width * (3 - s) / 3 * 0.96
            y = base_y - step_h * (s + 1)
            sw = 'hi' if s == 2 else 'red'
            if axis == 'x':
                res.append(cube([c - w / 2, y, f0], [w, step_h, depth], sw))
            else:
                res.append(cube([f0, y, c - w / 2], [depth, step_h, w], sw))
    return res


def strip(axis, a0, a1, y, face, out, h=0.1):
    depth = 0.04
    f0 = face + (0.005 if out > 0 else -depth - 0.005)
    if axis == 'x':
        return [cube([a0, y, f0], [a1 - a0, h, depth], 'dark')]
    return [cube([f0, y, a0], [depth, h, a1 - a0], 'dark')]


def is_diamond(c, y):
    return abs(c['pivot'][1] - y) < 0.02 and any(abs(abs(r) - 0.7854) < 0.01 for r in c['rot'])


def process(name):
    p = RES + f'models/detail/{name}.json'
    d = json.load(open(p))
    if d.get('trim_v2'):
        print(name, 'already done'); return
    body = d['bones']['body']
    # top band: y 5.7 - 6.7; front segments, full back, now the sides too
    body['children'] = [c for c in body['children'] if not is_diamond(c, 6.1)]
    body['cubes'] += [cube([-4.4, 5.7, -2.75], [0.5, 1.0, 5.5], 'orange'), cube([3.9, 5.7, -2.75], [0.5, 1.0, 5.5], 'orange')]
    for a0, a1 in ((-4.4, -1.6), (1.6, 4.4)):
        body['cubes'] += triangles('x', a0, a1, 6.7, -2.75, -1) + strip('x', a0, a1, 5.7, -2.75, -1) + strip('x', a0, a1, 6.6, -2.75, -1)
    body['cubes'] += triangles('x', -4.4, 4.4, 6.7, 2.75, 1) + strip('x', -4.4, 4.4, 5.7, 2.75, 1) + strip('x', -4.4, 4.4, 6.6, 2.75, 1)
    for x, out in ((-4.4, -1), (4.4, 1)):
        body['cubes'] += triangles('z', -2.75, 2.75, 6.7, x, out) + strip('z', -2.75, 2.75, 5.7, x, out) + strip('z', -2.75, 2.75, 6.6, x, out)
    # shorts hems: band y 5.3 - 6.4 on each leg
    for leg in ('right_leg', 'left_leg'):
        L = d['bones'][leg]
        L['children'] = [c for c in L['children'] if not is_diamond(c, 5.73)]
        band = next(c for c in L['cubes'] if abs(c['from'][1] - 5.3) < 0.01 and c['size'][1] > 1.0)
        x0, z0 = band['from'][0], band['from'][2]
        x1, z1 = x0 + band['size'][0], z0 + band['size'][2]
        L['cubes'] += triangles('x', x0, x1, 6.4, z0, -1) + triangles('x', x0, x1, 6.4, z1, 1)
        L['cubes'] += triangles('z', z0, z1, 6.4, x0, -1) + triangles('z', z0, z1, 6.4, x1, 1)
        L['cubes'] += strip('x', x0, x1, 5.3, z0, -1) + strip('x', x0, x1, 5.3, z1, 1) + strip('z', z0, z1, 5.3, x0, -1) + strip('z', z0, z1, 5.3, x1, 1)
        # shoe sole reaching just past the leg (and its pants layer) underneath
        for c in L['cubes']:
            if c['from'][1] >= 11.2 and c['size'][2] >= 6:
                c['size'][1] = round(12.32 - c['from'][1], 4)
                c['uv'] = list(SW['sole']) if False else c['uv']
        L['cubes'].append(cube([-2.62, 12.27, -3.62], [5.04, 0.06, 6.14], 'sole'))   # dark rubber underside
    # bracelet: red, with a dark top edge, a bright rim and the cuff flaring out at the wrist
    for arm in ('right_arm', 'left_arm'):
        A = d['bones'][arm]
        cuff = next((c for c in A['cubes'] if abs(c['from'][1] - 6.6) < 0.01 and c['size'][1] > 3), None)
        if cuff is None: continue
        cuff['uv'] = list(SW['red'])
        x0, z0 = cuff['from'][0], cuff['from'][2]
        w, dz = cuff['size'][0], cuff['size'][2]
        for c in A['cubes']:
            if abs(c['from'][1] - 6.1) < 0.01: c['uv'] = list(SW['dark'])          # top edge
        A['cubes'].append(cube([x0 - 0.25, 9.55, z0 - 0.25], [w + 0.5, 0.65, dz + 0.5], 'red'))   # flare at the wrist
        A['cubes'].append(cube([x0 - 0.27, 10.15, z0 - 0.27], [w + 0.54, 0.08, dz + 0.54], 'hi'))  # bright rim
        A['cubes'].append(cube([x0 + w * 0.3, 7.0, z0 - 0.04], [0.35, 2.4, 0.04], 'hi'))   # shine down the front
    d['trim_v2'] = True
    json.dump(d, open(p, 'w'), separators=(',', ':'))
    print(name, 'done')


if __name__ == '__main__':
    import glob
    for a in glob.glob(RES + 'textures/entity/character/mikiko*_details*.png'):
        if 'hairmask' in a: continue
        paint(a)
    for n in ('mikiko', 'mikiko_foxking'):
        process(n)
    # Rhalix: his soles also stop exactly at the bottom of the leg (the pants layer pokes out under them)
    p = RES + 'models/detail/rhalix.json'
    d = json.load(open(p))
    for leg in ('right_leg', 'left_leg'):
        for c in d['bones'][leg]['cubes']:
            if c['from'][1] >= 11.2 and c['size'][2] >= 6 and c['from'][1] + c['size'][1] < 12.3:
                c['size'][1] = round(12.32 - c['from'][1], 4)
    json.dump(d, open(p, 'w'), separators=(',', ':'))
    print('rhalix soles done')
