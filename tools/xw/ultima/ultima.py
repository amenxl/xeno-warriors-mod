"""Ultima (Aniri Tenjo): 5 model options + Overload (over 100% output: circuit face, white-green eyes, green static)."""
import sys, os, math
sys.path.insert(0, '/home/claude/xw/npcs'); sys.path.insert(0, '/home/claude/xw/outfits'); sys.path.insert(0, '/home/claude/xw/detail')
import npcs
from kit import VBONES, Rzyx, box_quads
import numpy as np
from npcs import hair, shell, pants, shoes, paint_skin, face_img, crown_spikes, fringe_locks, Fit, pack, preview, C, mul
from PIL import Image, ImageDraw, ImageFont
import random

BLK, BLK2, TRIM, TRIM2, SILV, GEM, PANT, SKIN = '#191b24', '#23263a', '#1fe07a', '#14a858', '#b9bccb', '#2ef08a', '#2a2338', '#e7b98f'
HAIR_T, HAIR_B = '#16303c', '#0a1016'

def trimmed(col, edge=TRIM, t=.12):
    return lambda x, y: edge if y > 1 - t else col

def body_paint(gloves, sleek=False):
    def fn(part, face, x, y, W, H, outer):
        if part == 'body':
            if face == 'front':
                # green trim lines: a V from the collar, a band across the chest, a curve at the waist
                if abs(x - 3.5) + 0.5 > 3.6 - y * 0.55 and abs(x - 3.5) - 0.6 < 3.6 - y * 0.55 and y < 6: return TRIM
                if y == 7 and 1 <= x <= 6: return TRIM
                if y == 11: return '#101016'
            if face == 'back' and y in (3, 8): return TRIM2
            return BLK
        if part in ('rarm', 'larm'):
            glove = gloves == 'both' or (gloves == 'one' and part == 'larm')
            if y < 4: return BLK                                       # short sleeve
            if y == 4: return TRIM
            if glove and y >= 8: return '#15151c' if y < 11 else BLK2
            if glove and y == 7: return TRIM
            if sleek and face == 'front' and x == 2 and 5 <= y <= 11: return TRIM2
            return SKIN
        if part in ('rleg', 'lleg'):
            if face == 'top': return PANT
            if y >= 7:                                                  # boots
                return TRIM if (face == 'front' and x in (1, 2) and y < 11) or y == 7 else '#14141a'
            if sleek and face in ('right', 'left') and x == 2: return TRIM2
            return PANT
        return None
    return fn

SPEC = dict(skin=SKIN, eyes='#3cf2c8', hair=[HAIR_T, HAIR_B],
            brow=['......kkk', '..kkkk...', 'kk.......'],
            eye=['..kkkkkk.', 'kkkkkkkkk', '.wOOooOw.', '.wOOpoOw.', '.wOoopOw.', '..wOOOw..', '..kkkkk..'],
            mouth=['kwwwwwwwwk', '.kwkwkwkk.', '..kkkkkk..'], mouth_x=11, mouth_y=23)

def ultima_hair(f, long_spikes=False):
    sp = crown_spikes(11 if long_spikes else 9, 3.6 if long_spikes else 2.8, 1.6, seed=21, up=0.7)
    sp += [([0.5, 33.2, -1], [0.3, 1, 0.4], 4.6 if long_spikes else 3.4, 1.0), ([-3.5, 31.5, 2], [-1, 0.5, 0.6], 3.6, 1.4), ([3.5, 31.5, 2], [1, 0.4, 0.6], 3.4, 1.4)]
    # the long fringe that falls over his right eye (his right = +x)
    locks = [([x, 31.6, -4.75], [0.15, -1, -0.2], ln, 1.5) for x, ln in ((3.4, 4.2), (2.2, 3.8), (1.0, 3.0), (-0.4, 2.2), (-1.8, 2.0), (-3.2, 1.8))]
    locks += [([s * 4.6, 30.5, z], [s * 0.4, -1, 0.1], 3.8, 1.4) for s in (1, -1) for z in (-2.0, 1.0)]
    locks += [([x, 29, 4.7], [0, -1, 0.5], 3.2, 1.6) for x in (-2.5, 0, 2.5)]
    hair(f, HAIR_T, HAIR_B, spikes=sp, locks=locks, back_len=25.2)

def collar_and_gems(f, big_pads=False):
    # high collar with green trim, and the big round gem at his throat
    f.add('body', [-2.6, 23.4, -2.7], [2.6, 25.2, -2.2], BLK, {'front': trimmed(BLK, TRIM, .2), 'top': TRIM})
    for s in (1, -1): f.add('body', [s * 2.2 if s > 0 else -2.7, 23.4, -2.6], [2.7 if s > 0 else -2.2, 25.4, 2.4], BLK, {'top': TRIM})
    f.add('body', [-1.2, 22.4, -3.25], [1.2, 24.0, -2.6], GEM, {'front': lambda x, y: '#b8ffd8' if x < .35 and y < .4 else GEM})
    # shoulder pads: silver rims with green gems
    r = 1.0 if big_pads else 0.0
    f.both('right_arm', [3.6 - r * .2, 24.3, -2.0 - r * .5], [8.2 + r, 25.0 + r * .3, 2.0 + r * .5], SILV, {'top': lambda x, y: GEM if .2 < x < .8 and .15 < y < .85 else SILV})
    f.both('right_arm', [4.6, 25.0 + r * .3, -1.2 - r * .3], [7.4 + r * .5, 25.5 + r * .4, 1.2 + r * .3], GEM, {'top': lambda x, y: '#b8ffd8' if x < .3 and y < .3 else GEM})
    # cords hanging off the pads (from the art)
    f.both('right_arm', [8.2, 20.5, -0.2], [8.55, 24.4, 0.15], TRIM)

def belt(f):
    shell(f, 'body', [-4, 12.0, -2], [4, 13.4, 2], 0.42, '#121218')
    f.add('body', [-1.5, 11.6, -2.85], [1.5, 13.9, -2.45], SILV)
    f.add('body', [-1.1, 12.0, -3.0], [1.1, 13.5, -2.8], GEM, {'front': lambda x, y: '#b8ffd8' if x < .35 and y < .35 else GEM})

def cape_jacket(f, length=17.0, sleeves=True):
    """The cropped jacket worn open: hangs from the shoulders, green edges, ends mid-chest (or longer)."""
    e = TRIM
    for s in (1, -1):
        x0, x1 = (0.9, 4.4) if s > 0 else (-4.4, -0.9)
        f.add('body', [x0, length, -2.75], [x1, 24.2, -2.35], BLK2, {'front': lambda x, y, s=s: e if y > .93 or (x < .12 if s > 0 else x > .88) else BLK2})
    f.add('body', [-4.4, length, 2.35], [4.4, 24.2, 2.75], BLK2, {'back': lambda x, y: e if y > .93 else BLK2})
    for s in (1, -1):
        x0, x1 = (4.25, 4.65) if s > 0 else (-4.65, -4.25)
        f.add('body', [x0, length, -2.75], [x1, 24.2, 2.75], BLK2)
    if sleeves:
        f.both('right_arm', [3.5, 19.4, -2.5], [8.5, 24.4, 2.5], BLK2, {'front': trimmed(BLK2), 'back': trimmed(BLK2), 'right': trimmed(BLK2), 'left': trimmed(BLK2)})

def long_coat(f):
    cape_jacket(f, 13.0)
    for s in (1, -1):                                                 # long tails down to the knees, swinging with the legs
        x0, x1 = (0.4, 4.4) if s > 0 else (-4.4, -0.4)
        f.add('body', [x0, 5.0, 2.3], [x1, 13.0, 2.75], BLK2, {'back': lambda x, y: TRIM if y > .94 or x < .08 or x > .92 else BLK2}, group=('coat_back', [0, 12, 2.5]))
        f.add('body', [s * 4.25 if s > 0 else -4.65, 6.0, -2.0], [4.65 if s > 0 else -4.25, 13.0, 2.6], BLK2, group=('coat_back', [0, 12, 2.5]))

def bracers(f, both=True):
    f.both('right_arm', [3.55, 13.6, -2.45], [8.45, 14.7, 2.45], SILV, {'front': lambda x, y: '#8e90a0' if int(x * 5) % 2 else SILV}) if both else \
        f.add('right_arm', [3.55, 13.6, -2.45], [8.45, 14.7, 2.45], SILV)

def chest_plate(f):
    f.add('body', [-3.0, 17.0, -2.85], [3.0, 21.5, -2.4], BLK2, {'front': lambda x, y: TRIM if y < .08 or y > .92 or x < .06 or x > .94 else BLK2})
    f.add('body', [-0.8, 18.4, -3.05], [0.8, 20.0, -2.8], GEM)

def legs(f, sleek=False):
    pants(f, PANT, 6.8, baggy=0.2 if not sleek else 0.05)
    f.both('right_leg', [-0.38, 0, -2.45], [4.38, 7.2, 2.45], '#14141a', {'front': lambda x, y: TRIM if .25 < x < .45 else '#14141a', 'top': TRIM,
                                                                        'right': lambda x, y: TRIM if y < .1 else '#14141a', 'left': lambda x, y: TRIM if y < .1 else '#14141a'})
    f.both('right_leg', [-0.45, -0.3, -3.3], [4.45, 0.45, 2.5], TRIM2)          # green soles
    f.both('right_leg', [0.1, 0, -3.2], [3.9, 1.6, -2.45], '#14141a')

OPTIONS = {
    'A': ('Classic (art 1): cropped open jacket, one glove', lambda f: (ultima_hair(f), cape_jacket(f), collar_and_gems(f), belt(f), legs(f)), 'one', False),
    'B': ('Battle (art 2): both gloves, silver bracers, wilder hair', lambda f: (ultima_hair(f, True), cape_jacket(f), collar_and_gems(f), belt(f), bracers(f), legs(f)), 'both', False),
    'C': ('Commander: long coat tails to the knees', lambda f: (ultima_hair(f), long_coat(f), collar_and_gems(f), belt(f), bracers(f), legs(f)), 'both', False),
    'D': ('Living weapon: big shoulder pads + chest gem plate', lambda f: (ultima_hair(f, True), cape_jacket(f, 17.5), collar_and_gems(f, True), chest_plate(f), belt(f), bracers(f), legs(f)), 'both', False),
    'E': ('Sleek tech: no jacket, glowing circuit lines', lambda f: (ultima_hair(f), collar_and_gems(f), belt(f), legs(f, True), f.both('right_arm', [3.55, 20.0, -2.45], [8.45, 24.4, 2.45], BLK, {'front': trimmed(BLK), 'right': trimmed(BLK), 'left': trimmed(BLK), 'back': trimmed(BLK)})), 'both', True),
}

def preview(model_json, skin, detail, face, yaws=(0, 35, 180), scale=7, pose=None):
    """Like kit.preview, but taller (hair fits) and with the full 32x32 face drawn on the head."""
    ims = []
    fimg = face.crop((0, 0, 32, 32)) if face is not None else None
    for yaw in yaws:
        V = Rzyx([0, math.radians(yaw), 0])
        quads = []
        for b, (piv, frm, uv) in VBONES.items():
            M = V; T = V @ np.array(piv, float)
            quads += box_quads(frm, [8, 8, 8] if b == 'head' else [8, 12, 4] if b == 'body' else [4, 12, 4], uv, skin, M, T)
            def walk(nd, M, T):
                out = []
                for c in nd['cubes']: out += box_quads(c['from'], c['size'], c['uv'], detail, M, T)
                for ch in nd.get('children', []):
                    M2 = M @ Rzyx(ch.get('rot', [0, 0, 0])); T2 = M @ np.array(ch['pivot'], float) + T
                    out += walk(ch, M2, T2)
                return out
            quads += walk(model_json['bones'][b], M, T)
            if b == 'head' and fimg is not None and (M @ np.array([0, 0, -1.0]))[2] < 0.01:
                for j in range(32):
                    for i in range(32):
                        c = fimg.getpixel((i, j))
                        if c[3] < 10: continue
                        pts = [M @ np.array([-4 + (i + a) / 4, -8 + (j + bb) / 4, -4.06]) + T for a, bb in ((0, 0), (1, 0), (1, 1), (0, 1))]
                        quads.append((np.mean([p[2] for p in pts]) - 0.02, [(p[0], p[1]) for p in pts], c[:3]))
        quads.sort(key=lambda q: -q[0])
        W, H = 30 * scale, 40 * scale
        img = Image.new('RGB', (W, H), (40, 44, 52)); dr = ImageDraw.Draw(img)
        for _, pts, col in quads:
            dr.polygon([(W / 2 + x * scale, 7 * scale + (y + 8) * scale) for x, y in pts], fill=col)
        ims.append(img)
    sheet = Image.new('RGB', (sum(i.width for i in ims), ims[0].height))
    x = 0
    for i in ims: sheet.paste(i, (x, 0)); x += i.width
    return sheet

def overload_face(face):
    """Over 100%: green circuit grid over half his face, white-hot green eyes."""
    im = face.copy(); px = im.load()
    for y in range(6, 30):
        for x in range(16, 30):
            r, g, b, a = px[x, y]
            if a == 0: continue
            grid = (x - 16) % 5 == 0 or (y - 6) % 5 == 0
            px[x, y] = (40, 200, 120, 255) if grid else (int(r * .5 + 20), int(g * .6 + 70), int(b * .5 + 40), 255)
    for (x, y) in [(xx, yy) for xx in list(range(4, 12)) + list(range(21, 29)) for yy in range(15, 20)]:
        r, g, b, a = px[x, y]
        if a and (g > 150 or r > 200): px[x, y] = (230, 255, 240, 255)
    return im

def static(img, seed=1, n=14):
    """Green static crackling around him (drawn on the preview)."""
    d = ImageDraw.Draw(img); rnd = random.Random(seed); W, H = img.size
    for i in range(n):
        x, y = rnd.randrange(int(W * .2), int(W * .8)), rnd.randrange(int(H * .1), int(H * .95))
        pts = [(x, y)]
        for k in range(5): x += rnd.randint(-14, 14); y += rnd.randint(-14, 14); pts.append((x, y))
        d.line(pts, fill=(120, 255, 170), width=3); d.line(pts, fill=(230, 255, 240), width=1)
    return img

if __name__ == '__main__':
    out = '/home/claude/xw/ultima/out/'; os.makedirs(out, exist_ok=True)
    F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 18)
    tiles = []
    for key, (title, build, gloves, sleek) in OPTIONS.items():
        fit = Fit(); build(fit)
        atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); pack(fit, atlas, region=(0, 0, 256, 256))
        spec = dict(SPEC); spec['paint'] = body_paint(gloves, sleek)
        skin = paint_skin(spec); face = face_img(spec)
        mj = npcs.export(fit, out + f'ultima_{key}.json')
        skin.save(out + f'ultima_{key}.png'); atlas.save(out + f'ultima_{key}_details.png'); face.save(out + f'ultima_{key}_face.png')
        im = preview(mj, skin, atlas, face, yaws=(0, 35, 180), scale=7)
        ImageDraw.Draw(im).text((10, 8), f'Option {key}: {title}', font=F, fill=(255, 255, 255))
        tiles.append(im); im.save(out + f'option_{key}.png')
        print(key, len(fit.cubes))
    # overload (on option A's model; works with any)
    title, build, gloves, sleek = OPTIONS['A']
    fit = Fit(); build(fit); atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); pack(fit, atlas, region=(0, 0, 256, 256))
    spec = dict(SPEC); spec['paint'] = body_paint(gloves, sleek)
    skin = paint_skin(spec); face = overload_face(face_img(spec))
    glow = atlas.copy(); gp = glow.load()
    for y in range(256):
        for x in range(256):
            r, g, b, a = gp[x, y]
            if a and g > 150 and r < 120: gp[x, y] = (min(255, r + 90), 255, min(255, b + 90), a)
    mj = npcs.export(fit, out + 'ultima_overload.json')
    im = static(preview(mj, skin, glow, face, yaws=(0, 35), scale=7), seed=4, n=18)
    ImageDraw.Draw(im).text((10, 8), 'Overload (over 100% output): circuit face, white-green eyes, green static', font=F, fill=(160, 255, 200))
    im.save(out + 'overload.png'); print('overload')
