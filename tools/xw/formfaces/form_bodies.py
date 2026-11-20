"""
Bodies for the shirt-off transformations, for every character and outfit (textures <who>[_<outfit>]_<form>.png):
  ssj4  Super Saiyan 4: the top comes off - chest and abs shaded in, bare arms (trousers / shorts stay)
  buff  DragonMineZ's Human "Buffed": the top off and a lot of muscle - big pecs, a deep six-pack, shoulders, biceps
The girls keep a chest wrap in their colour. The jacket layers over the body and arms are cleared (the 3D clothes are
hidden in game while the form is on). Run: python3 form_bodies.py [who...]
"""
import sys
from collections import Counter
from PIL import Image
CH = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'
OUTFITS = {'mikiko': ['', 'origins1', 'origins2', 's3', 's3b'], 'rhalix': ['', 'origins1', 'origins2', 's3'], 'yuri': ['', 'c1pjs'],
           'kyrie': ['', 's2'], 'lexy': ['', 's2'], 'max': ['', 'royal', 's3'], 'sam': ['', 'royal', 's3']}
GIRLS = {'kaeru', 'kyrie', 'lexy', 'technica', 'aioni', 'yuri', 'kaitlyn', 'ava', 'ava_demon', 'persephone', 'persephone_demon', 'fox_girl', 'fox_girl_sakura', 'athrea', 'ranrea'}
COLOR = {'mikiko': (196, 30, 40), 'rhalix': (130, 50, 200), 'kyrie': (120, 60, 190), 'lexy': (200, 60, 150), 'technica': (230, 90, 170),
         'aioni': (170, 20, 40), 'yuri': (40, 160, 90), 'kaeru': (40, 40, 48),
         'kaitlyn': (30, 28, 34), 'ava': (200, 40, 56), 'ava_demon': (200, 40, 56), 'persephone': (110, 60, 170), 'persephone_demon': (110, 60, 170), 'fox_girl': (216, 36, 126), 'athrea': (138, 26, 20), 'ranrea': (42, 74, 208), 'fox_girl_sakura': (228, 98, 142)}

def skin_of(who):
    f = Image.open(CH + who + '_face.png').convert('RGBA'); px = f.load()
    return Counter(px[x, y][:3] for y in range(2, 30) for x in range(2, 30) if px[x, y][3]).most_common(1)[0][0]

def shade(c, k): return tuple(max(0, min(255, int(v * k))) for v in c) + (255,)

def fill(px, x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1): px[x, y] = c

def make(who, look, form):
    src = who + ('_' + look if look else '')
    try: im = Image.open(CH + src + '.png').convert('RGBA')
    except FileNotFoundError: return False
    if im.size != (64, 64): return False
    px = im.load()
    s = skin_of(who)
    buff = form == 'buff'
    base, dark, deep, light = shade(s, 1.0), shade(s, 0.80 if buff else 0.84), shade(s, 0.66), shade(s, 1.12)
    belt = px[20, 31]                                                  # (the trousers' waistband)
    fill(px, 20, 16, 28, 20, base)                                     # body top
    fill(px, 16, 20, 40, 31, base)                                     # body: right side, front, left side, back
    fill(px, 44, 16, 52, 20, base); fill(px, 40, 20, 56, 32, base)     # right arm
    fill(px, 36, 48, 44, 52, base); fill(px, 32, 52, 48, 64, base)     # left arm
    for x in range(16, 40): px[x, 31] = belt
    for (x0, y0, x1, y1) in ((16, 32, 40, 48), (40, 32, 56, 48), (48, 48, 64, 64)): fill(px, x0, y0, x1, y1, (0, 0, 0, 0))   # jacket / sleeves off
    girl = who in GIRLS
    # chest: pecs (front 20-28)
    if not girl:
        for x in (21, 22, 25, 26): px[x, 24 if not buff else 25] = dark
        px[23, 22] = dark; px[24, 22] = dark; px[23, 23] = dark; px[24, 23] = dark
        if buff:
            for x in (21, 26): px[x, 24] = dark
            for x in (20, 27): px[x, 21] = light; px[x, 22] = light
            for x in (21, 26): px[x, 21] = light
    # abs: a six-pack (front)
    rows = (26, 28, 30) if buff else (26, 28)
    for y in range(25, 31): px[23, y] = dark; px[24, y] = dark if y % 2 else base
    for y in rows:
        for x in (21, 22, 25, 26): px[x, y] = dark
    if buff:
        for y in range(25, 31): px[20, y] = dark; px[27, y] = dark   # obliques
        for y in (27, 29): px[22, y] = light; px[25, y] = light
        # back: shoulder blades and spine
        for y in range(20, 31): px[35, y] = dark if y % 2 == 0 else px[35, y]; px[36, y] = dark if y % 2 else px[36, y]
        for x in (33, 38): px[x, 22] = dark; px[x, 23] = dark
        # sides
        for y in range(21, 30): px[16, y] = dark; px[19, y] = dark; px[28, y] = dark; px[31, y] = dark
        # arms: shoulders lit, biceps, forearm line (right arm front 44-48, left arm front 36-40)
        for (fx, ty) in ((44, 20), (36, 52)):
            for x in range(fx, fx + 4): px[x, ty] = light; px[x, ty + 1] = light
            for x in range(fx, fx + 4): px[x, ty + 4] = dark
            px[fx + 1, ty + 3] = light; px[fx + 2, ty + 3] = light
            for x in range(fx, fx + 4): px[x, ty + 7] = dark
        # outer arm sides
        for y in range(20, 30): px[40, y] = dark; px[47 + 4, y] = dark
        for y in range(52, 62): px[32, y] = dark; px[43, y] = dark
    else:
        for x in (20, 27): px[x, 22] = light
        for y in range(20, 31): px[32, y] = dark; px[39, y] = dark
    for x in range(40, 56): px[x, 31] = dark
    for x in range(32, 48): px[x, 63] = dark
    if girl:   # a chest wrap round the body (front, sides, back) in her colour
        c = COLOR.get(who, (60, 60, 70)); c1 = c + (255,); c2 = shade(c, 0.7)
        fill(px, 16, 21, 40, 25, c1)
        for x in range(16, 40): px[x, 24] = c2
        for x in range(20, 28): px[x, 25] = dark
        px[23, 28] = dark; px[24, 28] = dark                          # navel
    im.save(CH + src + '_' + form + '.png')
    return True

WHO = sys.argv[1:] or ['mikiko', 'rhalix', 'kyrie', 'yuri', 'keita', 'sam', 'logan', 'garry', 'zane', 'kill', 'kaeru', 'max',
                       'lexy', 'technica', 'aioni', 'stevie', 'ultima']
n = 0
for who in WHO:
    for look in OUTFITS.get(who, ['']):
        for form in ('ssj4', 'buff'):
            if who in ('mikiko', 'rhalix') and form == 'ssj4': continue    # (theirs are hand-made: ssj4_skins.py)
            n += make(who, look, form)
print(n)
