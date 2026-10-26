"""Healing pod block: textures, block/item models, blockstate, loot, tags; plus a preview render."""
import json, os, math, random
from PIL import Image, ImageDraw
import numpy as np
RES = '/home/claude/xeno-warriors-mod/src/main/resources/'
A = RES + 'assets/xenowarriors/'; D = RES + 'data/'
T = A + 'textures/block/'; os.makedirs(T, exist_ok=True)
rnd = random.Random(4)
def tex(name, fn):
    im = Image.new('RGBA', (16, 16)); px = im.load()
    for y in range(16):
        for x in range(16): px[x, y] = fn(x, y)
    im.save(T + name + '.png'); return im
n = lambda b: max(0, min(255, b + rnd.randint(-4, 4)))
tex('healing_pod_shell', lambda x, y: (196, 202, 212, 255) if x in (0, 15) or y in (0, 15) else (150, 158, 170, 255) if y == 7 else (n(236), n(240), n(246), 255))
tex('healing_pod_base', lambda x, y: (120, 128, 140, 255) if y < 2 else (60, 220, 130, 255) if y == 3 and 2 <= x <= 13 else (90, 96, 108, 255) if x % 4 == 0 and y > 5 else (n(210), n(214), n(222), 255))
tex('healing_pod_glass', lambda x, y: (220, 255, 230, 200) if (x + y) in (5, 6, 7) else (70, 235, 130, 120) if 0 < x < 15 and 0 < y < 15 else (150, 255, 190, 210))
tex('healing_pod_glow', lambda x, y: (120, 255, 170, 255) if (x * 3 + y * 5) % 7 else (200, 255, 220, 255))

def el(frm, to, t, rot=None, faces='all'):
    f = {d: {"texture": "#" + t, "uv": uv(frm, to, d)} for d in ('north', 'south', 'east', 'west', 'up', 'down')}
    e = {"from": frm, "to": to, "faces": f}
    if rot: e["rotation"] = rot
    return e
def uv(frm, to, d):
    x0, y0, z0 = frm; x1, y1, z1 = to
    if d in ('north', 'south'): return [round(x0 % 16, 2), round(16 - min(16, y1 % 16 or 16), 2), round(min(16, x1), 2), round(16 - y0 % 16, 2)] if y1 - y0 <= 16 else [x0, 0, x1, 16]
    if d in ('east', 'west'): return [round(z0, 2), 0, round(z1, 2), 16]
    return [round(x0, 2), round(z0, 2), round(x1, 2), round(z1, 2)]
TILT = {"angle": 22.5, "axis": "x", "origin": [8, 3, 6]}
elements = [
    el([0, 0, 0], [16, 3, 16], 'base'),                          # plinth
    el([0, 3, 1], [1, 6, 15], 'shell'), el([15, 3, 1], [16, 6, 15], 'shell'),   # side rails
    el([4, 3, 12], [12, 12, 15], 'shell'),                      # back strut
    el([1, 3, 6], [15, 29, 10], 'shell', TILT),                 # the capsule's back shell
    el([2, 5, 5], [14, 27, 6], 'glow', TILT),                   # glowing bed inside
    el([1, 3, 1], [15, 6, 10], 'shell', TILT),                  # foot cap
    el([1, 26, 1], [15, 29, 10], 'shell', TILT),                # head cap
    el([1.5, 6, 1.2], [14.5, 26, 5], 'glass', TILT),            # the green glass dome
    el([3, 7, 0.4], [13, 25, 1.2], 'glass', TILT),              # ...rounded out in front
    el([0.5, 4, 1.5], [1.5, 28, 9.5], 'shell', TILT), el([14.5, 4, 1.5], [15.5, 28, 9.5], 'shell', TILT),   # side rims
]
model = {"parent": "block/block", "render_type": "minecraft:translucent", "ambientocclusion": False,
         "textures": {"particle": "xenowarriors:block/healing_pod_shell", "shell": "xenowarriors:block/healing_pod_shell", "base": "xenowarriors:block/healing_pod_base",
                      "glass": "xenowarriors:block/healing_pod_glass", "glow": "xenowarriors:block/healing_pod_glow"},
         "elements": elements}
os.makedirs(A + 'models/block', exist_ok=True); os.makedirs(A + 'models/item', exist_ok=True)
json.dump(model, open(A + 'models/block/healing_pod.json', 'w'), indent=1)
json.dump({"textures": {"particle": "xenowarriors:block/healing_pod_shell"}}, open(A + 'models/block/healing_pod_upper.json', 'w'))
json.dump({"parent": "xenowarriors:block/healing_pod", "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, -2.5, 0], "scale": [0.45, 0.45, 0.45]},
           "ground": {"scale": [0.3, 0.3, 0.3]}, "fixed": {"scale": [0.5, 0.5, 0.5]}, "thirdperson_righthand": {"rotation": [75, 45, 0], "scale": [0.3, 0.3, 0.3]},
           "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.3, 0.3, 0.3]}}}, open(A + 'models/item/healing_pod.json', 'w'), indent=1)
bs = {"variants": {}}
for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
    bs["variants"][f"facing={f},half=lower"] = {"model": "xenowarriors:block/healing_pod", "y": y}
    bs["variants"][f"facing={f},half=upper"] = {"model": "xenowarriors:block/healing_pod_upper", "y": y}
json.dump(bs, open(A + 'blockstates/healing_pod.json', 'w'), indent=1)
loot = {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "xenowarriors:healing_pod"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}, {"condition": "minecraft:block_state_property", "block": "xenowarriors:healing_pod", "properties": {"half": "lower"}}]}]}
json.dump(loot, open(D + 'xenowarriors/loot_tables/blocks/healing_pod.json', 'w'), indent=1)
for tag in ('mineable/pickaxe', 'needs_diamond_tool'):
    p = D + f'minecraft/tags/blocks/{tag}.json'
    d = json.load(open(p)) if os.path.exists(p) else {"replace": False, "values": []}
    if "xenowarriors:healing_pod" not in d["values"]: d["values"].append("xenowarriors:healing_pod")
    os.makedirs(os.path.dirname(p), exist_ok=True); json.dump(d, open(p, 'w'))
print('assets ok')

# ---------------------------------------------------------------- preview render of block models
TEXC = {}
def tcol(path):
    if path not in TEXC: TEXC[path] = Image.open(path).convert('RGBA')
    return TEXC[path]
def rot_x(a):
    c, s = math.cos(a), math.sin(a); return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
def render_model(model, texmap, yaw=35, pitch=-28, scale=10, size=(360, 420), offset=(0, 0, 0), img=None, center=(180, 300)):
    if img is None: img = Image.new('RGBA', size, (34, 36, 46, 255))
    d = ImageDraw.Draw(img, 'RGBA')
    Y = np.array([[math.cos(math.radians(yaw)), 0, math.sin(math.radians(yaw))], [0, 1, 0], [-math.sin(math.radians(yaw)), 0, math.cos(math.radians(yaw))]])
    P = rot_x(math.radians(pitch))
    quads = []
    for e in model['elements']:
        f0, t0 = np.array(e['from'], float), np.array(e['to'], float)
        R = np.eye(3); o = np.zeros(3)
        if 'rotation' in e:
            R = rot_x(math.radians(e['rotation']['angle'])); o = np.array(e['rotation']['origin'], float)
        corners = lambda pts: [R @ (np.array(p) - o) + o + np.array(offset) for p in pts]
        x0, y0, z0 = f0; x1, y1, z1 = t0
        faces = {'north': [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)], 'south': [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)],
                 'west': [(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)], 'east': [(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)],
                 'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], 'down': [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)]}
        norm = {'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0), 'up': (0, 1, 0), 'down': (0, -1, 0)}
        for fname, pts in faces.items():
            t = texmap[e['faces'][fname]['texture'][1:]]
            im = tcol(t); col = np.array(im.resize((1, 1), Image.LANCZOS).getpixel((0, 0)), float)
            nrm = P @ Y @ (R @ np.array(norm[fname], float))
            sh = 0.55 + 0.45 * max(0, nrm @ np.array([-0.4, 0.8, -0.45]))
            cs = [P @ Y @ (c - np.array([8, 0, 8])) for c in corners(pts)]
            depth = np.mean([c[2] for c in cs])
            quads.append((depth, [(center[0] + c[0] * scale, center[1] - c[1] * scale) for c in cs], (int(col[0] * sh), int(col[1] * sh), int(col[2] * sh), int(col[3]))))
    for _, pts, c in sorted(quads, key=lambda q: q[0], reverse=True):
        d.polygon(pts, fill=c, outline=(20, 22, 30, 160) if c[3] > 200 else None)
    return img
TM = {k: A + 'textures/' + v.split(':')[1] + '.png' for k, v in model['textures'].items()}
if __name__ == '__main__':
    shots = [render_model(model, TM, yaw=y) for y in (30, 150, 270)]
    sheet = Image.new('RGBA', (360 * 3, 420), (34, 36, 46, 255))
    for i, s in enumerate(shots): sheet.paste(s, (i * 360, 0))
    sheet.convert('RGB').save('/tmp/pod_preview.png'); print('preview ok')
