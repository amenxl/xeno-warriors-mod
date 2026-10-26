"""Ultima boss intro: perched on a Government facility roof edge, legs swinging, bored ->
spots the player -> shark grin -> drops down -> lands -> stands, hands in pockets -> title card."""
import sys, math, random
sys.path.insert(0, '/home/claude/xw/ultima')
import ultima as U
from PIL import Image, ImageDraw, ImageFont, ImageFilter
CH = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'
title, build, gloves, sleek = U.OPTIONS['A']
fit = U.Fit(); build(fit); atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); U.pack(fit, atlas, region=(0, 0, 256, 256))
spec = dict(U.SPEC); spec['paint'] = U.body_paint(gloves, sleek)
skin = U.paint_skin(spec); mj = U.npcs.export(fit, '/tmp/u.json')
FACES = {}
def face(n):
    if n not in FACES: FACES[n] = Image.open(CH + f'ultima_face{n}.png').convert('RGBA')
    return FACES[n]
S = 6; BG = (40, 44, 52); YAW = 42
W, H = 520, 470
ROOF_Y = 205; FLOOR_Y = 430; EDGE_X = 250

# ---------- poses ----------
def sit(swing, lean=0.0, head=(0, 0, 0), elbows=0.0):
    """Sitting on the ledge: thighs flat, shins hanging and swinging; forearm on knee like the panel."""
    return dict(pose={'right_leg': [-1.5 + .05 * swing, 0.12, 0.05], 'left_leg': [-1.5 - .05 * swing, -0.12, -0.05],
                      'body': [lean, 0, 0], 'head': list(head),
                      'right_arm': [-0.3 - elbows, -0.1, 0.12], 'left_arm': [-0.2 - elbows, 0.35, -0.2]},
                bends={'right_leg': 1.45 + .45 * swing, 'left_leg': 1.45 - .45 * swing,
                       'right_arm': -1.0, 'left_arm': -1.15})
PUSH = dict(pose={'right_leg': [-1.2, 0.1, 0.05], 'left_leg': [-1.2, -0.1, -0.05], 'body': [0.35, 0, 0], 'head': [-0.15, 0, 0],
                  'right_arm': [0.6, 0, 0.35], 'left_arm': [0.6, 0, -0.35]}, bends={'right_leg': 1.0, 'left_leg': 1.0})
FALL = dict(pose={'right_leg': [-0.25, 0, 0.12], 'left_leg': [0.15, 0, -0.1], 'body': [0.05, 0, 0], 'head': [-0.2, 0, 0],
                  'right_arm': [-0.4, 0, 1.2], 'left_arm': [-0.4, 0, -1.2]}, bends={'right_leg': 0.4, 'left_leg': 0.2})
LAND = dict(pose={'right_leg': [-1.45, 0.3, 0.12], 'left_leg': [-1.45, -0.3, -0.12], 'right_arm': [-0.6, -0.2, 0.5], 'left_arm': [-0.3, 0.1, -0.6],
                  'head': [-0.25, 0, 0], 'body': [0.3, 0, 0]},
            bends={'right_leg': 2.3, 'left_leg': 2.3, 'right_arm': -0.4, 'left_arm': -0.3},
            offset={k: [0, 5.5, -2 if 'leg' in k else 0] for k in ('head', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg')})
POCKETS = dict(pose={'right_arm': [0.18, 0.25, 0.12], 'left_arm': [0.18, -0.25, -0.12], 'head': [-0.1, 0, 0.1]}, bends={})

def lerp(a, b, t):
    out = {}
    for key in ('pose', 'bends', 'offset'):
        A, B = a.get(key, {}), b.get(key, {}); d = {}
        for k in set(A) | set(B):
            va, vb = A.get(k), B.get(k)
            if isinstance(va, list) or isinstance(vb, list):
                va = va or [0, 0, 0]; vb = vb or [0, 0, 0]; d[k] = [x + (y - x) * t for x, y in zip(va, vb)]
            else: d[k] = (va or 0) + ((vb or 0) - (va or 0)) * t
        out[key] = d
    return out
ease = lambda t: t * t * (3 - 2 * t)

# ---------- scene ----------
def background():
    im = Image.new('RGB', (W, H)); d = ImageDraw.Draw(im)
    for y in range(H):                                    # evening sky
        t = y / H; d.line([(0, y), (W, y)], fill=(int(70 + 120 * t), int(90 + 90 * t), int(140 + 60 * t)))
    rnd = random.Random(4)
    for i in range(9):                                    # Aurora City skyline (blue glass)
        x = rnd.randrange(-20, W); w = rnd.randrange(30, 70); h = rnd.randrange(120, 260)
        d.rectangle([x, FLOOR_Y - h, x + w, FLOOR_Y], fill=(78, 110, 150))
        for wy in range(FLOOR_Y - h + 8, FLOOR_Y, 12): d.line([(x + 4, wy), (x + w - 4, wy)], fill=(120, 170, 215))
    d.rectangle([0, FLOOR_Y, W, H], fill=(96, 100, 92))                       # ground
    # Government facility: light concrete, blue window strip, dark trim (same as the in-game build)
    d.rectangle([-10, ROOF_Y, EDGE_X + 40, FLOOR_Y], fill=(196, 199, 204))
    d.rectangle([-10, ROOF_Y - 6, EDGE_X + 46, ROOF_Y + 4], fill=(150, 154, 160))  # roof ledge
    for wy in (ROOF_Y + 50, ROOF_Y + 120):
        d.rectangle([-10, wy, EDGE_X + 40, wy + 18], fill=(70, 150, 210)); d.line([(-10, wy + 4), (EDGE_X + 40, wy + 4)], fill=(150, 210, 245), width=2)
    d.rectangle([EDGE_X - 70, FLOOR_Y - 70, EDGE_X - 10, FLOOR_Y], fill=(46, 48, 56))         # entrance
    d.rectangle([EDGE_X - 62, FLOOR_Y - 62, EDGE_X - 18, FLOOR_Y], fill=(80, 170, 220))
    d.rectangle([EDGE_X - 46, FLOOR_Y - 84, EDGE_X - 34, FLOOR_Y - 76], fill=(255, 245, 200))
    return im
BGIMG = background()

def sprite(p, f):
    im = U.preview(mj, skin, atlas, face(f), yaws=(YAW,), scale=S, **p).convert('RGBA')
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            if px[x, y][:3] == BG: px[x, y] = (0, 0, 0, 0)
    return im
HIP = (15 * S, 27 * S)        # hip pixel inside a sprite (model y=12)

def compose(spr, hip_xy, extra=None, shake=0, flash=0):
    im = BGIMG.copy()
    sh = ImageDraw.Draw(im)
    if hip_xy[1] > ROOF_Y + 20:                           # shadow on the ground while dropping / standing
        r = 30 - min(25, (FLOOR_Y - hip_xy[1]) // 8)
        sh.ellipse([hip_xy[0] - r, FLOOR_Y - 5, hip_xy[0] + r, FLOOR_Y + 5], fill=(70, 72, 66))
    im.paste(spr, (int(hip_xy[0] - HIP[0]), int(hip_xy[1] - HIP[1])), spr)
    if extra: extra(im)
    if shake: im = im.transform(im.size, Image.AFFINE, (1, 0, random.uniform(-shake, shake), 0, 1, random.uniform(-shake, shake)))
    if flash: im = Image.blend(im, Image.new('RGB', im.size, (200, 255, 220)), flash)
    return im

def dust(n, t):
    def f(im):
        d = ImageDraw.Draw(im); rnd = random.Random(7)
        for i in range(n):
            a = rnd.uniform(math.pi, 2 * math.pi); sp = rnd.uniform(20, 70) * t
            x = HX + math.cos(a) * sp * 1.8; y = FLOOR_Y + math.sin(a) * sp * .45; r = 6 + 10 * t
            c = int(170 + 50 * t); d.ellipse([x - r, y - r, x + r, y + r], fill=(c, c - 4, c - 12))
    return f

def title_card(t, sub_t):
    def f(im):
        d = ImageDraw.Draw(im, 'RGBA')
        a = int(200 * min(1, t * 2))
        d.rectangle([0, 32, W, 112], fill=(10, 14, 12, a))
        d.line([(0, 32), (W, 32)], fill=(31, 224, 122, a), width=3); d.line([(0, 112), (W, 112)], fill=(31, 224, 122, a), width=3)
        BIG = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 46)
        SM = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 16)
        txt = 'ULTIMA'[:max(0, int(6 * min(1, t * 1.6)))]
        d.text((24, 38), txt, font=BIG, fill=(31, 224, 122, 255))
        if sub_t > 0:
            sub = 'Output 25%  ·  "Is that all you got?"'[:int(42 * min(1, sub_t))]
            d.text((28, 88), sub, font=SM, fill=(225, 240, 230, 255))
        if t > .2:                                         # binary data trickling on the right
            rnd = random.Random(int(t * 40))
            for i in range(6):
                d.text((W - 140 + i * 22, 40 + rnd.randrange(0, 50)), rnd.choice('01'), font=SM, fill=(31, 224, 122, 150))
    return f

frames = []; durs = []
def add(im, ms=70): frames.append(im.convert('P', palette=Image.ADAPTIVE, colors=200)); durs.append(ms)
SIT_HIP = (EDGE_X, ROOF_Y - 2)
HX = EDGE_X + 60

# 1) bored, looking away, legs swinging
for i in range(30):
    sw = math.sin(i / 30 * 4 * math.pi)
    f = '_bored' if i not in (17, 18) else '_blink'
    add(compose(sprite(sit(sw, head=(0.05, 0.55, 0.08)), f), SIT_HIP))
# 2) notices the player: head turns toward camera, leans on his knees, grin grows
for i in range(14):
    t = ease(i / 13); sw = math.sin((30 + i) / 30 * 4 * math.pi) * (1 - t)
    f = '_glance_r' if i < 5 else '_smug' if i < 10 else '_grin'
    add(compose(sprite(sit(sw, lean=0.25 * t, head=(0.05 + 0.1 * t, 0.55 - 0.75 * t, 0.08 + 0.1 * t), elbows=0.15 * t), f), SIT_HIP))
GRIN = sprite(sit(0, lean=0.25, head=(0.15, -0.2, 0.18), elbows=0.15), '_grin')
for i in range(12): add(compose(GRIN, SIT_HIP), 80)
# 3) pushes off the ledge
base = sit(0, lean=0.25, head=(0.15, -0.2, 0.18), elbows=0.15)
for i in range(5):
    add(compose(sprite(lerp(base, PUSH, ease(i / 4)), '_grin'), (SIT_HIP[0] + 3 * i, SIT_HIP[1] - 3 * i)))
# 4) drop
y0 = SIT_HIP[1] - 12; y1 = FLOOR_Y - 12 * S + 4
fall = sprite(FALL, '_grin')
for i in range(8):
    t = (i + 1) / 8
    add(compose(fall, (SIT_HIP[0] + 15 + (HX - SIT_HIP[0] - 15) * t, y0 + (y1 - y0) * t * t)), 50)
# 5) landing: crouch + dust + shake + green flash
land = sprite(LAND, '_grin')
for i in range(8):
    add(compose(land, (HX, y1), extra=dust(14, (i + 1) / 8), shake=6 * (1 - i / 8), flash=.35 if i == 0 else 0))
# 6) stands up, hands in pockets, grin -> title card
for i in range(8):
    add(compose(sprite(lerp(LAND, POCKETS, ease(i / 7)), '_grin'), (HX, y1)))
stand = sprite(POCKETS, '_grin')
for i in range(26):
    add(compose(stand, (HX, y1), extra=title_card(i / 12, (i - 10) / 10)), 80)
durs[-1] = 1200
frames[0].save('/mnt/user-data/outputs/ultima_boss_intro.gif', save_all=True, append_images=frames[1:], duration=durs, loop=0, optimize=True)
frames[40].convert('RGB').save('/mnt/user-data/outputs/ultima_boss_intro_still.png')
print(len(frames))
