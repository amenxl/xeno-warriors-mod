"""Vehicles for Aurora: car, motorbike, helicopter, Government armored truck.
Builds GeckoLib (.geo.json) models with box-UV textures painted face by face, colour variants, glowmasks (headlights),
and preview renders. 16 units = 1 block; the model faces -z (front); y up from the ground."""
import json, math, random, os, sys
from PIL import Image, ImageDraw

R = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
os.makedirs(R + 'geo', exist_ok=True); os.makedirs(R + 'textures/entity/vehicle', exist_ok=True); os.makedirs(R + 'animations', exist_ok=True)


def C(h):
    if isinstance(h, tuple): return h[:3]
    h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mul(c, f): return tuple(max(0, min(255, int(v * f))) for v in C(c))


class Model:
    def __init__(self, name, tex=256):
        self.name, self.tex = name, tex
        self.bones = {'root': dict(pivot=[0, 0, 0], parent=None, cubes=[])}
        self.order = ['root']

    def bone(self, name, pivot, parent='root'):
        self.bones[name] = dict(pivot=pivot, parent=parent, cubes=[]); self.order.append(name)

    def box(self, bone, lo, hi, col, faces=None, glow=None, inflate=0):
        lo = [min(a, b) - inflate for a, b in zip(lo, hi)]; hi = [max(a, b) + inflate for a, b in zip(lo, hi)]
        self.bones[bone]['cubes'].append(dict(lo=lo, hi=hi, col=col, faces=faces or {}, glow=glow or {}))

    def sym(self, bone, lo, hi, col, faces=None, glow=None):
        """A box and its mirror across x=0."""
        self.box(bone, lo, hi, col, faces, glow)
        mf = {('left' if k == 'right' else 'right' if k == 'left' else k): v for k, v in (faces or {}).items()}
        mg = {('left' if k == 'right' else 'right' if k == 'left' else k): v for k, v in (glow or {}).items()}
        self.box(bone, [-hi[0], lo[1], lo[2]], [-lo[0], hi[1], hi[2]], col, mf, mg)

    # ------------------------------------------------------------ texture
    def pack(self, variant_col=None):
        W = self.tex
        im = Image.new('RGBA', (W, W), (0, 0, 0, 0)); px = im.load()
        gm = Image.new('RGBA', (W, W), (0, 0, 0, 0)); gp = gm.load()
        rnd = random.Random(3)
        cubes = [c for b in self.order for c in self.bones[b]['cubes']]
        order = sorted(cubes, key=lambda c: -(math.ceil(c['hi'][2] - c['lo'][2]) + math.ceil(c['hi'][1] - c['lo'][1])))
        x = y = shelf = 0
        for c in order:
            w, h, d = [math.ceil(c['hi'][k] - c['lo'][k] - 1e-6) for k in range(3)]
            w, h, d = max(w, 1), max(h, 1), max(d, 1)
            rw, rh = 2 * d + 2 * w, d + h
            if x + rw > W: x = 0; y += shelf + 1; shelf = 0
            if y + rh > W: raise SystemExit(f'{self.name}: atlas full')
            c['uv'] = [x, y]
            u, v = x, y
            rects = {'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h),
                     'back': (u + 2 * d + w, v + d, w, h), 'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d)}
            for f, (fx, fy, fw, fh) in rects.items():
                spec = c['faces'].get(f, c['col'])
                for j in range(fh):
                    for i in range(fw):
                        lx, ly = (i + .5) / fw, (j + .5) / fh
                        col = spec(lx, ly) if callable(spec) else spec
                        if col == 'BODY': col = variant_col
                        if col is None: continue
                        if isinstance(col, str) and col == 'BODY2': col = mul(variant_col, .72)
                        col = C(col)
                        fac = 1 + (rnd.random() - .5) * .05
                        fac *= 1.06 if f == 'top' else .78 if f == 'bottom' else 1.0 - .1 * ly
                        px[fx + i, fy + j] = mul(col, fac) + (255,)
                        gs = c['glow'].get(f)
                        if gs is not None:
                            gc = gs(lx, ly) if callable(gs) else gs
                            if gc is not None: gp[fx + i, fy + j] = C(gc) + (255,)
            x += rw + 1; shelf = max(shelf, rh)
        return im, gm

    # ------------------------------------------------------------ export
    def geo(self):
        bones = []
        for b in self.order:
            node = self.bones[b]
            px, py, pz = node['pivot']
            out = {'name': b, 'pivot': [-px, py, pz]}
            if node['parent']: out['parent'] = node['parent']
            cubes = []
            for c in node['cubes']:
                lo, hi = c['lo'], c['hi']
                cubes.append({'origin': [round(-hi[0], 4), round(lo[1], 4), round(lo[2], 4)],
                              'size': [round(hi[k] - lo[k], 4) for k in range(3)], 'uv': c['uv']})
            if cubes: out['cubes'] = cubes
            bones.append(out)
        return {'format_version': '1.12.0', 'minecraft:geometry': [{'description': {
            'identifier': f'geometry.{self.name}', 'texture_width': self.tex, 'texture_height': self.tex,
            'visible_bounds_width': 8, 'visible_bounds_height': 6, 'visible_bounds_offset': [0, 2, 0]}, 'bones': bones}]}

    def elements(self):
        """Item-model style elements for the preview renderer (all 16-scaled per face uv)."""
        T = self.tex
        els = []
        for b in self.order:
            for c in self.bones[b]['cubes']:
                lo, hi = c['lo'], c['hi']
                w, h, d = [max(1, math.ceil(hi[k] - lo[k] - 1e-6)) for k in range(3)]
                u, v = c['uv']
                rects = {'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h), 'west': (u + d + w, v + d, d, h),
                         'south': (u + 2 * d + w, v + d, w, h), 'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d)}
                faces = {f: {'uv': [fx * 16 / T, fy * 16 / T, (fx + fw) * 16 / T, (fy + fh) * 16 / T]} for f, (fx, fy, fw, fh) in rects.items()}
                els.append({'from': [lo[0] + 8, lo[1], lo[2] + 8], 'to': [hi[0] + 8, hi[1], hi[2] + 8], 'faces': faces})
        return els


# ------------------------------------------------------------ helpers
GLASS, GLASS2 = (150, 200, 230), (110, 160, 200)
TIRE, RIM = (26, 26, 30), (170, 172, 180)
def glass(x, y): return GLASS if (x + y * 0.6) % 0.5 > 0.12 else (210, 235, 250)
def tire_side(x, y):
    dx, dy = x - .5, y - .5; r = math.hypot(dx, dy)
    return RIM if r < .22 else (60, 60, 66) if r < .27 else TIRE if r < .5 else None
def tread(x, y): return (40, 40, 44) if int(y * 8) % 2 else TIRE


def wheel(m, name, cx, cy, cz, r, wdt, side):
    m.bone(name, [cx, cy, cz])
    x0, x1 = sorted([cx - side * wdt / 2, cx + side * wdt / 2])
    m.box(name, [x0, cy - r, cz - r], [x1, cy + r, cz + r], TIRE, {'right': tire_side, 'left': tire_side, 'front': tread, 'back': tread, 'top': tread, 'bottom': tread})


# ------------------------------------------------------------ the car
def car():
    m = Model('car', 256)
    B, B2 = 'BODY', 'BODY2'
    def side_stripe(x, y): return B2 if 0.62 < y < 0.72 else B
    m.box('root', [-14, 4, -28], [14, 13, 28], B, {'right': side_stripe, 'left': side_stripe,
          'front': lambda x, y: (250, 250, 220) if y < .4 and (x < .2 or x > .8) else (30, 30, 34) if y > .55 and .25 < x < .75 else B,
          'back': lambda x, y: (200, 20, 24) if y < .35 and (x < .22 or x > .78) else (230, 230, 230) if .4 < y < .6 and .38 < x < .62 else B},
          glow={'front': lambda x, y: (255, 255, 230) if y < .4 and (x < .2 or x > .8) else None,
                'back': lambda x, y: (255, 40, 40) if y < .35 and (x < .22 or x > .78) else None})
    # cabin with windows
    win = lambda x, y: glass(x, y) if .15 < y < .85 and .06 < x < .94 else B
    m.box('root', [-12.5, 13, -10], [12.5, 22, 17], B, {'front': win, 'back': win, 'right': lambda x, y: glass(x, y) if .18 < y < .85 and (.06 < x < .46 or .54 < x < .94) else B,
          'left': lambda x, y: glass(x, y) if .18 < y < .85 and (.06 < x < .46 or .54 < x < .94) else B, 'top': B2})
    m.box('root', [-14.4, 4, -29.5], [14.4, 7, -27.5], (40, 40, 44))         # bumpers
    m.box('root', [-14.4, 4, 27.5], [14.4, 7, 29.5], (40, 40, 44))
    m.sym('root', [13.5, 14, -9], [16, 15.5, -6], B)                         # mirrors
    m.box('root', [-10, 13, -27], [10, 13.6, -12], B2)                       # hood crease
    for i, (x, z) in enumerate([(-14, -17), (14, -17), (-14, 18), (14, 18)]):
        wheel(m, ['wheel_fl', 'wheel_fr', 'wheel_rl', 'wheel_rr'][i], x + (0.5 if x < 0 else -0.5), 5, z, 5, 5, -1 if x < 0 else 1)
    return m


def bike():
    m = Model('motorbike', 128)
    B, B2 = 'BODY', 'BODY2'
    m.box('root', [-2.5, 7, -10], [2.5, 12, 9], (50, 50, 56))                # frame / engine
    m.box('root', [-3.5, 11, -9], [3.5, 16, 1], B, {'top': lambda x, y: B2 if abs(x - .5) < .1 else B})   # tank
    m.box('root', [-3, 14, 1], [3, 15.5, 11], (24, 22, 26))                  # seat
    m.box('root', [-3.2, 12, 9], [3.2, 15, 14], B)                            # tail
    m.box('root', [-1.5, 12, 14], [1.5, 13.5, 14.6], (200, 20, 24), glow={'back': (255, 40, 40)})   # tail light
    m.box('root', [-1.2, 8, -16], [1.2, 18, -13.5], RIM)                      # fork
    m.box('root', [-7, 18, -15.5], [7, 19.2, -14], (40, 40, 44))             # handlebar
    m.box('root', [-2.6, 14.5, -18], [2.6, 18, -15.5], B, {'front': lambda x, y: (250, 250, 220) if .2 < x < .8 and .2 < y < .8 else B},
          glow={'front': lambda x, y: (255, 255, 230) if .2 < x < .8 and .2 < y < .8 else None})   # headlight cowl
    m.box('root', [3, 6, 2], [5, 8, 15], (150, 150, 160))                     # exhaust
    wheel(m, 'wheel_f', 0, 6, -15, 6, 3, 1)
    wheel(m, 'wheel_r', 0, 6, 12, 6, 3.4, 1)
    return m


def heli():
    m = Model('helicopter', 512)
    B, B2 = 'BODY', 'BODY2'
    def side(x, y):
        if .12 < y < .55 and .1 < x < .62: return glass(x, y)
        return B2 if .72 < y < .8 else B
    m.box('root', [-12, 6, -16], [12, 26, 16], B, {'right': side, 'left': lambda x, y: side(1 - x, y),
          'front': lambda x, y: glass(x, y) if y < .7 and .06 < x < .94 else B, 'back': B2})
    m.box('root', [-10, 8, -22], [10, 22, -16], B, {'front': lambda x, y: glass(x, y) if .1 < y < .8 and .08 < x < .92 else B,
          'top': lambda x, y: glass(x, y), 'right': lambda x, y: glass(x, y) if y < .7 else B, 'left': lambda x, y: glass(x, y) if y < .7 else B})   # nose
    m.box('root', [-3.5, 17, 16], [3.5, 23, 56], B, {'right': lambda x, y: B2 if .4 < y < .6 else B, 'left': lambda x, y: B2 if .4 < y < .6 else B})   # tail boom
    m.box('root', [-1, 23, 48], [1, 34, 56], B)                               # fin
    m.box('root', [-8, 21, 50], [8, 22.5, 54], B2)                             # stabiliser
    m.box('root', [-4, 26, -6], [4, 30, 6], (60, 62, 70))                      # rotor mast housing
    m.sym('root', [9, 0, -16], [12, 2, 14], (60, 62, 70))                      # skids
    m.sym('root', [9.5, 2, -10], [11, 7, -8], (60, 62, 70))
    m.sym('root', [9.5, 2, 6], [11, 7, 8], (60, 62, 70))
    m.box('root', [-1.5, 7, -22.5], [1.5, 8.5, -21.8], (250, 250, 220), glow={'front': (255, 255, 230)})   # landing light
    m.bone('rotor', [0, 30, 0])
    m.box('rotor', [-1.5, 30, -1.5], [1.5, 33, 1.5], (40, 40, 44))
    m.box('rotor', [-56, 31, -2], [56, 32, 2], (34, 34, 38), {'top': lambda x, y: (230, 60, 40) if x < .04 or x > .96 else (34, 34, 38)})
    m.box('rotor', [-2, 31.2, -56], [2, 32.2, 56], (34, 34, 38), {'top': lambda x, y: (230, 60, 40) if y < .04 or y > .96 else (34, 34, 38)})
    m.bone('tail_rotor', [4.5, 28, 53])
    m.box('tail_rotor', [4, 27, 52.5], [5, 29, 53.5], (40, 40, 44))
    m.box('tail_rotor', [4.2, 20, 52.6], [4.8, 36, 53.4], (34, 34, 38))
    m.box('tail_rotor', [4.2, 27.6, 45], [4.8, 28.4, 61], (34, 34, 38))
    return m


def truck():
    m = Model('gov_truck', 512)
    B, B2 = 'BODY', 'BODY2'
    RED = (180, 20, 24)
    def armor(x, y):
        if .44 < y < .54: return RED
        return (60, 62, 68) if (int(x * 10) + int(y * 6)) % 2 == 0 and (x * 10) % 1 < .06 else B
    def back(x, y):
        if .1 < x < .48 or .52 < x < .9: c = B
        else: c = (30, 30, 34)
        if .44 < y < .54: c = RED
        return (200, 20, 24) if y > .8 and (x < .1 or x > .9) else c
    m.box('root', [-17, 6, -14], [17, 34, 40], B, {'right': armor, 'left': armor, 'back': back, 'top': B2},
          glow={'back': lambda x, y: (255, 40, 40) if y > .8 and (x < .1 or x > .9) else None})   # armored cargo box
    def cab_front(x, y):
        if y < .45 and .08 < x < .92: return glass(x, y)
        if y > .62 and (x < .16 or x > .84): return (250, 250, 220)
        if .7 < y < .9 and .3 < x < .7: return (40, 40, 44)
        return B
    m.box('root', [-16, 6, -40], [16, 28, -14], B, {'front': cab_front, 'right': lambda x, y: glass(x, y) if y < .42 and .15 < x < .7 else armor(x, y),
          'left': lambda x, y: glass(x, y) if y < .42 and .3 < x < .85 else armor(x, y)},
          glow={'front': lambda x, y: (255, 255, 230) if y > .62 and (x < .16 or x > .84) else None})
    m.box('root', [-17.5, 6, -42], [17.5, 10, -39], (40, 40, 44))             # bull bar
    for x in (-12, -4, 4, 12): m.box('root', [x - 1, 10, -41.5], [x + 1, 18, -39.5], (40, 40, 44))
    m.box('root', [-6, 34, 0], [6, 36, 12], (30, 30, 34))                      # roof hatch
    m.box('root', [-4, 28, -36], [4, 29.5, -30], (200, 20, 24), glow={'top': (255, 40, 40), 'front': (255, 40, 40)})   # light bar
    # GOV emblem on the sides (white "G" shield)
    for i, (x, z) in enumerate([(-17, -26), (17, -26), (-17, 26), (17, 26)]):
        wheel(m, ['wheel_fl', 'wheel_fr', 'wheel_rl', 'wheel_rr'][i], x + (0.5 if x < 0 else -0.5), 7, z, 7, 6, -1 if x < 0 else 1)
    return m


VARIANTS = {
    'car': {'red': (190, 24, 30), 'blue': (30, 80, 190), 'black': (32, 32, 38), 'white': (228, 228, 232), 'yellow': (232, 190, 30)},
    'motorbike': {'red': (190, 24, 30), 'black': (32, 32, 38), 'green': (40, 170, 90), 'orange': (230, 120, 30)},
    'helicopter': {'skyblue': (70, 140, 210), 'white': (226, 228, 232), 'black': (36, 36, 42)},
    'gov_truck': {'gov': (70, 74, 82)},
}
BUILD = {'car': car, 'motorbike': bike, 'helicopter': heli, 'gov_truck': truck}

if __name__ == '__main__':
    sys.path.insert(0, '/home/claude/xw/items'); import model_preview as MP
    sheet = []
    for key, fn in BUILD.items():
        m = fn()
        first = True
        for vname, col in VARIANTS[key].items():
            tex, glow = m.pack(col)
            tex.save(R + f'textures/entity/vehicle/{key}_{vname}.png'); glow.save(R + f'textures/entity/vehicle/{key}_{vname}_glowmask.png')
            if first:
                json.dump(m.geo(), open(R + f'geo/{key}.geo.json', 'w'))
                json.dump({'format_version': '1.8.0', 'animations': {}}, open(R + f'animations/{key}.animation.json', 'w'))
                mj = {'textures': {}, 'elements': m.elements()}
                json.dump(mj, open(f'/tmp/{key}_prev.json', 'w'))
                first = False
            if vname == list(VARIANTS[key])[0]:
                tex.save(f'/tmp/{key}_prev.png')
        # previews: front 3/4 and back 3/4
        ims = []
        sc = {'car': 4.2, 'motorbike': 7, 'helicopter': 2.4, 'gov_truck': 3.0}[key]
        for yaw in (-35, 145):
            ims.append(MP.render(f'/tmp/{key}_prev.json', f'/tmp/{key}_prev.png', f'/tmp/{key}_{yaw}.png', yaw=yaw, pitch=-22, scale=sc, size=440))
        sheet.append(ims)
        print(key, 'ok')
    S = Image.new('RGB', (880, 440 * len(sheet)), (34, 38, 46))
    for i, row in enumerate(sheet):
        for j, im in enumerate(row): S.paste(im, (j * 440, i * 440))
    S.save('/tmp/vehicles.png')
