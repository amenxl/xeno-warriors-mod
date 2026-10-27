"""The townspeople (11) and the permanently possessed (4): skins, 3D hair/outfits, HD faces, dark possessed variants."""
import sys, json, math, random, os
sys.path.insert(0, '/home/claude/xw/outfits'); sys.path.insert(0, '/home/claude/xw/detail')
from kit import Fit, pack, C, mul, r4, BB_PIV, PARTS, face_rects, preview
import core
from PIL import Image

RES = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
TEX = RES + 'textures/entity/npc/'
MOD = RES + 'models/detail/npc/'
os.makedirs(TEX, exist_ok=True); os.makedirs(MOD, exist_ok=True)
BONE = {'Head': 'head', 'Body': 'body', 'RightArm': 'right_arm', 'LeftArm': 'left_arm', 'RightLeg': 'right_leg', 'LeftLeg': 'left_leg'}

class Adapter:
    """core.lock / core.strand talk to a Model; this passes their cubes on to a Fit."""
    def __init__(self, fit): self.fit = fit
    def add(self, bone, name, frm, to, color, rot=None, origin=None, shade=None):
        self.fit.add(BONE.get(bone, bone), frm, to, color, rot=rot if rot and any(rot) else None, origin=origin)

# ------------------------------------------------------------------ painting helpers
def skin_painter(spec):
    S = C(spec['skin']); HAIR = C(spec['hair'][0])
    def fn(part, face, x, y, W, H, outer):
        r = spec['paint'](part, face, x, y, W, H, outer)
        if r is not None: return r
        return S
    return fn

def paint_skin(spec):
    rnd = random.Random(7)
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); px = im.load()
    S = C(spec['skin']); HAIR = C(spec['hair'][-1])
    # head: skin face/sides, hair on top and back (the 3D hair sits over it)
    for face, (x0, y0, W, H) in face_rects(0, 0, 8, 8, 8).items():
        for y in range(H):
            for x in range(W):
                c = HAIR if face in ('top', 'back') or (face in ('right', 'left') and (y < 3 or (x < 3 if face == 'right' else x > 4))) else S
                if face == 'bottom': c = mul(S, .85)
                px[x0 + x, y0 + y] = mul(c, 1 + (rnd.random() - .5) * .06) + (255,)
    fn = skin_painter(spec)
    for part, (u, v, w, h, d, outer) in PARTS.items():
        for face, (x0, y0, W, H) in face_rects(u, v, w, h, d).items():
            for y in range(H):
                for x in range(W):
                    c = C(fn(part, face, x, y, W, H, outer))
                    px[x0 + x, y0 + y] = mul(c, 1 + (rnd.random() - .5) * .07) + (255,)
    return im

def clothes(shirt=None, sleeve=4, pants=None, pants_len=12, shoes=None, shoe_rows=2, extra=None):
    """A painter from simple parts: shirt colour + sleeve length (rows), trousers + length, shoes. `extra` overrides."""
    def fn(part, face, x, y, W, H, outer):
        if extra:
            r = extra(part, face, x, y, W, H, outer)
            if r is not None: return r
        if part == 'body':
            return shirt if face != 'top' or not (2 <= x <= 5 and y >= 1) else None
        if part in ('rarm', 'larm'):
            if face == 'top': return shirt
            return shirt if y < sleeve else None
        if part in ('rleg', 'lleg'):
            if face == 'top': return pants
            if face == 'bottom': return '#222024'
            if y >= 12 - shoe_rows: return shoes
            return pants if y < pants_len else None
        return None
    return fn

# ------------------------------------------------------------------ faces (32x32 in a 64x64 sheet)
def face_img(spec, dark=False):
    S = C(spec['skin']); iris = C(spec['eyes'])
    if dark or spec.get('dark'):
        S = (24, 20, 26); iris = (235, 20, 24)
    K = (25, 18, 18) if not (dark or spec.get('dark')) else (8, 6, 8)
    W = (244, 240, 238) if not (dark or spec.get('dark')) else (60, 6, 8)
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); px = im.load()
    for y in range(32):
        for x in range(32):
            if 6 <= y < 30 and 2 <= x < 30: px[x, y] = S + (255,)
    def put(rows, x0, y0, mirror=False, cmap=None):
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == '.': continue
                x = x0 + (len(row) - 1 - i if mirror else i)
                px[x, y0 + j] = cmap[ch] + (255,)
    cm = {'k': K, 'w': W, 'o': iris, 'O': tuple(min(255, int(c * 1.35) + 30) for c in iris), 'd': mul(iris, .6), 'p': mul(iris, .25) if not dark else (90, 0, 0),
          'W': (255, 255, 255) if not (dark or spec.get('dark')) else (255, 90, 90), 'm': mul(S, .55), 'h': mul(S, .82)}
    brow = spec.get('brow', ['..kkkkk..', 'kk.....k.'])
    eye = spec.get('eye', ['..kkkkkk.', 'kkkkkkkkk', '.wddpddw.', '.wWWoodw.', '.wWoopow.', '..wOOOw..', '..kkkkk..'])
    if (dark or spec.get('dark')) and spec.get('dark_eye'): eye = spec['dark_eye']
    put(brow, 3, 10, False, cm); put(brow, 20, 10, True, cm)
    put(eye, 3, 14, False, cm); put(eye, 20, 14, True, cm)
    put(spec.get('mouth', ['.kkkkkk.']), spec.get('mouth_x', 12), spec.get('mouth_y', 24), False, cm)
    for extra in spec.get('face_extra', []):
        extra(px, cm, dark)
    return im

def darken(img, tint=(0.16, 0.12, 0.18), keep=None):
    """The possessed look: everything drained toward black-purple (keep=callable(rgb)->bool leaves some colours, e.g. red eyes)."""
    out = img.copy(); px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0: continue
            if keep and keep((r, g, b)): continue
            l = (r * .3 + g * .5 + b * .2)
            px[x, y] = (int(l * tint[0] + 10), int(l * tint[1] + 6), int(l * tint[2] + 12), a)
    return out

# ------------------------------------------------------------------ hair
def hair(f, top, base, fringe=True, back_len=26.0, side_len=27.0, spikes=(), locks=(), seed=1):
    """A cap over the head plus locks/spikes: top/base colours; spikes and locks are (root, dir, length, width)."""
    m = Adapter(f)
    A = lambda frm, to, c: f.add('head', frm, to, c)
    A([-4.6, 32.0, -4.6], [4.6, 33.0, 4.6], top)
    A([-4.6, back_len, 4.0], [4.6, 32.0, 4.7], base)
    for s in (1, -1):
        x0, x1 = sorted([s * 4.0, s * 4.7])
        A([x0, side_len, -4.6], [x1, 32.0, 4.7], base)
    if fringe: A([-4.6, 30.6, -4.75], [4.6, 32.0, -4.0], top)
    for root, d, ln, w in spikes:
        core.lock(m, 'Head', root, d, ln, w, w * 0.8, (top, top, base), wide='x')
    for root, d, ln, w in locks:
        core.lock(m, 'Head', root, d, ln, w, 0.8, (base, base, top), wide='x', taper=(0.8, 0.55), tip_len=(0.3, 0.2))

def fringe_locks(n, y, spread, length, width, tilt=0.15, z=-4.7):
    # (kept short, and rooted high, so the fringe stops above the eyes)
    y = max(y, 31.8); length = min(length * 0.55, 2.4)
    out = []
    for i in range(n):
        t = (i / (n - 1) - 0.5) if n > 1 else 0
        out.append(([t * spread, y, z], [t * tilt * 4, -1, -0.25], length, width))
    return out

def crown_spikes(n, length, width, y=32.6, r=3.2, up=0.9, seed=3):
    rnd = random.Random(seed); out = []
    for i in range(n):
        a = i / n * math.tau + rnd.random() * 0.3
        x, z = math.sin(a) * r, -math.cos(a) * r * 0.9
        out.append(([x, y, z], [math.sin(a) * 0.8, up, -math.cos(a) * 0.8], length * (0.8 + rnd.random() * 0.4), width))
    return out

# ------------------------------------------------------------------ clothing pieces
def shell(f, bone, lo, hi, inf, col, faces=None):
    f.add(bone, [a - inf for a in lo], [b + inf for b in hi], col, faces)

def tee(f, col, sleeve_bottom=20.5, cuff=None, body_bottom=12.4):
    shell(f, 'body', [-4, body_bottom, -2], [4, 24, 2], 0.28, col)
    f.both('right_arm', [3.62, sleeve_bottom, -2.38], [8.38, 24.38, 2.38], col)
    if cuff: f.both('right_arm', [3.52, sleeve_bottom - 0.4, -2.48], [8.48, sleeve_bottom + 0.4, 2.48], cuff)

def pants(f, col, bottom=1.6, cuff=None, baggy=0.25):
    f.both('right_leg', [-0.3 - baggy + .05, bottom, -2.3 - baggy + .05], [4.3 + baggy - .05, 12.3, 2.3 + baggy - .05], col)
    if cuff: f.both('right_leg', [-0.45 - baggy, bottom - 0.5, -2.45 - baggy], [4.45 + baggy, bottom + 0.4, 2.45 + baggy], cuff)

def shoes(f, col, sole='#222024', h=1.8, toe=True, lace=None):
    f.both('right_leg', [-0.32, 0, -2.6], [4.32, h, 2.32], col, {'top': col, 'front': (lambda x, y: lace if lace and .3 < x < .7 and y < .6 else col) if lace else col})
    if toe: f.both('right_leg', [0.15, 0, -3.15], [3.85, h * 0.6, -2.6], col)
    f.both('right_leg', [-0.4, 0, -3.25], [4.4, 0.35, 2.4], sole)

def suit_vest(f, col, shirt='#f2f2f2', tie='#141416', buttons='#e8e8e8'):
    vest = lambda x, y: (shirt if .42 < x < .58 and y < .18 else tie if .46 < x < .54 and y < .5 else buttons if .46 < x < .54 and int(y * 10) % 3 == 0 else col)
    shell(f, 'body', [-4, 12.2, -2], [4, 24, 2], 0.3, col, {'front': vest})
    f.add('body', [-1.4, 23.2, -2.62], [1.4, 24.6, -2.2], shirt)               # collar
    f.both('right_arm', [3.6, 20.2, -2.4], [8.4, 24.4, 2.4], col)
    f.both('right_arm', [3.5, 19.7, -2.5], [8.5, 20.4, 2.5], shirt)            # white cuffs

# ================================================================== the characters
def P(c): return c   # (readability)

CH = {}

CH['capboy'] = dict(skin='#f3dcd6', eyes='#22c45a', hair=['#121522', '#1c2236'],
    paint=clothes('#f4f4f8', 4, '#2b2a44', 10, '#b8d6f0', 2, extra=lambda part, face, x, y, W, H, o:
        ('#9a92d8' if part == 'body' and face == 'front' and ((x - 3.5) ** 2 + (y - 4) ** 2) < 3.5 and abs(x - 3.5) > 0.9 else None) if part == 'body'
        else ('#4a3cc8' if part in ('rarm', 'larm') and y in (9, 10) else None)),
    brow=['.kkkkk...', 'kk...kk..'], mouth=['.kkkkk.', 'kwwwwwk', '.kkkkk.'], mouth_x=13, mouth_y=24)
def capboy_fit(f):
    hair(f, '#121522', '#1c2236', fringe=False, locks=fringe_locks(4, 30.8, 6.5, 2.6, 1.3) + [([4.4, 30, -2], [0.7, -1, -0.3], 3.4, 1.2)])
    stripe = lambda x, y: '#f2f2f2' if abs(x - .5) < .06 or abs(x - .2) < .04 or abs(x - .8) < .04 else '#121214'
    f.add('head', [-4.8, 31.6, -4.8], [4.8, 34.6, 4.8], '#121214', {'front': stripe, 'back': stripe, 'top': stripe, 'right': stripe, 'left': stripe})
    f.add('head', [-4.4, 31.6, -8.2], [4.4, 32.1, -4.6], '#f2f2f2')                # bill
    f.add('head', [-0.6, 34.6, -0.6], [0.6, 35.1, 0.6], '#f2f2f2')                 # button
    tee(f, '#f4f4f8', 20.6, '#4a3cc8')
    f.both('right_arm', [3.65, 13.0, -2.35], [8.35, 14.2, 2.35], '#4a3cc8')          # wristbands
    pants(f, '#2b2a44', 2.0); shoes(f, '#b8d6f0', '#6a8ac0')
CH['capboy']['fit'] = capboy_fit

CH['redhair'] = dict(skin='#d9935e', eyes='#b8521c', hair=['#5e1414', '#7a2020'],
    paint=clothes('#8a8a8e', 3, '#1a1a1c', 12, '#141414', 2, extra=lambda part, face, x, y, W, H, o:
        ('#f2f2f2' if part == 'body' and face == 'front' and y == 0 else None) if part == 'body'
        else ('#d9935e' if part in ('rleg', 'lleg') and face == 'front' and y == 6 and x in (1, 2) else None)),
    mouth=['.kk.', 'kmmk', '.kk.'], mouth_x=14, mouth_y=23)
def redhair_fit(f):
    hair(f, '#7a2020', '#4a0e0e', spikes=crown_spikes(7, 3.2, 1.4, seed=5) + [([-3, 32, -2], [-1, 0.4, 0], 4.2, 1.6), ([3, 32, -2], [1, 0.3, 0], 4.4, 1.6)],
         locks=fringe_locks(5, 31, 7, 4.8, 1.5, 0.3) + [([s * 4.5, 30, z], [s * 0.5, -1, 0.2], 5.5, 1.4) for s in (1, -1) for z in (-2, 1, 3)], back_len=24)
    tee(f, '#8a8a8e', 21.5, '#7a7a7e'); f.add('body', [-2.2, 23.3, -2.65], [2.2, 24.5, -2.2], '#f2f2f2')
    pants(f, '#1a1a1c', 1.6, baggy=0.3); shoes(f, '#141414', '#2a2a2a')
CH['redhair']['fit'] = redhair_fit

CH['messy'] = dict(skin='#d48e5a', eyes='#3a2416', hair=['#2a1a16', '#3a2620'],
    paint=clothes('#8c8c90', 4, '#1a1a1c', 10, '#222226', 2, extra=lambda part, face, x, y, W, H, o:
        ('#b2181c' if part == 'body' and face in ('front', 'back') and abs((x if face == 'front' else 7 - x) - (y * 0.66)) < 0.8 else None) if part == 'body'
        else ('#1c1c20' if part in ('rarm', 'larm') and y < 4 else None)),
    mouth=['kwwwwk', '.kkkk.'], mouth_x=13, mouth_y=23)
def messy_fit(f):
    hair(f, '#3a2620', '#22140f', spikes=crown_spikes(10, 3.0, 1.5, seed=9) + [([0, 33, -1], [0.1, 1, 0.3], 3.6, 0.8)],
         locks=fringe_locks(6, 31, 7.5, 4.2, 1.4, 0.35) + [([s * 4.6, 31, -1], [s * 0.8, -0.6, 0], 3.8, 1.4) for s in (1, -1)])
    tee(f, '#8c8c90', 20.5, '#1c1c20')
    f.both('right_arm', [3.6, 20.0, -2.4], [8.4, 24.4, 2.4], '#1c1c20')
    f.add('body', [-4.5, 17.5, -2.55], [4.5, 18.3, 2.55], '#b2181c', rot=[0, 0, -35], origin=[0, 18, 0])       # bag strap
    pants(f, '#1a1a1c', 2.6, '#1a1a1c', baggy=0.4); shoes(f, '#222226', '#f0f0f0', 2.4, lace='#f0f0f0')
CH['messy']['fit'] = messy_fit

STAFF = dict(paint=clothes('#26262a', 3, '#2a2a2e', 12, '#6a6a80', 2, extra=lambda part, face, x, y, W, H, o:
        ('#f2f2f2' if face == 'front' and y == 0 and 2 <= x <= 5 else '#101012' if face == 'front' and x in (3, 4) and y < 6 else None) if part == 'body'
        else ('#f2f2f2' if part in ('rarm', 'larm') and y == 3 else None)))
def staff_body(f, shoes_col='#6a6a80'):
    suit_vest(f, '#26262a')
    pants(f, '#2a2a2e', 1.6); shoes(f, shoes_col, '#3a3a4a', lace='#9a9ab0')

CH['spiky'] = dict(skin='#c99070', eyes='#4a6ad8', hair=['#141416', '#1e1e22'], **STAFF,
    mouth=['kwwwwwwk', '.kkkkkk.'], mouth_x=12, mouth_y=23)
def spiky_fit(f):
    hair(f, '#1e1e22', '#121214', spikes=[([0, 33, 0], [0.15, 1, 0.1], 7.5, 2.4), ([-2, 32.6, 0], [-0.7, 1, 0], 5.2, 1.8), ([2.5, 32.6, 0], [0.8, 1, 0.2], 5.0, 1.8),
                                            ([0, 32, 3], [0, 0.6, 1], 4.5, 1.8), ([-4.4, 30, 0], [-1, 0.2, 0], 4.0, 1.4), ([4.4, 30, 0], [1, 0.3, 0], 4.0, 1.4)],
         locks=fringe_locks(4, 31, 6, 3.2, 1.4, 0.4))
    for s in (1, -1):                                                              # round sunglasses on his head
        f.add('head', [s * 0.4 if s > 0 else -3.4, 31.4, -5.0], [3.4 if s > 0 else -0.4, 33.4, -4.6], '#2a2a30', {'front': lambda x, y: '#9a9aa8' if x < .4 and y < .4 else '#3a3a44'})
    f.add('head', [-0.5, 32.3, -5.0], [0.5, 32.6, -4.7], '#141416')
    staff_body(f)
    f.add('right_arm', [3.5, 12.6, -2.5], [8.5, 15.2, 2.5], '#ececec', {k: (lambda x, y: '#cfcfd4' if int(y * 6) % 2 else '#ececec') for k in ('front', 'back', 'right', 'left')})   # bandaged wrist
CH['spiky']['fit'] = spiky_fit

CH['waver'] = dict(skin='#ecc0b8', eyes='#6a6a72', hair=['#121214', '#1c1c20'], **STAFF,
    brow=['.kkkkkk..', 'k......k.'], mouth=['.kkkk.', '..w...'], mouth_x=13, mouth_y=24)
def waver_fit(f):
    hair(f, '#1c1c20', '#121214', back_len=25.5, side_len=26.0, locks=fringe_locks(5, 31.2, 7.5, 3.2, 1.6, 0.2) + [([s * 4.6, 29, z], [s * 0.2, -1, 0], 3.2, 1.6) for s in (1, -1) for z in (-2.5, 0.5)])
    f.add('head', [-4.9, 30.5, -4.9], [4.9, 33.3, 4.9], '#1c1c20')                 # full, rounded bob
    staff_body(f)
CH['waver']['fit'] = waver_fit

CH['gunner'] = dict(skin='#d99a76', eyes='#e8e8ec', hair=['#5a3420', '#e8d2a8'],
    paint=clothes('#18181a', 3, '#262a1c', 9, '#141414', 2, extra=lambda part, face, x, y, W, H, o:
        ('#e8e8e8' if part == 'body' and face in ('front', 'back') and x in (1, 6) else None) if part == 'body'
        else ('#141414' if part in ('rarm', 'larm') and y >= 10 else None)),
    eye=['..kkkkkk.', 'kkkkkkkkk', '.wwwwwww.', '.wWwwwww.', '.wwwwkww.', '..kkkkk..', ''], mouth=['kwwwwwk.', '.kkkkk..'], mouth_x=12, mouth_y=23)
def gunner_fit(f):
    hair(f, '#5a3420', '#e8d2a8', fringe=False, spikes=crown_spikes(9, 2.4, 1.4, seed=11), back_len=27)
    for s in (1, -1): f.add('head', [s * 4.0 if s > 0 else -4.75, 27.0, -4.6], [4.75 if s > 0 else -4.0, 31.0, 4.0], '#e8d2a8')   # blond sides
    tee(f, '#18181a', 21.4)
    for s in (1, -1): f.add('body', [s * 2.0 - 0.4, 12.6, -2.55], [s * 2.0 + 0.4, 24.3, 2.55], '#e8e8e8')        # harness straps
    f.both('right_arm', [3.62, 11.9, -2.32], [8.38, 13.4, 2.32], '#141414')         # gloves
    pants(f, '#262a1c', 4.5, '#262a1c', baggy=0.35); shoes(f, '#141414', '#f0f0f0')
CH['gunner']['fit'] = gunner_fit

CH['briefcase'] = dict(skin='#a8704e', eyes='#2a3040', hair=['#1a3ab8', '#2a52d8'],
    paint=clothes('#2a1a18', 12, '#2a1a18', 12, '#141414', 2, extra=lambda part, face, x, y, W, H, o:
        ('#f2f2f2' if face == 'front' and y < 2 and 3 <= x <= 4 else '#2a2a2e' if face == 'front' and x in (3, 4) and y < 6 else None) if part == 'body' else
        ('#a8704e' if part in ('rarm', 'larm') and y == 11 else None)),
    brow=['kkkkkkk..', '.......k.'], mouth=['.kkkk.'], mouth_x=13, mouth_y=25)
def briefcase_fit(f):
    hair(f, '#2a52d8', '#16309a', spikes=crown_spikes(6, 2.6, 1.6, seed=13), locks=fringe_locks(5, 31, 7.5, 4.0, 1.6, 0.35) + [([s * 4.6, 30.5, z], [s * 0.6, -1, 0], 3.6, 1.5) for s in (1, -1) for z in (-2, 1.5)])
    shell(f, 'body', [-4, 11.2, -2], [4, 24, 2], 0.32, '#2a1a18', {'front': lambda x, y: '#f2f2f2' if .4 < x < .6 and y < .15 else '#2a2a2e' if .45 < x < .55 and y < .5 else '#2a1a18'})
    f.both('right_arm', [3.6, 13.0, -2.4], [8.4, 24.4, 2.4], '#2a1a18')
    pants(f, '#2a1a18', 1.6); shoes(f, '#141414', '#2a2a2a')
CH['briefcase']['fit'] = briefcase_fit

CH['woman'] = dict(skin='#a86a4a', eyes='#5a3a2a', hair=['#141416', '#b8b8bc'],
    paint=clothes('#e0605a', 10, '#6a6a6e', 12, '#141414', 2, extra=lambda part, face, x, y, W, H, o:
        ('#f2f2f2' if part == 'body' and y == 11 else None) if part == 'body' else None),
    brow=['.kkkkkk..', 'k........'], mouth=['k.....k', '.kwwwk.'], mouth_x=12, mouth_y=23)
def woman_fit(f):
    hair(f, '#141416', '#2a2a2e', back_len=21, side_len=23, locks=[([s * 4.6, 30, z], [s * 0.35, -1, 0.1], 7.0, 1.6) for s in (1, -1) for z in (-2.5, -0.5, 1.5)]
         + [([x, 30, 4.6], [0, -1, 0.15], 7.5, 1.8) for x in (-2.8, 0, 2.8)])
    f.add('head', [-4.6, 31.0, -4.8], [4.6, 32.2, -4.3], '#141416')                 # swept-back front
    # grey ends on the long hair: drawn as tips over the locks
    for s in (1, -1):
        for z in (-2.5, -0.5, 1.5): f.add('head', [s * 4.6 - 0.8, 22.4, z - 0.6], [s * 4.6 + 0.8, 25.0, z + 0.6], '#c0c0c4')
    tee(f, '#e0605a', 14.5, '#c84a46')
    f.add('body', [-4.4, 12.0, -2.4], [4.4, 12.8, 2.4], '#f2f2f2')                   # ruffle
    pants(f, '#6a6a6e', 1.8); shoes(f, '#141414', '#f0f0f0')
CH['woman']['fit'] = woman_fit

CH['girl'] = dict(skin='#f2cdbc', eyes='#1a2448', hair=['#7a4a2a', '#8e5a36'],
    paint=clothes('#f28a3c', 0, '#f2a060', 6, '#9a5a2a', 3, extra=lambda part, face, x, y, W, H, o:
        ('#f2f2f2' if part in ('rleg', 'lleg') and y in (6, 7, 8) else None)),
    eye=['..kkkkkk.', 'kkkkkkkkk', '.kooppok.', '.kWopook.', '.kooooOk.', '..kkkkk..', ''], mouth=['.k.k.', '..k..'], mouth_x=14, mouth_y=24)
def girl_fit(f):
    hair(f, '#8e5a36', '#6a3e22', back_len=26, side_len=26.5, spikes=[([0.5, 33, -1], [0.3, 1, -0.2], 4.0, 0.6)],
         locks=fringe_locks(5, 31, 7.5, 3.6, 1.6, 0.25) + [([s * 4.6, 30, z], [s * 0.3, -1, 0], 4.0, 1.6) for s in (1, -1) for z in (-2.5, 0.5, 3)])
    shell(f, 'body', [-4, 13.5, -2], [4, 24, 2], 0.26, '#f28a3c', {'front': lambda x, y: '#f2c08a' if y > .85 else '#f28a3c'})
    f.add('body', [-4.4, 12.6, -2.4], [4.4, 13.6, 2.4], '#f2a060')
    pants(f, '#f2a060', 6.0, '#f2f2f2', baggy=0.2); shoes(f, '#9a5a2a', '#5a3216', 3.2)
CH['girl']['fit'] = girl_fit

CH['phone'] = dict(skin='#f2dcd6', eyes='#6a4026', hair=['#141416', '#1e1e22'], **STAFF,
    eye=['.........', 'kkkkkkkkk', '.wdddddw.', '.wWdpddw.', '..wwwww..', '..kkkkk..', ''], mouth=['.kkkk.'], mouth_x=13, mouth_y=24)
def phone_fit(f):
    hair(f, '#1e1e22', '#121214', locks=[([-1.5, 32.4, -4.5], [0.9, -0.4, -0.4], 5.0, 2.0), ([1.5, 31.6, -4.6], [0.6, -1, -0.2], 3.2, 1.6)]
         + [([s * 4.6, 30.5, z], [s * 0.5, -1, 0], 3.0, 1.5) for s in (1, -1) for z in (-2, 1)], spikes=crown_spikes(5, 2.0, 1.4, seed=17))
    staff_body(f)
CH['phone']['fit'] = phone_fit

CH['soldier'] = dict(skin='#e2dcdc', eyes='#3a3a3a', hair=['#7a4a2e', '#9a6440'],
    paint=clothes('#3a3a40', 12, '#3a3a40', 12, '#5a5a62', 4, extra=None),
    eye=['', '', 'kkkkkkkk.', '.kkkkkk..', '', '', ''], mouth=['.kkkkk.', 'kwwwwwk', '.kkkkk.'], mouth_x=13, mouth_y=24)
def soldier_fit(f):
    hair(f, '#9a6440', '#6a3e24', spikes=crown_spikes(7, 2.8, 1.5, seed=19) + [([1, 33, -2], [0.8, 0.6, -0.6], 3.6, 1.2)], locks=fringe_locks(3, 31, 5, 2.4, 1.4))
    plate = lambda x, y: '#6a6a72' if x < .1 or x > .9 or y < .1 else '#4a4a52'
    shell(f, 'body', [-4, 12.0, -2], [4, 24, 2], 0.4, '#3a3a40', {'front': lambda x, y: '#5a5a62' if (x - .5) ** 2 + (y - .3) ** 2 < .02 else '#3a3a40'})
    f.add('body', [-3.4, 16.0, -2.75], [3.4, 23.0, -2.4], '#4a4a52', {'front': plate})                       # chest plate
    f.add('body', [-4.6, 11.4, -2.6], [4.6, 12.8, 2.6], '#2a2a2e', {'front': lambda x, y: '#8a8a92' if .4 < x < .6 else '#2a2a2e'})   # belt
    f.add('body', [1.2, 8.8, -2.8], [3.4, 11.6, -2.3], '#4a4a52')                                              # tasset
    f.both('right_arm', [3.3, 20.2, -2.7], [8.7, 24.9, 2.7], '#4a4a52', {'top': '#6a6a72'})                     # pauldrons
    f.both('right_arm', [3.55, 13.0, -2.45], [8.45, 17.0, 2.45], '#4a4a52')                                     # bracers
    f.both('right_arm', [3.7, 11.9, -2.3], [8.3, 13.1, 2.3], '#2a2a2e')                                         # gloves
    f.add('left_leg', [-4.8, 6.5, -1.2], [-4.2, 9.5, 1.2], '#4a4a52')                                         # thigh pouch
    pants(f, '#3a3a40', 4.5)
    f.both('right_leg', [-0.4, 0, -2.7], [4.4, 5.0, 2.45], '#5a5a62', {'front': lambda x, y: '#3a3a40' if .55 < y < .65 else '#5a5a62'})
    f.both('right_leg', [-0.5, 0, -3.2], [4.5, 0.45, 2.5], '#2a2a2e')
CH['soldier']['fit'] = soldier_fit

# ---------------- the permanently possessed
D_SKIN = '#141016'
CH['dark_slime'] = dict(skin=D_SKIN, eyes='#ff1414', hair=['#0c0a0c', '#141214'], dark=True,
    paint=clothes('#3a0606', 3, '#0c0a0c', 7, '#3a0606', 2, extra=lambda part, face, x, y, W, H, o:
        ('#5a0a0a' if part in ('rarm', 'larm') and y in (9, 10) else None)),
    eye=['', '', '...rrr...', '...rrr...', '...rrr...', '...rrr...', ''],
    mouth=['......'], face_extra=[])
def slime_face(px, cm, dark):
    for y in range(18, 30):
        for x in range(2, 30):
            if x - 2 > (y - 18) * 2.2 + 4: px[x, y] = (58, 4, 6, 255)       # the red lower half under the zipper
    for i in range(12):
        x, y = 8 + i * 2, 17 + i
        if 2 <= x < 30 and y < 30: px[x, y] = (40, 52, 64, 255)
    for y in range(16, 22):
        for x in range(12, 15): px[x, y] = (230, 10, 12, 255)              # one big red eye
CH['dark_slime']['face_extra'] = [slime_face]
CH['dark_slime']['eye'] = ['', '', '', '', '', '', '']
def slime_fit(f):
    hair(f, '#141214', '#0c0a0c', back_len=24, side_len=25, spikes=crown_spikes(6, 4.0, 1.8, seed=23, up=0.7) + [([-4, 32, 2], [-1, 0.5, 0.4], 6.0, 2.4)],
         locks=[([x, 29, -4.7], [0, -1, -0.1], ln, 1.2) for x, ln in ((-3, 5), (-1, 3), (2, 6), (3.6, 4))] + [([s * 4.7, 29, z], [s * 0.2, -1, 0], 6.5, 1.4) for s in (1, -1) for z in (-1, 2)])
    tee(f, '#3a0606', 21.4)
    f.both('right_arm', [3.6, 13.0, -2.4], [8.4, 14.4, 2.4], '#5a0a0a')
    pants(f, '#0c0a0c', 6.0, '#0c0a0c', baggy=0.4); shoes(f, '#3a0606', '#0c0a0c', 2.2)
CH['dark_slime']['fit'] = slime_fit

def dark_eyes():
    return ['', 'kkkkkkkkk', '.koooook.', '.kOooppk.', '..kkkkk..', '', '']
CH['dark_vest'] = dict(skin=D_SKIN, eyes='#ff1414', hair=['#14101e', '#2a2240'], dark=True,
    paint=clothes('#4a4210', 12, '#0c0a0c', 9, '#2a2a2e', 2, extra=lambda part, face, x, y, W, H, o:
        (D_SKIN if part in ('rarm', 'larm') else None)), eye=dark_eyes(), mouth=['.kk.'], mouth_x=14, mouth_y=25)
def vest_fit(f):
    hair(f, '#2a2240', '#14101e', locks=[([-2, 32.5, -4.6], [0.9, -0.5, -0.3], 6.0, 2.6)] + [([s * 4.6, 30.5, z], [s * 0.4, -1, 0], 3.6, 1.6) for s in (1, -1) for z in (-2, 1)])
    shell(f, 'body', [-4, 12.4, -2], [4, 24, 2], 0.28, '#4a4210', {'front': lambda x, y: D_SKIN if abs(x - .5) < .22 - y * .3 and y < .45 else '#4a4210'})
    pants(f, '#0c0a0c', 2.0, baggy=0.3); shoes(f, '#2a2a2e', '#14141a')
CH['dark_vest']['fit'] = vest_fit

CH['dark_kick'] = dict(skin=D_SKIN, eyes='#ff1414', hair=['#10121c', '#22284a'], dark=True,
    paint=clothes('#0c0c10', 3, '#0c0c10', 12, '#0c0c10', 2, extra=lambda part, face, x, y, W, H, o:
        ('#1a5a64' if part in ('body', 'rarm', 'larm') and y in (2, 5) and face != 'top' else None)), eye=dark_eyes(),
    mouth=['.kkk.', 'k...k'], mouth_x=13, mouth_y=24)
def kick_fit(f):
    hair(f, '#22284a', '#10121c', spikes=crown_spikes(6, 3.0, 1.6, seed=29), locks=fringe_locks(6, 31, 7.5, 5.0, 1.6, 0.4) + [([s * 4.6, 30, z], [s * 0.6, -1, 0], 4.5, 1.6) for s in (1, -1) for z in (-2, 1)])
    tee(f, '#0c0c10', 21.0, '#1a5a64')
    pants(f, '#0c0c10', 1.8); shoes(f, '#0c0c10', '#1a5a64', lace='#1a5a64')
CH['dark_kick']['fit'] = kick_fit

CH['dark_hood'] = dict(skin=D_SKIN, eyes='#ff1414', hair=['#1a1010', '#4a2a20'], dark=True,
    paint=clothes('#2a1640', 4, '#0c0a0c', 12, '#2a1640', 2, extra=lambda part, face, x, y, W, H, o:
        ('#6a1a1a' if part in ('rarm', 'larm') and y == 3 else '#3a3a40' if part in ('rarm', 'larm') and y < 3 else None)), eye=dark_eyes(),
    mouth=['.kkk.', '..w..'], mouth_x=13, mouth_y=24)
def hood_fit(f):
    hair(f, '#1a1010', '#4a2a20', spikes=crown_spikes(8, 2.6, 1.5, seed=31), locks=fringe_locks(6, 31, 7.5, 4.6, 1.6, 0.35) + [([s * 4.6, 30, z], [s * 0.6, -1, 0], 4.2, 1.6) for s in (1, -1) for z in (-2, 1)])
    puffer = lambda x, y: '#3a2258' if int(y * 6) % 2 else '#2a1640'
    shell(f, 'body', [-4, 12.2, -2], [4, 24, 2], 0.42, '#2a1640', {k: puffer for k in ('front', 'back', 'right', 'left')})
    f.add('body', [-3.6, 21.8, 2.1], [3.6, 25.0, 3.8], '#2a1640')                                              # hood down
    f.both('right_arm', [3.6, 20.0, -2.4], [8.4, 24.4, 2.4], '#3a3a40'); f.both('right_arm', [3.5, 19.6, -2.5], [8.5, 20.3, 2.5], '#6a1a1a')
    pants(f, '#0c0a0c', 1.8); f.both('right_leg', [-0.45, 2.6, -2.45], [4.45, 4.6, 2.45], '#3a3a40')
    shoes(f, '#2a1640', '#140a20')
CH['dark_hood']['fit'] = hood_fit

ORDER = ['capboy', 'redhair', 'messy', 'spiky', 'waver', 'gunner', 'briefcase', 'woman', 'girl', 'phone', 'soldier',
         'dark_slime', 'dark_vest', 'dark_kick', 'dark_hood']

def export(fit, path):
    out = {'texture_size': 256, 'bones': {}, 'face': {'bone': 'head', 'texture_size': 16, 'uv': [0, 0], 'from': [-4.0, -8.0, -4.08], 'size': [8.0, 8.0, 0.0]}}
    piv = {'head': [0, 0, 0], 'body': [0, 0, 0], 'right_arm': [-5, 2, 0], 'left_arm': [5, 2, 0], 'right_leg': [-1.9, 12, 0], 'left_leg': [1.9, 12, 0]}
    for b, p in piv.items(): out['bones'][b] = {'pivot': p, 'cubes': [], 'children': []}
    n = 0
    for c in fit.cubes:
        P_ = BB_PIV[c['bone']]; node = out['bones'][c['bone']]
        lo, hi = c['lo'], c['hi']; size3 = r4([hi[k] - lo[k] for k in range(3)])
        if c.get('group') and not c['rot']:              # a named part the mod can show/hide (e.g. glasses)
            gname, o = c['group']
            g = next((ch for ch in node['children'] if ch.get('name') == gname), None)
            if g is None:
                g = {'name': gname, 'pivot': r4([P_[0] - o[0], P_[1] - o[1], o[2] - P_[2]]), 'rot': [0, 0, 0], 'cubes': [], 'children': []}
                node['children'].append(g)
            g['cubes'].append({'uv': c['uv'], 'from': r4([o[0] - hi[0], o[1] - hi[1], lo[2] - o[2]]), 'size': size3})
        elif c['rot']:
            o = c['origin']; r = c['rot']
            node['children'].append({'name': f'n{n}', 'pivot': r4([P_[0] - o[0], P_[1] - o[1], o[2] - P_[2]]),
                                     'rot': r4([-math.radians(r[0]), -math.radians(r[1]), math.radians(r[2])]),
                                     'cubes': [{'uv': c['uv'], 'from': r4([o[0] - hi[0], o[1] - hi[1], lo[2] - o[2]]), 'size': size3}], 'children': []})
            n += 1
        else:
            node['cubes'].append({'uv': c['uv'], 'from': r4([P_[0] - hi[0], P_[1] - hi[1], lo[2] - P_[2]]), 'size': size3})
    json.dump(out, open(path, 'w'), separators=(',', ':'))
    return out

if __name__ == '__main__':
    which = sys.argv[1:] or ORDER
    sheets = []
    red_keep = lambda c: c[0] > 180 and c[1] < 90 and c[2] < 90
    for key in which:
        spec = CH[key]
        fit = Fit(); spec['fit'](fit)
        atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
        pack(fit, atlas, region=(0, 0, 256, 256))
        skin = paint_skin(spec); face = face_img(spec)
        skin.save(TEX + key + '.png'); atlas.save(TEX + key + '_details.png'); face.save(TEX + key + '_face.png')
        if not spec.get('dark'):          # the possessed look
            darken(skin).save(TEX + key + '_dark.png'); darken(atlas).save(TEX + key + '_dark_details.png')
            face_img(spec, dark=True).save(TEX + key + '_face_dark.png')
        mj = export(fit, MOD + key + '.json')
        sheets.append(preview(mj, skin, atlas, face, yaws=(0, 35), scale=7))
        if not spec.get('dark'):
            sheets.append(preview(mj, darken(skin), darken(atlas), face_img(spec, dark=True), yaws=(0,), scale=7))
        print(key, len(fit.cubes))
    W = 3 * sheets[0].width // 2 + 10
    rows = []
    cur = []
    for s in sheets: cur.append(s)
    cols = 6; tw = max(s.width for s in sheets); th = max(s.height for s in sheets)
    big = Image.new('RGB', (cols * (tw // 2 + 4), ((len(sheets) + cols - 1) // cols + 1) * th), (30, 30, 36))
    x = y = 0
    for s in sheets:
        if x + s.width > big.width: x = 0; y += th
        big.paste(s, (x, y)); x += s.width + 4
    big = big.crop((0, 0, big.width, y + th))
    big.save('/tmp/claude-0/-home-claude/b2fe60cb-fd40-5a94-8ee2-7d1a501fd998/scratchpad/outfit/npcs.png')

# ================================================================== Skylanders (winged; wings from Rhalix's model, recoloured)
SKY = {}
SKY['sky_scout'] = dict(skin='#f0cfb4', eyes='#2a9ae8', hair=['#f2d266', '#d8b040'], wing='#f4f2ee',
    paint=clothes('#f6f4ee', 4, '#8ec4ec', 10, '#7a4a28', 2, extra=lambda part, face, x, y, W, H, o:
        ('#d8a830' if part == 'body' and face == 'front' and (x in (3, 4) or y == 0) else None) if part == 'body' else None),
    mouth=['k.....k', '.kkkkk.'], mouth_x=12, mouth_y=23)
def sky_scout_fit(f):
    hair(f, '#f2d266', '#d8b040', spikes=crown_spikes(9, 2.8, 1.5, seed=41) + [([0, 33, -2], [0.2, 1, -0.6], 3.4, 1.3)], locks=fringe_locks(5, 31, 7, 3, 1.4, 0.3))
    tee(f, '#f6f4ee', 20.4, '#d8a830'); f.add('body', [-4.45, 11.8, -2.45], [4.45, 12.8, 2.45], '#d8a830')
    pants(f, '#8ec4ec', 2.8); shoes(f, '#7a4a28', '#4a2a16', 3.2)
SKY['sky_scout']['fit'] = sky_scout_fit

SKY['sky_lexy'] = dict(skin='#f6d8cc', eyes='#e85aa8', hair=['#f28ac0', '#d8689e'], wing='#fbe2ee',
    paint=clothes('#9ad4f4', 4, '#f6f6f6', 6, '#f6f6f6', 3), brow=['.kkkkkk..', 'k........'],
    eye=['..kkkkkk.', 'kkkkkkkkk', '.kooppok.', '.kWopook.', '.kooooOk.', '..kkkkk..', ''], mouth=['k....k', '.kkkk.'], mouth_x=13, mouth_y=23)
def sky_lexy_fit(f):
    hair(f, '#f28ac0', '#d8689e', back_len=23, side_len=25, locks=fringe_locks(5, 31, 7.5, 3, 1.6, 0.25)
         + [([0, 31, 4.7], [0, -0.6, 1], 7.5, 2.6)]
         + [([s * 4.6, 30, -2], [s * 0.3, -1, 0], 5.0, 1.6) for s in (1, -1)])
    f.add('head', [-0.8, 30.2, 4.6], [0.8, 31.6, 5.6], '#f6f6f6')                     # hair tie
    tee(f, '#9ad4f4', 20.4, '#f6f6f6')
    f.add('body', [-4.5, 9.0, -2.5], [4.5, 12.6, 2.5], '#f6f6f6', {k: (lambda x, y: '#d8e8f4' if y > .8 else '#f6f6f6') for k in ('front', 'back', 'right', 'left')})   # skirt
    shoes(f, '#f6f6f6', '#c8c8d0', 3.4)
SKY['sky_lexy']['fit'] = sky_lexy_fit

SKY['sky_max'] = dict(skin='#d8a888', eyes='#6a7a8a', hair=['#3a2618', '#4e3424'], wing='#a8acb4',
    paint=clothes('#6a6e78', 12, '#1e1e24', 12, '#2a2a30', 2, extra=lambda part, face, x, y, W, H, o:
        ('#e8e8e8' if part == 'body' and face == 'front' and 2 <= x <= 5 else None) if part == 'body' else ('#d8a888' if part in ('rarm', 'larm') and y == 11 else None)),
    brow=['kkkkkkk..', '.......k.'], mouth=['.kkkkk', '.....W'], mouth_x=12, mouth_y=25)
def sky_max_fit(f):
    hair(f, '#4e3424', '#3a2618', fringe=False, locks=[([0, 32.6, -4.4], [0, 0.3, 1], 6.5, 6.5)])
    shell(f, 'body', [-4, 11.6, -2], [4, 24, 2], 0.32, '#6a6e78', {'front': lambda x, y: '#e8e8e8' if .3 < x < .7 else '#6a6e78'})
    f.both('right_arm', [3.6, 13.0, -2.4], [8.4, 24.4, 2.4], '#6a6e78')
    f.add('head', [1.5, 25.0, -4.6], [3.6, 25.4, -4.2], '#f2f2f2'); f.add('head', [3.6, 25.0, -4.6], [3.9, 25.4, -4.2], '#e04a1a')   # cigarette
    pants(f, '#1e1e24', 1.6); shoes(f, '#2a2a30', '#141418')
SKY['sky_max']['fit'] = sky_max_fit

SKY['sky_royal'] = dict(skin='#f2dcd0', eyes='#d8a020', hair=['#e8e8f0', '#c8c8d6'], wing='#f2c84a',
    paint=clothes('#f6f2e6', 12, '#f6f2e6', 12, '#c89a2a', 2, extra=lambda part, face, x, y, W, H, o:
        ('#c89a2a' if face in ('front',) and x in (3, 4) else None) if part == 'body' else None),
    brow=['.kkkkk...', 'k........'], mouth=['.kkkk.'], mouth_x=13, mouth_y=24)
def sky_royal_fit(f):
    hair(f, '#e8e8f0', '#c8c8d6', back_len=20, side_len=23, locks=fringe_locks(5, 31, 7.5, 3, 1.6, 0.2) + [([x, 30, 4.7], [0, -1, 0.1], 9.0, 2.2) for x in (-2.5, 0, 2.5)]
         + [([s * 4.6, 30, z], [s * 0.2, -1, 0], 7.0, 1.6) for s in (1, -1) for z in (-2, 1)])
    f.add('head', [-4.7, 32.0, -4.7], [4.7, 32.8, 4.7], '#e8c040', {'front': lambda x, y: '#4ab8f0' if .45 < x < .55 else '#e8c040'})   # circlet
    coat = lambda x, y: '#c89a2a' if x < .07 or x > .93 or y > .92 else '#f6f2e6'
    shell(f, 'body', [-4, 6.0, -2], [4, 24, 2], 0.38, '#f6f2e6', {k: coat for k in ('front', 'back', 'right', 'left')})   # long robe-coat
    f.both('right_arm', [3.6, 12.6, -2.4], [8.4, 24.4, 2.4], '#f6f2e6'); f.both('right_arm', [3.5, 12.4, -2.5], [8.5, 13.2, 2.5], '#c89a2a')
    shoes(f, '#c89a2a', '#8a6a1a')
SKY['sky_royal']['fit'] = sky_royal_fit

SKY['sky_patrol'] = dict(skin='#c48c68', eyes='#20b8a0', hair=['#1aa898', '#128478'], wing='#a8daf2',
    paint=clothes('#1e2a54', 12, '#1e2a54', 12, '#141820', 3), mouth=['k......k', '.kkkkkk.'], mouth_x=12, mouth_y=23)
def sky_patrol_fit(f):
    hair(f, '#1aa898', '#128478', spikes=crown_spikes(7, 2.6, 1.5, seed=43), locks=fringe_locks(5, 31, 7.5, 3, 1.5, 0.3))
    shell(f, 'body', [-4, 12.0, -2], [4, 24, 2], 0.3, '#1e2a54', {'front': lambda x, y: '#f2f2f2' if abs(x - .5) < .05 and y < .5 else '#1e2a54'})
    f.add('body', [-4.6, 17.0, -2.6], [4.6, 18.2, 2.6], '#8ec8f0', rot=[0, 0, 35], origin=[0, 17.6, 0])         # sky-blue sash
    f.both('right_arm', [3.6, 13.0, -2.4], [8.4, 24.4, 2.4], '#1e2a54'); f.both('right_arm', [3.4, 21.6, -2.6], [8.6, 24.8, 2.6], '#2a3a6e', {'top': '#d8a830'})
    pants(f, '#1e2a54', 3.0); shoes(f, '#141820', '#0a0c10', 3.6)
SKY['sky_patrol']['fit'] = sky_patrol_fit

SKY['sky_kid'] = dict(skin='#a8704a', eyes='#6a3a18', hair=['#5a3420', '#7a4a2c'], wing='#b8885a',
    paint=clothes('#5aa850', 4, '#c8a878', 7, '#6a4426', 2), mouth=['kwwwwwk', '.kkkkk.'], mouth_x=12, mouth_y=23)
def sky_kid_fit(f):
    hair(f, '#7a4a2c', '#5a3420', spikes=crown_spikes(12, 1.8, 1.8, seed=47, up=0.5), locks=fringe_locks(5, 31, 7.5, 2.5, 1.8, 0.3))
    tee(f, '#5aa850', 20.6, '#4a9040')
    f.add('body', [-3.6, 22.4, -2.8], [3.6, 24.8, 2.8], '#f08a2a'); f.add('body', [1.0, 18.4, -3.0], [2.4, 22.6, -2.6], '#f08a2a')   # scarf
    pants(f, '#c8a878', 5.0, '#b89868'); shoes(f, '#6a4426', '#3a2614')
SKY['sky_kid']['fit'] = sky_kid_fit


SKY['sky_guard'] = dict(skin='#e8c4a8', eyes='#3a6ad8', hair=['#5a3a22', '#7a5030'], wing='#f4f0e2',
    paint=clothes('#d8dce4', 12, '#c8ccd6', 12, '#8a6a1a', 3, extra=lambda part, face, x, y, W, H, o:
        ('#d8a830' if part == 'body' and face == 'front' and (x in (3, 4) or y in (0, 9)) else None) if part == 'body' else None),
    brow=['kkkkkkk..', '.......k.'], mouth=['.kkkkk.'], mouth_x=12, mouth_y=24)
def sky_guard_fit(f):
    """A Royal Knight of Section 1: silver plate with gold trim, a royal blue tabard and sash, a crested helmet."""
    hair(f, '#7a5030', '#5a3a22', back_len=27, side_len=27.5, locks=fringe_locks(5, 31, 7.5, 2.2, 1.4, 0.3))
    f.add('head', [-5.0, 29.6, -5.0], [5.0, 33.4, 5.0], '#d8dce4', {'front': lambda x, y: '#2a2e38' if y < .35 and .15 < x < .85 else '#d8dce4'})   # helmet
    f.add('head', [-5.1, 29.4, -5.1], [5.1, 29.9, 5.1], '#d8a830')                                  # gold rim
    f.add('head', [-0.5, 33.2, -4.0], [0.5, 35.6, 4.0], '#2a4ab8')                                  # royal blue crest
    shell(f, 'body', [-4, 11.6, -2], [4, 24, 2], 0.34, '#d8dce4', {'front': lambda x, y: '#d8a830' if y > .93 or y < .05 else '#d8dce4'})   # breastplate
    f.add('body', [-2.4, 7.0, -2.6], [2.4, 21.0, -2.4], '#2a4ab8', {'front': lambda x, y: '#d8a830' if x < .1 or x > .9 or y < .04 else '#2a4ab8'})   # tabard
    f.add('body', [-4.6, 17.0, -2.6], [4.6, 18.2, 2.6], '#d8a830', rot=[0, 0, 35], origin=[0, 17.6, 0])                               # gold sash
    f.both('right_arm', [3.5, 13.0, -2.5], [8.5, 24.5, 2.5], '#d8dce4')
    f.both('right_arm', [3.3, 21.4, -2.7], [8.7, 24.8, 2.7], '#c8ccd6', {'top': '#d8a830'})         # pauldrons
    f.both('right_arm', [3.45, 12.0, -2.55], [8.55, 14.0, 2.55], '#8a8e98')                        # gauntlets
    pants(f, '#c8ccd6', 3.4); shoes(f, '#8a6a1a', '#4a3a10', 4.0)
SKY['sky_guard']['fit'] = sky_guard_fit

SKY_ORDER = ['sky_scout', 'sky_lexy', 'sky_max', 'sky_royal', 'sky_patrol', 'sky_kid']

def build_sky(key):
    spec = SKY[key]
    rh = json.load(open(RES + 'models/detail/rhalix.json'))
    ratlas = Image.open(RES + 'textures/entity/character/rhalix_details.png').convert('RGBA')
    wings = [c for c in rh['bones']['body']['children'] if c.get('name') in ('right_wing', 'left_wing')]
    atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    # the feathers: copied from Rhalix's sheet where his wing cubes read them, recoloured to this Skylander's
    target = C(spec['wing']); tl = sum(target) / 3
    def walk(n):
        for c in n.get('cubes', []):
            w, h, d = c['size']; u, v = c['uv']
            for yy in range(v, min(256, v + math.ceil(d + h))):
                for xx in range(u, min(256, u + math.ceil(2 * d + 2 * w))):
                    p = ratlas.getpixel((xx, yy))
                    if p[3] == 0: continue
                    l = (p[0] + p[1] + p[2]) / 3
                    f_ = 0.78 + 0.55 * (l / 120.0)
                    atlas.putpixel((xx, yy), tuple(max(0, min(255, int(c_ * f_))) for c_ in target) + (255,))
        for ch in n.get('children', []): walk(ch)
    for wgt in wings: walk(wgt)
    fit = Fit(); spec['fit'](fit)
    pack(fit, atlas, region=(0, 148, 256, 256))
    skin = paint_skin(spec); face = face_img(spec)
    sad = dict(spec); sad['brow'] = ['......kkk', '...kkk...', 'kk.......']; sad['mouth'] = ['.kkkkkk.', 'k......k']; sad['mouth_x'] = 12
    sad['face_extra'] = [lambda px, cm, dark: [px.__setitem__((x, y), (120, 170, 230, 255)) for (x, y) in ((5, 21), (5, 22), (26, 21), (26, 22))]]
    skin.save(TEX + key + '.png'); atlas.save(TEX + key + '_details.png'); face.save(TEX + key + '_face.png')
    face_img(sad).save(TEX + key + '_face_sad.png')
    mj = export(fit, MOD + key + '.json')
    mj['bones']['body']['children'] += wings
    json.dump(mj, open(MOD + key + '.json', 'w'), separators=(',', ':'))
    return mj, skin, atlas, face

if __name__ == '__main__' and os.environ.get('SKY'):
    sheets = []
    for key in SKY_ORDER:
        mj, skin, atlas, face = build_sky(key)
        sheets.append(preview(mj, skin, atlas, face, yaws=(20, 180), scale=6)); print(key)
    tw = sheets[0].width; th = sheets[0].height
    big = Image.new('RGB', (3 * tw, 2 * th), (30, 30, 36))
    for i, s in enumerate(sheets): big.paste(s, ((i % 3) * tw, (i // 3) * th))
    big.save('/tmp/claude-0/-home-claude/b2fe60cb-fd40-5a94-8ee2-7d1a501fd998/scratchpad/outfit/sky.png')

    # the girls' skins in the slim layout (their body in the mod is slim - see tools/xw/female/slim.py)
    sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/female'); import slim; slim.convert_all()
