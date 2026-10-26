import math, random, sys
sys.path.insert(0, '/home/claude/xw/lunar')
import preview as P
from PIL import Image, ImageDraw, ImageFilter, ImageFont
W, H = 520, 440; S = 7
IDLE = {}
ARMS_OUT = {'right_arm': [0, 0, 1.25], 'left_arm': [0, 0, -1.25], 'head': [-0.25, 0, 0]}
HAND_UP = {'right_arm': [-2.9, 0, 0.15], 'left_arm': [0.1, 0, -0.15], 'head': [-0.45, 0, 0]}
cache = {}
def spr(lunar, pose, expr=''):
    k = (lunar, str(pose), expr)
    if k not in cache:
        import ultima as U
        det = Image.open(P.CH + ('rhalix_lunar_details.png' if lunar else 'rhalix_details.png')).convert('RGBA')
        fc = Image.open(P.CH + ('rhalix_lunar_face' if lunar else 'rhalix_face') + expr + '.png').convert('RGBA')
        im = U.preview(P.mj, Image.open(P.P_CH_SKIN(lunar)).convert('RGBA'), det, fc, yaws=(20,), scale=S, pose=pose).convert('RGBA')
        px = im.load()
        for y in range(im.height):
            for x in range(im.width):
                if px[x, y][:3] == (40, 44, 52): px[x, y] = (0, 0, 0, 0)
        cache[k] = im
    return cache[k]
rnd = random.Random(3)
STARS = [(rnd.randrange(W), rnd.randrange(int(H * .7)), rnd.random()) for _ in range(70)]
def sky(t=0):
    im = Image.new('RGB', (W, H)); d = ImageDraw.Draw(im)
    for y in range(H):
        f = y / H; d.line([(0, y), (W, y)], fill=(int(12 + 40 * f), int(10 + 12 * f), int(34 + 30 * f)))
    for x, y, b in STARS:
        c = int(150 + 100 * abs(math.sin(t * .3 + b * 9))); d.point((x, y), fill=(c, c, c))
    d.ellipse([W - 110, 30, W - 50, 90], fill=(238, 238, 225))                       # the full moon
    d.rectangle([0, H - 60, W, H], fill=(30, 46, 30)); d.line([(0, H - 60), (W, H - 60)], fill=(50, 80, 50), width=3)
    return im
def star(d, cx, cy, r, a=255):
    pts = [(cx + math.cos(-math.pi / 2 + i * math.pi / 5) * (r if i % 2 == 0 else r * .45),
            cy + math.sin(-math.pi / 2 + i * math.pi / 5) * (r if i % 2 == 0 else r * .45)) for i in range(10)]
    d.polygon(pts, fill=(232, 222, 236, a), outline=(110, 70, 120, a))
CX, FEET = 200, H - 58
def put(im, s, cx=CX):
    im.paste(s, (int(cx - s.width / 2), int(FEET - 33 * S)), s)
def crescent_layer(alpha, rise=0):
    L = Image.new('RGBA', (W, H), (0, 0, 0, 0)); cx, cy, R = CX, FEET - 33 * S + 9 * S - rise, 24 * S
    m = Image.new('L', (W, H), 0); md = ImageDraw.Draw(m)
    md.ellipse([cx - R, cy - R, cx + R, cy + R], fill=255); md.ellipse([cx - R * .35, cy - R * 1.1, cx + R * 1.45, cy + R * .9], fill=0)
    edge = m.filter(ImageFilter.MaxFilter(7))
    L.paste(Image.new('RGBA', (W, H), (215, 90, 180, int(255 * alpha))), (0, 0), edge)
    L.paste(Image.new('RGBA', (W, H), (16, 10, 22, int(255 * alpha))), (0, 0), m)
    d = ImageDraw.Draw(L)
    for (dx, dy, r) in [(1.0, -.9, 20), (1.15, .1, 12), (-1.05, .4, 14), (-.7, -1.0, 9)]: star(d, cx + dx * R, cy + dy * R, r, int(255 * alpha))
    return L
frames, durs = [], []
def add(im, ms=70): frames.append(im.convert('P', palette=Image.ADAPTIVE)); durs.append(ms)
FN = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 30)
def label(im, txt, col=(200, 230, 255)):
    d = ImageDraw.Draw(im); d.text((W - 20 - d.textlength(txt, font=FN), H - 50), txt, font=FN, fill=col)
# 1) night falls, he stands; moonlight streams down onto him
for i in range(14):
    im = sky(i); d = ImageDraw.Draw(im, 'RGBA')
    if i > 4:
        for k in range(8):
            a = (i - 4) / 10; x = CX + rnd.uniform(-60, 60); d.line([(W - 80, 60), (x, FEET - 100 - rnd.uniform(0, 120))], fill=(220, 225, 255, int(60 * a)), width=2)
    put(im, spr(False, IDLE, '_calm')); add(im)
# 2) silver motes swirl in, arms spread
for i in range(14):
    t = i / 13; im = sky(14 + i).convert('RGBA'); d = ImageDraw.Draw(im)
    pose = {k: [v * t for v in vals] for k, vals in ARMS_OUT.items()}
    put(im, spr(False, pose, '_concentrate') if i % 2 == 0 or i < 4 else spr(False, ARMS_OUT, '_concentrate'))
    for k in range(40):
        a = k / 40 * math.pi * 2 + i * .4; r = 140 * (1 - t) + 20
        x, y = CX + math.cos(a) * r, FEET - 120 + math.sin(a) * r * .6
        d.rectangle([x - 2, y - 2, x + 2, y + 2], fill=(220, 228, 255))
    add(im.convert('RGB'))
# 3) flash
for f in (.9, .6, .3):
    im = sky(30).convert('RGBA'); im.alpha_composite(crescent_layer(.4)); put(im, spr(True, ARMS_OUT, '_fierce'))
    add(Image.blend(im.convert('RGB'), Image.new('RGB', (W, H), (240, 235, 255)), f), 60)
# 4) Lunar Form: crescent rises behind him, motes drift
for i in range(22):
    t = min(1, i / 8); im = sky(33 + i).convert('RGBA')
    im.alpha_composite(crescent_layer(t, int(12 * (1 - t))))
    put(im, spr(True, IDLE if i > 6 else ARMS_OUT, '_fierce' if i < 12 else ''))
    d = ImageDraw.Draw(im)
    for k in range(6):
        x = CX + rnd.uniform(-50, 50); y = FEET - rnd.uniform(20, 230); d.point((x, y), fill=(225, 230, 255))
    o = im.convert('RGB'); label(o, 'LUNAR FORM'); add(o, 80)
# 5) Moon Fall: hand raised, a moon comes down on the field to the right
TX = 420
for i in range(26):
    t = i / 25; im = sky(55 + i).convert('RGBA'); im.alpha_composite(crescent_layer(1)); put(im, spr(True, HAND_UP, '_shout' if i < 6 else '_fierce'))
    d = ImageDraw.Draw(im); R = 46; cy = -60 + (FEET - R - (-60)) * t * t
    d.ellipse([TX - R - 6, cy - R - 6, TX + R + 6, cy + R + 6], fill=(200, 210, 255, 90))
    d.ellipse([TX - R, cy - R, TX + R, cy + R], fill=(222, 226, 238))
    for (ox, oy, cr) in [(-15, -10, 10), (14, 12, 8), (8, -22, 6), (-20, 18, 6)]: d.ellipse([TX + ox - cr, cy + oy - cr, TX + ox + cr, cy + oy + cr], fill=(170, 176, 196))
    o = im.convert('RGB'); label(o, 'MOON FALL!'); add(o, 60)
# 6) impact
for i in range(10):
    t = (i + 1) / 10; im = sky(81).convert('RGBA'); im.alpha_composite(crescent_layer(1)); put(im, spr(True, HAND_UP, '_grin'))
    d = ImageDraw.Draw(im); r = 30 + 120 * t
    d.ellipse([TX - r, FEET - r * .5, TX + r, FEET + r * .25], fill=(225, 230, 255, int(230 * (1 - t) + 20)))
    for k in range(14):
        a = k / 14 * math.pi; d.line([(TX, FEET - 10), (TX + math.cos(a) * r * 1.2, FEET - 10 - math.sin(a) * r * .9)], fill=(255, 255, 255, int(200 * (1 - t))), width=3)
    o = im.convert('RGB')
    if i < 2: o = Image.blend(o, Image.new('RGB', (W, H), (240, 240, 255)), .5)
    o = o.transform(o.size, Image.AFFINE, (1, 0, rnd.uniform(-6, 6) * (1 - t), 0, 1, rnd.uniform(-6, 6) * (1 - t)))
    add(o, 60)
for i in range(10):
    im = sky(91 + i).convert('RGBA'); im.alpha_composite(crescent_layer(1)); put(im, spr(True, IDLE, '_smug')); add(im.convert('RGB'), 90)
durs[-1] = 900
frames[0].save('/mnt/user-data/outputs/lunar_rhalix_transform.gif', save_all=True, append_images=frames[1:], duration=durs, loop=0, optimize=True)
print(len(frames))
