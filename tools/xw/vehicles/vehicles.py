"""Vehicles for Aurora: car, motorbike, helicopter, Government armored truck (and their Mk II paint).
Builds GeckoLib (.geo.json) models with box-UV textures painted face by face, colour variants, glowmasks (headlights,
dash, Mk II trim), and preview renders. 16 units = 1 block; the model faces -z (front); y up from the ground; +x is
the vehicle's right.

v2 (vehicles2): hollow cabins you can see into (seats, dash, steering wheel / controls, see-through windows), doors
and panel lines, headlights and grilles that stand out, hubcaps that spin with the wheels, a four-blade rotor, and
Mk II tier colours (cyan trim and glowing pinstripes; gold on the Gov truck). Seats sit where the riders are put
(VehicleKind.rawSeats): a seated rider's hips are ~4.4 units (car scale) above their seat spot, thighs ~1.4 below that.

Usage: python3 vehicles.py [--no-preview]   (writes into the repo this script lives in)"""
import json, math, random, os, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
R = REPO + '/src/main/resources/assets/xenowarriors/'
PREV = os.environ.get('XW_PREVIEW_DIR', '/tmp/xw_vehicle_prev')
os.makedirs(R + 'geo', exist_ok=True); os.makedirs(R + 'textures/entity/vehicle', exist_ok=True); os.makedirs(R + 'animations', exist_ok=True)


def C(h):
    if isinstance(h, tuple): return h[:3]
    h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mul(c, f): return tuple(max(0, min(255, int(v * f))) for v in C(c))


# Tier palettes. Tokens a face can use: BODY (paint), BODY2 (darker paint), STRIPE (a body stripe: darker paint, or
# the Mk II glowing accent), TRIM (chrome, or the Mk II accent), DTRIM (dark trim).
TIERS = {
    'base': dict(body=1.0, trim=(186, 190, 198), stripe=None, glow_trim=False),
    'mk2': dict(body=0.8, trim=(60, 230, 255), stripe=(60, 230, 255), glow_trim=True),
    'mk2gov': dict(body=0.85, trim=(255, 200, 60), stripe=(255, 200, 60), glow_trim=True),
}


class Model:
    def __init__(self, name, tex=256):
        self.name, self.tex = name, tex
        self.bones = {'root': dict(pivot=[0, 0, 0], parent=None, cubes=[])}
        self.order = ['root']

    def bone(self, name, pivot, parent='root'):
        self.bones[name] = dict(pivot=pivot, parent=parent, cubes=[]); self.order.append(name)

    def box(self, bone, lo, hi, col, faces=None, glow=None):
        lo2 = [min(a, b) for a, b in zip(lo, hi)]; hi2 = [max(a, b) for a, b in zip(lo, hi)]
        self.bones[bone]['cubes'].append(dict(lo=lo2, hi=hi2, col=col, faces=faces or {}, glow=glow or {}))

    def sym(self, bone, lo, hi, col, faces=None, glow=None):
        """A box and its mirror across x=0 (left/right face paint swapped, and mirrored across the face)."""
        self.box(bone, lo, hi, col, faces, glow)
        def flip(d):
            out = {}
            for k, v in (d or {}).items():
                nk = 'left' if k == 'right' else 'right' if k == 'left' else k
                if callable(v) and k in ('front', 'back', 'top', 'bottom'): out[nk] = (lambda f: (lambda x, y: f(1 - x, y)))(v)
                else: out[nk] = v
            return out
        self.box(bone, [-hi[0], lo[1], lo[2]], [-lo[0], hi[1], hi[2]], col, flip(faces), flip(glow))

    # ------------------------------------------------------------ texture
    def pack(self, variant_col, tier='base'):
        T = TIERS[tier]
        W = self.tex
        im = Image.new('RGBA', (W, W), (0, 0, 0, 0)); px = im.load()
        gm = Image.new('RGBA', (W, W), (0, 0, 0, 0)); gp = gm.load()
        rnd = random.Random(3)
        body = mul(variant_col, T['body'])
        cubes = [c for b in self.order for c in self.bones[b]['cubes']]
        order = sorted(cubes, key=lambda c: -(math.ceil(c['hi'][2] - c['lo'][2] - 1e-6) + math.ceil(c['hi'][1] - c['lo'][1] - 1e-6)))
        x = y = shelf = 0
        for c in order:
            w, h, d = [max(1, math.ceil(c['hi'][k] - c['lo'][k] - 1e-6)) for k in range(3)]
            rw, rh = 2 * d + 2 * w, d + h
            if x + rw > W: x = 0; y += shelf + 1; shelf = 0
            if y + rh > W: raise SystemExit(f'{self.name}: atlas full')
            c['uv'] = [x, y]
            u, v = x, y
            rects = {'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h),
                     'back': (u + 2 * d + w, v + d, w, h), 'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d)}
            for f, (fx, fy, fw, fh) in rects.items():
                spec = c['faces'].get(f, c['col'])
                gspec = c['glow'].get(f)
                for j in range(fh):
                    for i in range(fw):
                        lx, ly = (i + .5) / fw, (j + .5) / fh
                        col = spec(lx, ly) if callable(spec) else spec
                        glow = None
                        if col is None: continue
                        if col == 'BODY': col = body
                        elif col == 'BODY2': col = mul(body, .72)
                        elif col == 'STRIPE':
                            if T['stripe']: col = T['stripe']; glow = T['stripe']
                            else: col = mul(body, .7)
                        elif col == 'TRIM':
                            col = T['trim']
                            if T['glow_trim']: glow = T['trim']
                        elif col == 'DTRIM': col = (36, 36, 40)
                        col = C(col)
                        fac = 1 + (rnd.random() - .5) * .05
                        fac *= 1.06 if f == 'top' else .78 if f == 'bottom' else 1.0 - .08 * ly
                        px[fx + i, fy + j] = mul(col, fac) + (255,)
                        if gspec is not None:
                            gc = gspec(lx, ly) if callable(gspec) else gspec
                            if gc is not None: glow = gc
                        if glow is not None: gp[fx + i, fy + j] = C(glow) + (255,)
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
            'visible_bounds_width': 10, 'visible_bounds_height': 8, 'visible_bounds_offset': [0, 2, 0]}, 'bones': bones}]}

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
                els.append({'from': [-hi[0] + 8, lo[1], lo[2] + 8], 'to': [-lo[0] + 8, hi[1], hi[2] + 8], 'faces': faces})
        return els


# ------------------------------------------------------------ paint helpers
TIRE, TIRE2 = (24, 24, 28), (44, 44, 50)
SEAT, SEAT2 = (58, 50, 46), (78, 68, 60)
DASH, CARPET = (34, 35, 40), (46, 44, 48)
FRAME = (30, 30, 34)
HEAD, HEADG = (250, 250, 225), (255, 255, 230)
TAIL, TAILG = (205, 22, 26), (255, 40, 40)
AMBER = (255, 160, 30)


def glass(x, y):
    """See-through: a pale frame line, two reflection streaks, the rest open (cutout)."""
    if x < .03 or x > .97 or y < .04 or y > .96: return FRAME
    t = (x * 0.8 + y) % 1.0
    if .14 < t < .2 or .3 < t < .33: return (215, 238, 252)
    return None


def tinted(x, y):
    """Darker see-through glass (the truck's armored slits, the helicopter's bubble edges)."""
    if x < .04 or x > .96 or y < .06 or y > .94: return FRAME
    t = (x * 0.8 + y) % 1.0
    if .16 < t < .21: return (170, 200, 220)
    return None


def tire_side(x, y):
    dx, dy = x - .5, y - .5; r = math.hypot(dx, dy)
    if r > .5: return None
    if r > .47: return TIRE2
    return TIRE


def tread(x, y): return TIRE2 if int(y * 10) % 2 else TIRE


def hubcap(spokes=5):
    def f(x, y):
        dx, dy = x - .5, y - .5; r = math.hypot(dx, dy)
        if r > .5: return None
        if r < .12: return (90, 92, 100)
        a = (math.atan2(dy, dx) / (2 * math.pi) * spokes) % 1.0
        if r > .42: return 'TRIM'
        return 'TRIM' if a < .32 else (60, 62, 68)
    return f


def tire_face(r, half_z, half_y):
    """A tyre's side seen on a box half_z x half_y (blocks of the wheel's radius r): round tread, chrome rim, dark hub."""
    def f(x, y):
        zz, yy = (x - .5) * 2 * half_z, (y - .5) * 2 * half_y
        q = math.hypot(zz, yy) / r
        if q > 1.0: return None
        if q > .93: return TIRE2
        if q > .62: return TIRE
        if q > .5: return 'TRIM'
        return (58, 60, 66)
    return f


def wheel(m, name, cx, cy, cz, r, wdt, side, spokes=5, both=False):
    """A tyre - two crossed boxes, so it reads round from the side - with a hubcap on its outer face (both faces for a
    bike), in its own bone (spins with its x rotation, the front ones steer with y)."""
    m.bone(name, [cx, cy, cz])
    x0, x1 = sorted([cx - side * wdt / 2, cx + side * wdt / 2])
    k = 0.58
    m.box(name, [x0, cy - r * k, cz - r], [x1, cy + r * k, cz + r], TIRE,
          {'right': tire_face(r, r, r * k), 'left': tire_face(r, r, r * k), 'front': tread, 'back': tread, 'top': tread, 'bottom': tread})
    m.box(name, [x0 + 0.05, cy - r, cz - r * k], [x1 - 0.05, cy + r, cz + r * k], TIRE,
          {'right': tire_face(r, r * k, r), 'left': tire_face(r, r * k, r), 'front': tread, 'back': tread, 'top': tread, 'bottom': tread})
    hc = r * 0.5
    for sd in ((1, -1) if both else (side,)):
        ox = x1 if sd > 0 else x0
        m.box(name, [ox - (0 if sd > 0 else 0.4), cy - hc, cz - hc], [ox + (0.4 if sd > 0 else 0), cy + hc, cz + hc], 'TRIM',
              {'right': hubcap(spokes), 'left': hubcap(spokes), 'front': 'TRIM', 'back': 'TRIM', 'top': 'TRIM', 'bottom': 'TRIM'})


def seat(m, bone, x0, x1, y0, top, z_front, z_back, back_top, col=SEAT):
    """A seat: cushion (its top at `top`) from z_front to z_back, backrest behind (up to back_top), a headrest."""
    stitch = lambda x, y: SEAT2 if int(x * 6) % 2 == 0 and .2 < y < .8 else col
    m.box(bone, [x0, y0, z_front], [x1, top, z_back], col, {'top': stitch})
    m.box(bone, [x0, top, z_back], [x1, back_top, z_back + 2], col, {'front': lambda x, y: SEAT2 if int(y * 5) % 2 else col})
    w = (x1 - x0) * 0.25
    m.box('fp' if 'fp' in m.bones else bone, [x0 + w, back_top, z_back + 0.5], [x1 - w, back_top + 3, z_back + 1.5], col)


# ------------------------------------------------------------ the car (scale 1.45; seats: front hips z -0.8, rear z 11.2, hips y 6.3)
def car():
    m = Model('car', 256)
    m.bone('fp', [0, 0, 0])                                                    # (hidden for its riders in first person)
    B, B2, S, T = 'BODY', 'BODY2', 'STRIPE', 'TRIM'
    # floor pan and sills
    m.box('root', [-13, 3, -27], [13, 4.5, 27], CARPET, {'bottom': (30, 30, 34)})
    # sides: door panels (panel lines, handles, a stripe) between the wheel arches
    def door(x, y):
        if abs(x - .5) < .012 or abs(x - .14) < .012 or abs(x - .86) < .012: return (30, 30, 34)       # door seams
        if .46 < y < .6 and (.38 < x < .44 or .78 < x < .84): return T                                  # handles
        if .72 < y < .8: return S
        return B
    m.sym('root', [12.5, 4.5, -10], [14, 12.5, 18], B, {'right': door, 'left': door, 'top': B2})
    # nose (engine bay, hood) and tail (trunk), solid
    def nose_front(x, y):
        if y > .82: return 'DTRIM'
        if .2 < y < .62 and (.04 < x < .3 or .7 < x < .96): return B          # (headlights stand out in front of here)
        if .26 < y < .7 and .3 < x < .7: return (24, 24, 28) if int(y * 9) % 2 else (60, 62, 70)   # grille
        return B
    m.box('root', [-14, 4, -29], [14, 12.5, -10], B, {'front': nose_front, 'top': lambda x, y: B2 if abs(x - .3) < .02 or abs(x - .7) < .02 else B,
          'right': lambda x, y: S if .72 < y < .8 else B, 'left': lambda x, y: S if .72 < y < .8 else B})
    def tail_back(x, y):
        if .22 < y < .48 and (x < .22 or x > .78): return TAIL
        if .5 < y < .7 and .38 < x < .62: return (232, 232, 232) if not (.55 < y < .65 and int(x * 30) % 3 == 0) else (40, 40, 44)   # plate
        if y > .82: return 'DTRIM'
        return B
    m.box('root', [-14, 4, 18], [14, 12.5, 29], B, {'back': tail_back, 'top': lambda x, y: B2 if y < .06 else B,
          'right': lambda x, y: S if .72 < y < .8 else B, 'left': lambda x, y: S if .72 < y < .8 else B},
          glow={'back': lambda x, y: TAILG if .22 < y < .48 and (x < .22 or x > .78) else None})
    # headlights (glowing), indicators, bumpers, grille surround
    m.sym('root', [8.5, 7.5, -29.8], [13, 10.5, -29], HEAD, {'front': lambda x, y: HEAD if x > .2 else AMBER, 'right': T, 'left': T, 'top': T, 'bottom': T},
          glow={'front': lambda x, y: HEADG if x > .2 else AMBER})
    m.box('root', [-15, 3.6, -31], [15, 6.6, -28.6], T)                     # bumpers
    m.box('root', [-15, 3.6, 28.6], [15, 6.6, 31], T)
    m.box('root', [-5, 10.6, -29.5], [5, 11.4, -29], T)                       # badge bar over the grille
    m.sym('root', [5, 4.2, 29.5], [6.5, 5.6, 32], (90, 92, 100))              # exhaust (right)
    # cabin: pillars, roof, see-through windows
    for z0, z1 in ((-10, -8.5), (4, 5.5), (16.5, 18)):
        m.sym('fp', [12, 12.5, z0], [13.5, 21.5, z1], B, {'top': B2})
    m.box('fp', [-13, 21, -10.5], [13, 22.5, 18], B, {'top': lambda x, y: B2 if abs(x - .5) < .2 else B, 'bottom': (70, 68, 72)})   # roof
    m.box('fp', [-12, 12.5, -9.8], [12, 21, -9.2], 'DTRIM', {'front': glass, 'back': glass})              # windshield
    m.box('fp', [-12, 12.5, 17.2], [12, 21, 17.8], 'DTRIM', {'front': glass, 'back': glass})              # rear window
    m.sym('fp', [12.9, 12.5, -8.5], [13.3, 21, 4], 'DTRIM', {'right': glass, 'left': glass})              # side windows (front doors)
    m.sym('fp', [12.9, 12.5, 5.5], [13.3, 21, 16.5], 'DTRIM', {'right': glass, 'left': glass})            # (back doors)
    m.sym('fp', [13.5, 13.5, -9.5], [16, 15.5, -7], B, {'front': (190, 200, 210), 'back': (190, 200, 210)})    # mirrors
    # interior: dashboard (glowing dials), steering wheel (driver: left), seats
    def dash_back(x, y):
        if .3 < y < .7 and (abs(x - .22) < .07 or abs(x - .35) < .07): return (70, 200, 255)
        if .3 < y < .7 and .55 < x < .75: return (40, 120, 70)
        return DASH
    m.box('root', [-12.5, 9.5, -10], [12.5, 11.8, -6.5], DASH, {'back': dash_back},
          glow={'back': lambda x, y: (70, 200, 255) if .3 < y < .7 and (abs(x - .22) < .07 or abs(x - .35) < .07) else (40, 160, 90) if .3 < y < .7 and .55 < x < .75 else None})
    m.box('root', [-6.6, 10.5, -6.5], [-5.4, 11.5, -5.4], DASH)                # steering column
    ring = lambda x, y: DASH if math.hypot(x - .5, y - .5) > .34 or abs(x - .5) < .08 else None
    m.box('root', [-8.5, 8.8, -5.4], [-3.5, 13.3, -4.8], DASH, {'front': ring, 'back': ring})
    m.box('root', [-1, 4.5, -10], [1, 9, 2], DASH)                               # centre console, gear stick
    m.box('root', [-0.4, 9, -2], [0.4, 11, -1.2], (20, 20, 24))
    seat(m, 'root', -10, -2.5, 4.5, 5.2, -6, 1.5, 15)                             # front seats
    seat(m, 'root', 2.5, 10, 4.5, 5.2, -6, 1.5, 15)
    seat(m, 'root', -11, 11, 4.5, 5.2, 6.5, 13.5, 15)                             # rear bench
    for i, (x, z) in enumerate([(-14, -18), (14, -18), (-14, 19), (14, 19)]):
        wheel(m, ['wheel_fl', 'wheel_fr', 'wheel_rl', 'wheel_rr'][i], x + (1 if x < 0 else -1), 5, z, 5, 4, -1 if x < 0 else 1)
    # wheel arches over them (dark lip)
    for x, z in [(-14, -18), (14, -18), (-14, 19), (14, 19)]:
        s = -1 if x < 0 else 1
        m.box('root', [s * 12.5, 10, z - 6], [s * 14.6, 11, z + 6], 'DTRIM')
    return m


# ------------------------------------------------------------ the motorbike (scale 1.3; rider hips y ~14.5 at z 2.4, pillion z 9.6)
def bike():
    m = Model('motorbike', 128)
    m.bone('fp', [0, 0, 0])                                                    # (hidden for its riders in first person)
    m.bone('bars', [0, 0, -15.5])                                              # fork, bars, cowl: turn with the front wheel
    B, B2, S, T = 'BODY', 'BODY2', 'STRIPE', 'TRIM'
    m.box('root', [-2.5, 6, -9], [2.5, 11, 6], (54, 54, 60), {'right': lambda x, y: (90, 92, 100) if int(y * 6) % 2 else (54, 54, 60),
          'left': lambda x, y: (90, 92, 100) if int(y * 6) % 2 else (54, 54, 60)})          # engine (fins)
    m.box('root', [-1, 10, -12], [1, 13, 8], (40, 40, 44))                     # frame spine
    m.box('root', [-3.5, 11, -10], [3.5, 15.5, 0], B, {'top': lambda x, y: S if abs(x - .5) < .1 else B,
          'right': lambda x, y: S if .4 < y < .55 else B, 'left': lambda x, y: S if .4 < y < .55 else B})   # tank
    m.box('root', [-3.2, 12, 0], [3.2, 13.2, 13], (24, 22, 26), {'top': lambda x, y: (40, 36, 40) if int(y * 8) % 2 else (24, 22, 26)})   # seat (rider + pillion)
    m.box('root', [-3, 13.2, 7.5], [3, 13.8, 13], (24, 22, 26))               # (pillion step up)
    m.box('root', [-3.2, 10.5, 9], [3.2, 13, 15], B, {'right': lambda x, y: S if .4 < y < .6 else B, 'left': lambda x, y: S if .4 < y < .6 else B})   # tail
    m.box('root', [-2, 10.8, 15], [2, 12.4, 15.6], TAIL, glow={'back': TAILG})   # tail light
    m.sym('bars', [0.7, 7, -17], [1.7, 16, -14], T)                            # fork tubes
    m.box('bars', [-6, 17, -15.5], [6, 18, -14.5], (40, 40, 44))               # handlebar
    m.sym('bars', [5.2, 17.2, -15], [6.2, 18.2, -12.5], (40, 40, 44))          # (bars sweep back to the rider)
    m.sym('bars', [6, 16.9, -13.5], [8.5, 18.5, -11.5], (24, 24, 28))          # grips
    m.sym('bars', [4.4, 18, -15.5], [5.2, 21, -14.7], (40, 40, 44))            # mirror stalks
    m.sym('bars', [3.8, 21, -16], [6, 22.5, -14.5], (190, 200, 210))
    m.box('bars', [-2.8, 13.5, -19], [2.8, 18, -15.5], B, {'front': lambda x, y: HEAD if math.hypot(x - .5, y - .5) < .32 else B},
          glow={'front': lambda x, y: HEADG if math.hypot(x - .5, y - .5) < .32 else None})   # headlight cowl
    m.box('bars', [-2.4, 18, -17.5], [2.4, 21, -16.8], 'DTRIM', {'front': tinted, 'back': tinted})   # windscreen
    m.box('bars', [-1.4, 16.5, -15.5], [1.4, 18, -14.5], (20, 20, 24), {'back': lambda x, y: (70, 200, 255) if .3 < x < .7 else (20, 20, 24)},
          glow={'back': lambda x, y: (70, 200, 255) if .3 < x < .7 else None})   # speedo
    m.box('root', [-2.2, 12, -21], [2.2, 13, -12], B)                          # front mudguard
    m.box('root', [-2.4, 12.6, 13], [2.4, 13.4, 19], B)                        # rear mudguard
    m.box('root', [3, 5, 2], [5, 7, 17], T, {'back': (30, 30, 34)})            # exhaust (right)
    m.sym('root', [2.5, 6.5, -5], [6, 7.2, -3], (40, 40, 44))                  # foot pegs (under the rider's feet)
    m.sym('root', [2.5, 7.5, 9], [5.5, 8.2, 11], (40, 40, 44))                 # pillion pegs
    m.sym('root', [3.2, 13, 11], [3.8, 14.5, 15], T)                           # grab rails (the pillion holds on)
    m.sym('root', [1.9, 5.2, 4], [2.9, 7, 13], (40, 40, 44))                  # swingarm
    m.box('root', [-3, 9, -12], [3, 14, -10.5], (50, 52, 58), {'front': lambda x, y: (30, 30, 34) if int(y * 8) % 2 else (80, 82, 90)})   # radiator
    wheel(m, 'wheel_f', 0, 6, -15, 6, 3, 1, spokes=6, both=True)
    wheel(m, 'wheel_r', 0, 6, 13, 6, 3.6, 1, spokes=6, both=True)
    return m


# ------------------------------------------------------------ the helicopter (scale 1.45; seat rows z -9.6 / 1.9 / 12.8, hips y ~10)
def heli():
    m = Model('helicopter', 512)
    m.bone('fp', [0, 0, 0])                                                    # (hidden for its riders in first person)
    B, B2, S, T = 'BODY', 'BODY2', 'STRIPE', 'TRIM'
    m.box('root', [-12, 5, -16], [12, 6.5, 16], (60, 62, 70))                  # cabin floor
    m.box('root', [-12, 6.5, -16], [12, 8.5, 16], CARPET, {'right': B, 'left': B, 'front': B, 'back': B})
    # cabin walls: the front doors are glass, the back is an open sliding doorway
    side = lambda x, y: S if .1 < y < .2 else B
    m.sym('fp', [11, 8.5, -6], [12, 26, -4.5], B)                             # door pillars
    m.sym('root', [11, 8.5, 14], [12, 26, 16], B, {'right': side, 'left': side})
    m.sym('root', [11, 8.5, -16], [12, 12, -6], B, {'right': side, 'left': side})        # front door, lower
    m.sym('fp', [11.2, 12, -15], [11.8, 25, -6], 'DTRIM', {'right': glass, 'left': glass})   # front door window
    m.sym('root', [11.4, 8.5, 12], [12.6, 25, 15], B, {'right': lambda x, y: (30, 30, 34) if abs(x - .5) < .1 else B, 'left': lambda x, y: (30, 30, 34) if abs(x - .5) < .1 else B})   # slid-back door
    m.sym('fp', [11.6, 24.5, -14], [12.4, 25.2, 15], (40, 40, 44))           # door rail
    m.box('fp', [-12, 26, -16], [12, 28, 16], B, {'top': B2})                  # roof
    m.box('root', [-12, 8.5, 16], [12, 26, 18], B, {'back': B2})                 # back wall
    # nose: a glass bubble, the instrument panel under it
    m.box('root', [-11, 5, -23], [11, 12, -16], B, {'front': lambda x, y: S if .1 < y < .25 else B})   # nose (lower)
    m.box('fp', [-10, 12, -22.6], [10, 24, -22], 'DTRIM', {'front': glass, 'back': glass})           # windscreen
    m.sym('fp', [10, 12, -22.6], [11, 24, -16], 'DTRIM', {'right': glass, 'left': glass})            # side bubble panes
    m.box('fp', [-11, 24, -22.6], [11, 26, -16], B, {'top': B2})               # brow
    m.box('root', [-11, 12, -23], [11, 13, -16], B)                              # sill
    m.box('fp', [-1, 24, -23], [1, 26, -22.6], B)                              # centre frame
    def panel(x, y):
        if .3 < y < .75 and int(x * 8) % 2 == 0: return (70, 200, 255)
        return DASH
    m.box('root', [-10, 12, -20], [10, 14.5, -16.5], DASH, {'back': panel}, glow={'back': lambda x, y: (70, 200, 255) if .3 < y < .75 and int(x * 8) % 2 == 0 else None})
    m.sym('root', [5.5, 8.5, -14], [6.5, 14, -13], DASH)                          # cyclic sticks
    # seats: pilot + co-pilot, two rows behind
    for z_f, z_b in ((-14, -7.6), (-4, 3.9), (7, 14)):
        seat(m, 'root', -10.5, -2, 8.5, 8.8, z_f, z_b, 18, col=(52, 56, 64))
        seat(m, 'root', 2, 10.5, 8.5, 8.8, z_f, z_b, 18, col=(52, 56, 64))
    # landing light, nav lights, the rotor mast
    m.box('root', [-1.5, 5.5, -23.6], [1.5, 7.5, -23], HEAD, glow={'front': HEADG})
    m.box('root', [12, 20, -2], [12.6, 21, -1], (40, 220, 60), glow={'right': (40, 255, 60), 'left': (40, 255, 60)})
    m.box('root', [-12.6, 20, -2], [-12, 21, -1], (230, 30, 30), glow={'right': (255, 40, 40), 'left': (255, 40, 40)})
    m.box('root', [-5, 28, -7], [5, 32, 7], (60, 62, 70), {'right': lambda x, y: (40, 40, 44) if int(x * 8) % 2 else (60, 62, 70), 'left': lambda x, y: (40, 40, 44) if int(x * 8) % 2 else (60, 62, 70)})   # engine housing
    m.box('root', [-1, 32, -1], [1, 33, 1], (40, 40, 44))
    # tail boom (tapering), fin, stabilisers
    m.box('root', [-4, 16, 18], [4, 24, 30], B, {'right': lambda x, y: S if .4 < y < .55 else B, 'left': lambda x, y: S if .4 < y < .55 else B})
    m.box('root', [-3, 17.5, 30], [3, 23, 44], B, {'right': lambda x, y: S if .4 < y < .55 else B, 'left': lambda x, y: S if .4 < y < .55 else B})
    m.box('root', [-2, 18.5, 44], [2, 22, 56], B, {'right': lambda x, y: S if .4 < y < .55 else B, 'left': lambda x, y: S if .4 < y < .55 else B})
    m.box('root', [-1, 22, 48], [1, 34, 56], B, {'right': lambda x, y: S if y < .15 else B, 'left': lambda x, y: S if y < .15 else B})   # fin
    m.box('root', [-9, 20, 46], [9, 21.5, 50], B2, {'top': lambda x, y: S if x < .1 or x > .9 else B2})   # stabiliser
    m.box('root', [-0.5, 33, 51], [0.5, 34, 52], (255, 40, 40), glow={'top': (255, 60, 60), 'back': (255, 60, 60)})   # beacon
    # skids and struts (the hangers grip these)
    m.sym('root', [9, 0, -18], [11.5, 1.6, 15], (60, 62, 70), {'front': T, 'top': lambda x, y: T if y < .05 else (60, 62, 70)})
    m.sym('root', [9, 0.8, -21], [11.5, 2.4, -18], (60, 62, 70))               # (skid tips curl up)
    for z in (-11, 8):
        m.sym('root', [9.6, 1.6, z], [11, 5, z + 1.6], (60, 62, 70))
    # main rotor: hub and four blades
    m.bone('rotor', [0, 33, 0])
    m.box('rotor', [-2, 33, -2], [2, 35, 2], (40, 40, 44))
    blade = lambda x, y: (230, 60, 40) if x < .04 or x > .96 else (34, 34, 38)
    m.box('rotor', [-58, 34, -1.8], [58, 34.8, 1.8], (34, 34, 38), {'top': blade, 'bottom': blade})
    m.box('rotor', [-1.8, 34.9, -58], [1.8, 35.7, 58], (34, 34, 38), {'top': lambda x, y: blade(y, x), 'bottom': lambda x, y: blade(y, x)})
    m.box('rotor', [-1, 35, -1], [1, 36.5, 1], 'TRIM')
    # tail rotor (on the left of the fin)
    m.bone('tail_rotor', [-1.6, 28, 53])
    m.box('tail_rotor', [-2.4, 27, 52], [-1, 29, 54], (40, 40, 44))
    m.box('tail_rotor', [-2.2, 21, 52.6], [-1.6, 35, 53.4], (34, 34, 38), {'right': lambda x, y: (230, 60, 40) if y < .08 or y > .92 else (34, 34, 38), 'left': lambda x, y: (230, 60, 40) if y < .08 or y > .92 else (34, 34, 38)})
    m.box('tail_rotor', [-2.2, 27.6, 46], [-1.6, 28.4, 60], (34, 34, 38), {'right': lambda x, y: (230, 60, 40) if x < .08 or x > .92 else (34, 34, 38), 'left': lambda x, y: (230, 60, 40) if x < .08 or x > .92 else (34, 34, 38)})
    return m


# ------------------------------------------------------------ the Gov armored truck (scale 1.35; cab seats z -23.2, crew z -3.2, hips y ~8.7)
def truck():
    m = Model('gov_truck', 512)
    m.bone('fp', [0, 0, 0])                                                    # (hidden for its riders in first person)
    B, B2, S, T = 'BODY', 'BODY2', 'STRIPE', 'TRIM'
    RED = (180, 20, 24)
    def armor(x, y):
        if .44 < y < .54: return RED
        if .58 < y < .62: return S
        if (x * 12) % 1 < .06 or (y * 7) % 1 < .05: return (56, 58, 64)          # armor plate seams
        if ((x * 12) % 1 > .85 and (y * 7) % 1 > .8): return (120, 124, 132)    # rivets
        return B
    m.box('root', [-16, 6, -40], [16, 7.5, 40], (44, 46, 52))                     # chassis / floor
    m.box('root', [-15, 7.5, -38], [15, 8, 38], CARPET)
    # cargo / crew box: walls with firing ports, rear doors, roof (the riders on top stand on it)
    port = lambda x, y: (20, 20, 24) if .3 < y < .42 and (.15 < x < .35 or .6 < x < .8) else armor(x, y)
    m.sym('fp', [15.5, 7.5, -14], [17, 34, 40], B, {'right': port, 'left': port, 'top': B2})
    m.box('fp', [-17, 34, -14], [17, 36, 40], B2, {'top': lambda x, y: (56, 58, 64) if (x * 8) % 1 < .05 else B2})
    def rear(x, y):
        if abs(x - .5) < .01: return (30, 30, 34)
        if .2 < y < .3 and (.2 < x < .4 or .6 < x < .8): return tinted(((x - .2) % .2) * 5, (y - .2) * 10) or (20, 20, 24)
        if .44 < y < .54: return RED
        if y > .82 and (x < .1 or x > .9): return TAIL
        if .55 < y < .65 and (.42 < x < .46 or .54 < x < .58): return T
        return B
    m.box('root', [-15.5, 7.5, 38.5], [15.5, 34, 40], B, {'back': rear, 'front': (60, 62, 68)},
          glow={'back': lambda x, y: TAILG if y > .82 and (x < .1 or x > .9) else None})
    m.box('fp', [-15.5, 26, -14], [15.5, 34, -13], B, {'back': (60, 62, 68)})                      # bulkhead (upper; the crew sees the cab)
    # cab: armored, slit windscreen, side windows, roof with the light bar
    def cab_front(x, y):
        if y > .62 and (x < .16 or x > .84): return B
        if .7 < y < .92 and .28 < x < .72: return (24, 24, 28) if int(x * 14) % 2 else (60, 62, 70)   # grille
        if .45 < y < .52: return RED
        return B
    m.box('root', [-16, 7.5, -42], [16, 18, -34], B, {'front': cab_front, 'right': armor, 'left': armor, 'top': B2})   # engine block (lower front)
    m.sym('fp', [15.5, 18, -40], [16.5, 30, -14], B, {'right': lambda x, y: armor(x, y) if not (y < .5 and .1 < x < .85) else None, 'left': lambda x, y: armor(x, y) if not (y < .5 and .15 < x < .9) else None})
    m.sym('fp', [15.7, 23.5, -38], [16.3, 29, -16], 'DTRIM', {'right': tinted, 'left': tinted})       # side windows
    m.box('root', [-16, 18, -40], [16, 19.5, -39], B)                                                  # under the windscreen
    m.box('fp', [-14, 19.5, -40.2], [14, 29, -39.6], 'DTRIM', {'front': tinted, 'back': tinted})    # windscreen
    m.box('fp', [-16, 29, -41], [16, 31, -14], B, {'top': B2})                                       # cab roof
    m.box('fp', [-6, 31, -36], [6, 32.5, -32], RED, {'top': lambda x, y: (40, 120, 255) if x > .5 else (255, 40, 40)},
          glow={'top': lambda x, y: (60, 140, 255) if x > .5 else (255, 40, 40), 'front': lambda x, y: (60, 140, 255) if x > .5 else (255, 40, 40)})   # light bar
    m.sym('root', [10, 13, -42.6], [14.5, 17, -42], HEAD, glow={'front': HEADG})                      # headlights
    m.box('root', [-18, 7, -45], [18, 11, -42], (40, 40, 44))                                          # bull bar
    for x in (-13, -5, 3, 11): m.box('root', [x, 11, -44.5], [x + 2, 20, -42.5], (40, 40, 44))
    m.box('root', [-12, 21, -41.5], [12, 23, -40.2], (40, 40, 44))
    m.sym('fp', [16.5, 22, -38], [19, 25, -36], (40, 40, 44), {'front': (190, 200, 210)})          # mirrors
    # interior: dash, wheel (left), cab seats, the crew benches along the walls
    m.box('root', [-15, 14, -39], [15, 17, -36], DASH, {'back': lambda x, y: (70, 200, 255) if .4 < y < .7 and .15 < x < .35 else DASH},
          glow={'back': lambda x, y: (70, 200, 255) if .4 < y < .7 and .15 < x < .35 else None})
    ring = lambda x, y: DASH if math.hypot(x - .5, y - .5) > .34 or abs(x - .5) < .08 else None
    m.box('root', [-10, 12.5, -34.5], [-4, 17.5, -33.8], DASH, {'front': ring, 'back': ring})
    seat(m, 'root', -12, -3, 8, 9.5, -30, -21, 22, col=(52, 56, 48))
    seat(m, 'root', 3, 12, 8, 9.5, -30, -21, 22, col=(52, 56, 48))
    seat(m, 'root', -14, -2, 8, 9.5, -8, 1, 22, col=(52, 56, 48))
    seat(m, 'root', 2, 14, 8, 9.5, -8, 1, 22, col=(52, 56, 48))
    m.sym('root', [11, 8, 6], [15.5, 12, 36], (52, 56, 48))                                            # benches
    m.box('fp', [-17.2, 34, -14], [17.2, 35, -13], T)                                                # (edge trim)
    m.box('fp', [-5, 36, 4], [5, 37.5, 14], (30, 30, 34))                                            # roof hatch
    for i, (x, z) in enumerate([(-16, -27), (16, -27), (-16, 27), (16, 27)]):
        wheel(m, ['wheel_fl', 'wheel_fr', 'wheel_rl', 'wheel_rr'][i], x + (0.5 if x < 0 else -0.5), 7, z, 7, 6, -1 if x < 0 else 1, spokes=6)
    for x, z in [(-16, -27), (16, -27), (-16, 27), (16, 27)]:
        s = -1 if x < 0 else 1
        m.box('root', [s * 15, 14.5, z - 8], [s * 18, 16, z + 8], (40, 40, 44))   # fenders
    return m


VARIANTS = {
    'car': {'red': (190, 24, 30), 'blue': (30, 80, 190), 'black': (32, 32, 38), 'white': (228, 228, 232), 'yellow': (232, 190, 30)},
    'motorbike': {'red': (190, 24, 30), 'black': (32, 32, 38), 'green': (40, 170, 90), 'orange': (230, 120, 30)},
    'helicopter': {'skyblue': (70, 140, 210), 'white': (226, 228, 232), 'black': (36, 36, 42)},
    'gov_truck': {'gov': (70, 74, 82)},
}
BUILD = {'car': car, 'motorbike': bike, 'helicopter': heli, 'gov_truck': truck}

if __name__ == '__main__':
    preview = '--no-preview' not in sys.argv
    os.makedirs(PREV, exist_ok=True)
    if preview: sys.path.insert(0, '/home/claude/xw/items'); import model_preview as MP
    sheet = []
    for key, fn in BUILD.items():
        m = fn()
        for vname, col in VARIANTS[key].items():
            for tier, suffix in (('base', ''), ('mk2gov' if key == 'gov_truck' else 'mk2', '_mk2')):
                tex, glow = m.pack(col, tier)
                tex.save(R + f'textures/entity/vehicle/{key}{suffix}_{vname}.png'); glow.save(R + f'textures/entity/vehicle/{key}{suffix}_{vname}_glowmask.png')
                if vname == list(VARIANTS[key])[0]: tex.save(f'{PREV}/{key}{suffix}_prev.png')
        json.dump(m.geo(), open(R + f'geo/{key}.geo.json', 'w'))
        json.dump({'format_version': '1.8.0', 'animations': {}}, open(R + f'animations/{key}.animation.json', 'w'))
        json.dump({'textures': {}, 'elements': m.elements()}, open(f'{PREV}/{key}_prev.json', 'w'))
        print(key, 'cubes', sum(len(m.bones[b]['cubes']) for b in m.order))
        if preview:
            ims = []
            sc = {'car': 4.2, 'motorbike': 7, 'helicopter': 2.4, 'gov_truck': 3.0}[key]
            for yaw, tx in ((-35, ''), (145, ''), (-35, '_mk2')):
                ims.append(MP.render(f'{PREV}/{key}_prev.json', f'{PREV}/{key}{tx}_prev.png', f'{PREV}/{key}{tx}_{yaw}.png', yaw=yaw, pitch=-22, scale=sc, size=440))
            sheet.append(ims)
    if preview:
        S = Image.new('RGB', (1320, 440 * len(sheet)), (34, 38, 46))
        for i, row in enumerate(sheet):
            for j, im in enumerate(row): S.paste(im, (j * 440, i * 440))
        S.save(f'{PREV}/vehicles.png')
    print('ok')
