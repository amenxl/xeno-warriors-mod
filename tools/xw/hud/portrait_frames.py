# Live HUD portraits: the same round close-up as portraits_all.py, but with the face cut out (a hole), so the game can key
# the face the model is wearing RIGHT NOW (blink, mouth, expression, Demon/Lunar eyes...) into it - see client/HudPortrait.java.
# One frame per look (outfit, Fox King, Lunar) and per head toggle (Kaeru's hood, Yuri's glasses, Technica's goggles down).
# Writes textures/gui/hud/frame/<look>[_<toggle>].png (ring + head, face hole see-through), plus for looks with a DragonMineZ
# hair mask <look>_hair.png (the visible hair, for the form colour) and for Kaeru <look>_mask.png (the head's coverage,
# for her Change silhouette). The face texels don't change: they're the face textures themselves, drawn at runtime.
import sys, json, re, os, copy
sys.path.insert(0, '/home/claude/xw/outfits')
import kit
from kit import preview
from PIL import Image, ImageDraw, ImageChops
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..'))
RES = ROOT + '/src/main/resources/assets/xenowarriors/'
CHAR = RES + 'textures/entity/character/'
MD = RES + 'models/detail/'
OUT = RES + 'textures/gui/hud/frame/'
os.makedirs(OUT, exist_ok=True)
S = 14; N = 128; K = 4
src = open(ROOT + '/src/main/java/com/xenowarriors/character/CharacterType.java').read()
COL = {m.group(1): int(m.group(2), 16) & 0xFFFFFF for m in re.finditer(r'[A-Z]+\("(\w+)", 0x([0-9A-Fa-f]{8})\)', src)}
KEY = (255, 0, 255, 255)

def clean(im):
    im = im.convert('RGBA'); p = im.load(); bg = p[2, 2]
    for y in range(im.height):
        for x in range(im.width):
            if p[x, y][:3] == bg[:3]: p[x, y] = (0, 0, 0, 0)
    return im

def drop(node, names):
    node['children'] = [c for c in node.get('children', []) if c.get('name') not in names]
    for c in node['children']: drop(c, names)

def find(node, name):
    for c in node.get('children', []):
        if c.get('name') == name: return c
        r = find(c, name)
        if r is not None: return r
    return None

def head_model(model, off=(), goggles_down=False):
    mj = json.load(open(MD + model + '.json'))
    for b in mj['bones']:
        if b != 'head': mj['bones'][b]['cubes'] = []; mj['bones'][b]['children'] = []
    drop(mj['bones']['head'], set(off))
    if goggles_down:
        g = find(mj['bones']['head'], 'goggles')
        if g is not None: g['pivot'] = [g['pivot'][0], g['pivot'][1] + 2.9, g['pivot'][2] - 0.15]
    return mj

def keyed_skin(skin):
    s = Image.open(CHAR + skin + '.png').convert('RGBA'); kp = s.load()
    for y in range(16, 64):
        for x in range(64): kp[x, y] = (0, 0, 0, 0)
    for y in range(8, 16):
        for x in range(8, 16): kp[x, y] = KEY
    return s

def crop_box():
    W = 30 * S; oy = 3 * S
    side = int(8 * S * 1.32); cx, cy = W // 2, oy + 4 * S - int(0.35 * S)
    return (cx - side // 2, cy - side // 2, cx + side // 2, cy + side // 2)

def is_key(c): return c[3] and c[1] < 40 and c[0] > 100 and c[2] > 100

def render(mj, skin, det):
    """The head close-up (cropped like portraits_all) and the face-hole mask (255 = face shows)."""
    A = clean(preview(mj, skin, det, None, yaws=(0,), scale=S))
    W = 30 * S; ox, oy = W // 2 - 4 * S, 3 * S
    hole = Image.new('L', A.size, 0); hp = hole.load(); p = A.load()
    for y in range(A.height):
        for x in range(A.width):
            if is_key(p[x, y]) and 0 <= x - ox < 8 * S and 0 <= y - oy < 8 * S:
                hp[x, y] = 255; p[x, y] = (0, 0, 0, 0)
    b = crop_box()
    return A.crop(b), hole.crop(b)

def place(im, big, resample=Image.NEAREST):
    """Scale and centre a crop into the ring's inner circle (as portraits_all.ring does)."""
    R = (N - 22) * K / 2
    s = (2 * R * 1.02) / max(im.width, im.height)
    q = im.resize((int(im.width * s), int(im.height * s)), resample)
    layer = Image.new(im.mode, (big, big), 0 if im.mode == 'L' else (0, 0, 0, 0))
    layer.paste(q, (int(N * K / 2 - q.width / 2), int(N * K / 2 - q.height / 2)))
    return layer, s, int(N * K / 2 - q.width / 2), int(N * K / 2 - q.height / 2)

def inner():
    m = Image.new('L', (N * K, N * K), 0); ImageDraw.Draw(m).ellipse((11 * K, 11 * K, (N - 11) * K, (N - 11) * K), fill=255); return m

RECT = None
def frame(ch, look, skin, det, model, suffix='', off=(), goggles_down=False, hairmask=None):
    global RECT
    mj = head_model(model, off, goggles_down)
    sk = keyed_skin(skin)
    dt = Image.open(CHAR + det + '.png').convert('RGBA')
    head, hole = render(mj, sk, dt)
    c = COL[ch]; col = ((c >> 16) & 255, (c >> 8) & 255, c & 255)
    big = Image.new('RGBA', (N * K, N * K), (0, 0, 0, 0)); d = ImageDraw.Draw(big)
    d.ellipse((0, 0, N * K - 1, N * K - 1), fill=(12, 12, 20, 255))
    d.ellipse((5 * K, 5 * K, (N - 5) * K, (N - 5) * K), fill=col + (255,))
    d.ellipse((11 * K, 11 * K, (N - 11) * K, (N - 11) * K), fill=(30, 34, 52, 255))
    lay, s, px, py = place(head, N * K)
    msk = inner()
    lay.putalpha(ImageChops.multiply(lay.split()[3], msk))
    big.alpha_composite(lay)
    hl, _, _, _ = place(hole, N * K)
    hl = ImageChops.multiply(hl, msk)              # (the ring stays solid over the face's corners)
    a = ImageChops.subtract(big.split()[3], hl)
    big.putalpha(a)
    # where the face goes, in 128px portrait space: the head's 8x8 front, scaled like the rest
    W = 30 * S; b = crop_box(); fx0 = (W // 2 - 4 * S - b[0]) * s + px; fy0 = (3 * S - b[1]) * s + py
    rect = (round(fx0 / K, 3), round(fy0 / K, 3), round(8 * S * s / K, 3))
    if RECT is None: RECT = rect
    assert rect == RECT, (rect, RECT)
    name = look + suffix
    big.resize((N, N), Image.LANCZOS).save(OUT + name + '.png')
    if ch == 'kaeru':                              # (only Kaeru has a silhouette form: her Change)
        head_cov = ImageChops.multiply(lay.split()[3], ImageChops.invert(hl))
        m = Image.new('RGBA', (N, N), (255, 255, 255, 0)); m.putalpha(head_cov.resize((N, N), Image.LANCZOS))
        m.save(OUT + name + '_mask.png')               # (RGBA: the game reads every frame file as RGBA)
    if hairmask:
        # the head again with the hair mask on the detail atlas (everything else keyed), to find the visible hair
        hm = Image.open(CHAR + hairmask + '.png').convert('RGBA')
        dk = dt.copy(); dp = dk.load(); mp = hm.load()
        for y in range(dk.height):
            for x in range(dk.width):
                if dp[x, y][3] >= 10: dp[x, y] = mp[x, y] if mp[x, y][3] >= 10 else (0, 255, 0, 255)
        sk2 = sk.copy(); sp = sk2.load()
        for y in range(16):
            for x in range(64):
                if sp[x, y][3] >= 10: sp[x, y] = (0, 255, 0, 255)
        hh, _ = render(mj, sk2, dk)
        q = hh.load()
        for y in range(hh.height):
            for x in range(hh.width):
                r, g_, bb, aa = q[x, y]
                if aa == 0 or (g_ > 100 and r < 40 and bb < 40): q[x, y] = (0, 0, 0, 0)
        hlay, _, _, _ = place(hh, N * K)
        hlay.putalpha(ImageChops.multiply(hlay.split()[3], msk))
        hlay.resize((N, N), Image.LANCZOS).save(OUT + name + '_hair.png')
    print('ok', name)

def has(model, part):
    mj = json.load(open(MD + model + '.json')); return find(mj['bones']['head'], part) is not None

OUTFITS = {'mikiko': ['origins1', 'origins2', 's3', 's3b'], 'rhalix': ['origins1', 'origins2', 's3'], 'yuri': ['c1pjs'],
           'max': ['royal', 's3'], 'sam': ['royal', 's3']}
TYPES = ['mikiko', 'rhalix', 'ultima', 'kaeru', 'keita', 'kill', 'kyrie', 'lexy', 'logan', 'max', 'sam', 'stevie', 'technica',
         'yuri', 'zane', 'aioni', 'garry']

def toggles(ch, look, skin, det, model, hairmask=None):
    opt = [p for p in ('hood_up', 'glasses') if has(model, p)]
    frame(ch, look, skin, det, model, '', off=opt, hairmask=hairmask)
    if 'hood_up' in opt: frame(ch, look, skin, det, model, '_hood', off=[p for p in opt if p != 'hood_up'])
    if 'glasses' in opt: frame(ch, look, skin, det, model, '_glasses', off=[p for p in opt if p != 'glasses'])
    if has(model, 'goggles'): frame(ch, look, skin, det, model, '_goggles', off=opt, goggles_down=True)

only = set(sys.argv[1:])
for ch in TYPES:
    if only and ch not in only: continue
    hm = ch + '_details_hairmask' if os.path.exists(CHAR + ch + '_details_hairmask.png') else None
    toggles(ch, ch, ch, ch + '_details', ch, hm)
    for o in OUTFITS.get(ch, []):
        toggles(ch, ch + '_' + o, ch + '_' + o, ch + '_' + o + '_details', ch + '_' + o)
    if ch == 'mikiko':
        toggles(ch, 'mikiko_foxking', 'mikiko', 'mikiko_foxking_details', 'mikiko_foxking', 'mikiko_foxking_details_hairmask')
        for o in OUTFITS['mikiko']:
            toggles(ch, 'mikiko_' + o + '_foxking', 'mikiko_' + o, 'mikiko_' + o + '_foxking_details', 'mikiko_' + o + '_foxking')
    if ch == 'rhalix':
        toggles(ch, 'rhalix_lunar', 'rhalix_lunar', 'rhalix_lunar_details', 'rhalix_lunar')
print('FACE RECT (x, y, size in the 128px portrait):', RECT)
