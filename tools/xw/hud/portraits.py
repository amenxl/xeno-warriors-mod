import sys, json
sys.path.insert(0, '/home/claude/xw/outfits')
from kit import *
from PIL import Image
MD = RES + 'models/detail/'
S = 14
def clean(im):
    im = im.convert('RGBA'); p = im.load(); bg = p[2, 2]
    for y in range(im.height):
        for x in range(im.width):
            if p[x, y][:3] == bg[:3]: p[x, y] = (0, 0, 0, 0)
    return im
for ch in ('mikiko', 'rhalix', 'ultima'):
    mj = json.load(open(MD + ch + '.json'))
    for b in mj['bones']:
        if b != 'head': mj['bones'][b]['cubes'] = []; mj['bones'][b]['children'] = []
    skin = Image.open(CHAR + ch + '.png').convert('RGBA')
    head = skin.copy(); px = head.load()
    for y in range(16, 64):
        for x in range(64): px[x, y] = (0, 0, 0, 0)
    none = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    det = Image.open(CHAR + ch + '_details.png').convert('RGBA')
    key = head.copy(); kp = key.load()
    for y in range(8, 16):
        for x in range(8, 16): kp[x, y] = (255, 0, 255, 255)     # the face area, keyed out
    A = clean(preview(mj, key, det, None, yaws=(0,), scale=S))
    W = 30 * S
    for ex in ('', '_hurt', '_exhausted', '_shout', '_damage1', '_blink', '_rage'):
        face = Image.open(CHAR + ch + '_face' + ex + '.png').convert('RGBA').crop((0, 0, 32, 32)).resize((8 * S, 8 * S), Image.NEAREST)
        im = A.copy(); p = im.load(); fp = face.load(); ox, oy = W // 2 - 4 * S, 3 * S
        for y in range(im.height):
            for x in range(im.width):
                r, g, b, a = p[x, y]
                if a and g < 40 and r > 100 and b > 100 and 0 <= x - ox < 8 * S and 0 <= y - oy < 8 * S:
                    c = fp[x - ox, y - oy]
                    p[x, y] = c if c[3] else (0, 0, 0, 0)
        im = im.crop(im.getbbox())
        im.save(f'/tmp/portrait_{ch}{ex}.png')
print('ok')
