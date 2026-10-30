# Circular HUD portraits (128px, 7 faces) for every playable character, the same way Mikiko's/Rhalix's/Ultima's were made:
# the head rendered from the character's model and skin, each face texture keyed into it, in a ring of their colour.
import sys, json, re, os
sys.path.insert(0, '/home/claude/xw/outfits')
from kit import *
from PIL import Image, ImageDraw, ImageChops
MD = RES + 'models/detail/'
OUT = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/gui/hud/'
S = 14; N = 128; K = 4
src = open('/home/claude/xeno-warriors-mod/src/main/java/com/xenowarriors/character/CharacterType.java').read()
COL = {m.group(1): int(m.group(2), 16) & 0xFFFFFF for m in re.finditer(r'[A-Z]+\("(\w+)", 0x([0-9A-Fa-f]{8})\)', src)}
FACES = ('', '_hurt', '_exhausted', '_shout', '_damage1', '_blink', '_rage')

def clean(im):
    im = im.convert('RGBA'); p = im.load(); bg = p[2, 2]
    for y in range(im.height):
        for x in range(im.width):
            if p[x, y][:3] == bg[:3]: p[x, y] = (0, 0, 0, 0)
    return im

def heads(ch):
    mj = json.load(open(MD + ch + '.json'))
    for b in mj['bones']:
        if b != 'head': mj['bones'][b]['cubes'] = []; mj['bones'][b]['children'] = []
    skin = Image.open(CHAR + ch + '.png').convert('RGBA')
    key = skin.copy(); kp = key.load()
    for y in range(16, 64):
        for x in range(64): kp[x, y] = (0, 0, 0, 0)
    for y in range(8, 16):
        for x in range(8, 16): kp[x, y] = (255, 0, 255, 255)
    det = Image.open(CHAR + ch + '_details.png').convert('RGBA')
    A = clean(preview(mj, key, det, None, yaws=(0,), scale=S))
    W = 30 * S
    out = {}
    for ex in FACES:
        face = Image.open(CHAR + ch + '_face' + ex + '.png').convert('RGBA').crop((0, 0, 32, 32)).resize((8 * S, 8 * S), Image.NEAREST)
        im = A.copy(); p = im.load(); fp = face.load(); ox, oy = W // 2 - 4 * S, 3 * S
        for y in range(im.height):
            for x in range(im.width):
                r, g, b, a = p[x, y]
                if a and g < 40 and r > 100 and b > 100 and 0 <= x - ox < 8 * S and 0 <= y - oy < 8 * S:
                    c = fp[x - ox, y - oy]
                    p[x, y] = c if c[3] else (0, 0, 0, 0)
        out[ex] = im.crop(im.getbbox())
    return out

def ring(ch, p, ex):
    c = COL[ch]; col = ((c >> 16) & 255, (c >> 8) & 255, c & 255)
    big = Image.new('RGBA', (N * K, N * K), (0, 0, 0, 0)); d = ImageDraw.Draw(big)
    d.ellipse((0, 0, N * K - 1, N * K - 1), fill=(12, 12, 20, 255))
    d.ellipse((5 * K, 5 * K, (N - 5) * K, (N - 5) * K), fill=col + (255,))
    d.ellipse((11 * K, 11 * K, (N - 11) * K, (N - 11) * K), fill=(30, 34, 52, 255))
    R = (N - 22) * K / 2
    s = (2 * R * 1.04) / max(p.width, p.height); p = p.resize((int(p.width * s), int(p.height * s)), Image.NEAREST)
    layer = Image.new('RGBA', big.size, (0, 0, 0, 0)); layer.paste(p, (int(N * K / 2 - p.width / 2), int(N * K / 2 - p.height * 0.5)), p)
    mask = Image.new('L', big.size, 0); ImageDraw.Draw(mask).ellipse((11 * K, 11 * K, (N - 11) * K, (N - 11) * K), fill=255)
    layer.putalpha(ImageChops.multiply(layer.split()[3], mask))
    big.alpha_composite(layer)
    big.resize((N, N), Image.LANCZOS).save(OUT + f'portrait_{ch}_{ex[1:] if ex else "normal"}.png')

for ch in sys.argv[1:]:
    try:
        for ex, im in heads(ch).items(): ring(ch, im, ex)
        print('ok', ch)
    except Exception as e:
        print('FAILED', ch, e)
