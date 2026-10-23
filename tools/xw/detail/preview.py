import json, math, sys
import numpy as np
from PIL import Image, ImageDraw
sys.path.insert(0, '/home/claude/xw/detail')
from core import BASE, hexc

def rotm(rx, ry, rz):
    rx, ry, rz = map(math.radians, (rx, ry, rz))
    X = np.array([[1, 0, 0], [0, math.cos(rx), -math.sin(rx)], [0, math.sin(rx), math.cos(rx)]])
    Y = np.array([[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]])
    Z = np.array([[math.cos(rz), -math.sin(rz), 0], [math.sin(rz), math.cos(rz), 0], [0, 0, 1]])
    return Z @ Y @ X

FACES = {'north': ([0, 2, 6, 4], (0, 0, -1)), 'south': ([1, 5, 7, 3], (0, 0, 1)),
         'west': ([0, 1, 3, 2], (-1, 0, 0)), 'east': ([4, 6, 7, 5], (1, 0, 0)),
         'down': ([0, 4, 5, 1], (0, -1, 0)), 'up': ([2, 3, 7, 6], (0, 1, 0))}

def corners(f, t, inf=0):
    f = np.array(f) - inf; t = np.array(t) + inf
    return np.array([[x, y, z] for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])])

def face_color_from_skin(skin, uv, w, h, d, face):
    u, v = uv
    reg = {'north': (u + d, v + d, w, h), 'east': (u, v + d, d, h), 'south': (u + 2 * d + w, v + d, w, h),
           'west': (u + d + w, v + d, d, h), 'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d)}[face]
    crop = skin.crop((reg[0], reg[1], reg[0] + reg[2], reg[1] + reg[3])).convert('RGBA')
    a = np.array(crop).reshape(-1, 4)
    a = a[a[:, 3] > 0]
    return tuple(a[:, :3].mean(0).astype(int)) if len(a) else None

def render(name, yaw, out, scale=9):
    spec = json.load(open(f'/home/claude/xw/detail/{name}_spec.json'))
    skin = Image.open(f'/home/claude/xw/tex/{name}.png')
    groups = {g['name']: g for g in spec['groups']}
    polys = []
    light = np.array([0.3, 0.8, -0.5]); light /= np.linalg.norm(light)
    V = rotm(0, yaw, 0)

    def add_box(c, col_fn, rot, origin, grp=None, inf=0):
        P = corners(c[0], c[1], inf)
        R = rotm(*rot)
        P = (P - origin) @ R.T + origin
        Rg = np.eye(3)
        if grp:
            Rg = rotm(*grp['rot']); go = np.array(grp['origin'])
            P = (P - go) @ Rg.T + go
        for fname, (idx, n) in FACES.items():
            col = col_fn(fname)
            if col is None:
                continue
            nn = Rg @ R @ np.array(n)
            q = P[idx] @ V.T
            nv = V @ nn
            if nv[2] > 0:   # facing away (camera looks toward +z)
                continue
            sh = 0.55 + 0.45 * max(0, nn @ light)
            polys.append((q[:, 2].mean(), [(-p[0], p[1]) for p in q], tuple(int(cc * sh) for cc in col)))

    for nm, bone, f, t, uv, inf, ov in BASE:
        if ov and nm not in spec['keep']:
            continue
        w, h, d = [t[i] - f[i] for i in range(3)]
        add_box((f, t), lambda fc: face_color_from_skin(skin, uv, w, h, d, fc), [0, 0, 0], np.zeros(3), None, inf)
    for c in spec['cubes']:
        col = hexc(c['color'])
        add_box((c['frm'], c['to']), lambda fc: col, c['rot'], np.array(c['origin']), groups.get(c['bone']))
    polys.sort(key=lambda p: -p[0])
    W, H = 60 * scale, 46 * scale
    img = Image.new('RGB', (W, H), (24, 22, 30))
    dr = ImageDraw.Draw(img)
    for _, pts, col in polys:
        dr.polygon([(W / 2 + x * scale, H - 3 * scale - y * scale) for x, y in pts], fill=col, outline=tuple(int(c * .8) for c in col))
    img.save(out)

if __name__ == '__main__':
    for name in ('mikiko', 'rhalix'):
        ims = []
        for yaw in (0, 35, 180, 90):
            p = f'/home/claude/xw/detail/prev_{name}_{yaw}.png'
            render(name, yaw, p, 7)
            ims.append(Image.open(p))
        sheet = Image.new('RGB', (sum(i.width for i in ims), ims[0].height))
        x = 0
        for i in ims:
            sheet.paste(i, (x, 0)); x += i.width
        sheet.save(f'/home/claude/xw/detail/prev_{name}.png')
