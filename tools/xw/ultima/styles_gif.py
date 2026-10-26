"""Ultima's fighting at each output: 25% lazy kicks (hands in pockets), 45% flying punches, 70% heavy slams and
haymakers, 100% fast flurries, Overload (circuit face, green static). Poses use the same numbers as UltimaAnim."""
import sys, math, json, random
sys.path.insert(0, '/home/claude/xw/ultima')
import ultima as U
from PIL import Image, ImageDraw, ImageFont
CH = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'
mj = json.load(open('/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/models/detail/ultima.json'))
skin = Image.open(CH + 'ultima.png').convert('RGBA'); det = Image.open(CH + 'ultima_details.png').convert('RGBA')
FACES = {k: Image.open(CH + f).convert('RGBA') for k, f in (('25', 'ultima_face.png'), ('45', 'ultima_face_focused.png'), ('70', 'ultima_face_annoyed.png'),
         ('100', 'ultima_face_fierce.png'), ('over', 'ultima_face_overload.png'))}
F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 15)
TW, TH, S = 230, 330, 5
def swing(a): return math.sin(a * math.pi)
def pose(style, t):
    a = (t % 12) / 12.0                                      # one attack every 12 frames
    alt = (t // 12) % 2
    p = {}
    if style == '25':
        p = {'right_arm': [0.18, 0.25, 0.12], 'left_arm': [0.18, -0.25, -0.12], 'head': [0, 0, 0.08 + 0.06 * math.sin(t * .3)]}
        s = swing(a); p['right_leg'] = [-1.45 * s, 0, 0.1 * s]; p['left_leg'] = [0.25 * s, 0, 0]; p['body'] = [0, -0.15 * s, 0]
    elif style in ('45', '100', 'over'):
        sp = 2 if style != '45' else 1                         # faster flurries at 100% / Overload
        a = (t * sp % 12) / 12.0; alt = (t * sp // 12) % 2
        s = math.sin(math.sqrt(a) * math.pi)
        arm = 'right_arm' if alt == 0 else 'left_arm'; other = 'left_arm' if alt == 0 else 'right_arm'
        p = {arm: [-1.5 - 0.4 * s if s > .1 else -0.6, (0.2 if alt == 0 else -0.2) * s, 0], other: [-0.9, 0, 0.1 if alt else -0.1],
             'right_leg': [0.35, 0, 0.05], 'left_leg': [0.15, 0, -0.05], 'body': [0, (-0.3 if alt == 0 else 0.3) * s, 0]}
    elif style == '70':
        if alt == 0:                                          # slam: both fists up, then smash down
            up = a / 0.45 if a < 0.45 else 1 - (a - 0.45) / 0.55
            down = math.sin((a - 0.45) / 0.55 * math.pi) if a > 0.45 else 0
            x = -2.9 * up + 0.8 * down
            p = {'right_arm': [x, -0.3 * up, 0.2 * up], 'left_arm': [x, 0.3 * up, -0.2 * up], 'body': [0.5 * down if a > .45 else -0.2 * up, 0, 0],
                 'right_leg': [-0.4 * down, 0, 0], 'left_leg': [0.35 * down, 0, 0]}
        else:                                                 # haymaker
            s = swing(a)
            y = 1.0 * (a / 0.4) if a < 0.4 else 1.0 - 2.2 * ((a - 0.4) / 0.6)
            p = {'right_arm': [-1.4 - 0.3 * s, y, 0], 'left_arm': [-0.6, 0, -0.3], 'body': [0, 0.5 * (a / .4) if a < .4 else 0.5 - 1.1 * ((a - .4) / .6), 0],
                 'right_leg': [-0.3 * s, 0, 0], 'left_leg': [0.3 * s, 0, 0]}
    return p
STYLES = [('25', '25% - lazy kicks'), ('45', '45% - flies, punches'), ('70', '70% - heavy slams'), ('100', '100% - fast flurries'), ('over', 'OVERLOAD (120%)')]
cache = {}
def sprite(style, t):
    k = (style, str(pose(style, t)))
    if k not in cache:
        im = U.preview(mj, skin, det, FACES[style], yaws=(30,), scale=S, pose=pose(style, t), wide=44, tall=56, top=12).convert('RGBA')
        px = im.load()
        for y in range(im.height):
            for x in range(im.width):
                if px[x, y][:3] == (40, 44, 52): px[x, y] = (0, 0, 0, 0)
        cache[k] = im
    return cache[k]
frames = []
for t in range(48):
    sheet = Image.new('RGBA', (TW * 5, TH), (20, 22, 30, 255))
    for i, (st, label) in enumerate(STYLES):
        tile = Image.new('RGBA', (TW, TH), (24 + i * 6, 26, 36, 255))
        d = ImageDraw.Draw(tile)
        fly = st != '25'
        bob = math.sin(t * 0.35 + i) * 6 if fly else 0
        if fly: d.ellipse([TW / 2 - 40, TH - 40, TW / 2 + 40, TH - 28], fill=(14, 16, 22, 255))          # shadow below (in the air)
        else: d.rectangle([0, TH - 40, TW, TH], fill=(40, 46, 40, 255))
        s = sprite(st, t)
        tile.alpha_composite(s, (int(TW / 2 - s.width / 2), int((TH - 40 - s.height) - (30 if fly else 0) + bob)))
        if st == 'over':
            U.static(tile, seed=t, n=7)
        if st in ('100', 'over') and t % 2 == 0:
            for k in range(4):
                y = random.Random(t * 7 + k).randrange(60, TH - 60); d.line([(8, y), (40, y)], fill=(120, 255, 190, 255) if st == 'over' else (200, 210, 255, 255), width=2)
        d.rectangle([0, 0, TW, 24], fill=(10, 10, 14, 255)); d.text((8, 4), label, font=F, fill=(120, 255, 170) if st == 'over' else (230, 230, 240))
        sheet.alpha_composite(tile, (i * TW, 0))
    frames.append(sheet.convert('RGB').convert('P', palette=Image.ADAPTIVE))
frames[0].save('/tmp/ultima_styles.gif', save_all=True, append_images=frames[1:], duration=70, loop=0)
print(len(frames))
