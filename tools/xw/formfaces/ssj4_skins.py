"""
Super Saiyan 4 bodies for Rhalix and Mikiko (textures <who>_ssj4.png): the top comes off for the transformation.
Rhalix: bare chest and arms (pecs and abs shaded in), his trousers stay. Mikiko: bare arms and midriff with a dark red
chest wrap (her colours), her shorts stay. Base: their normal skin; the jacket layer over the body/arms is cleared.
"""
from collections import Counter
from PIL import Image
CH = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'

def skin_of(who):
    f = Image.open(CH + who + '_face.png').convert('RGBA'); px = f.load()
    return Counter(px[x, y][:3] for y in range(2, 30) for x in range(2, 30) if px[x, y][3]).most_common(1)[0][0]

def shade(c, k): return tuple(max(0, min(255, int(v * k))) for v in c) + (255,)

def fill(px, x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1): px[x, y] = c

def make(who, wrap):
    im = Image.open(CH + who + '.png').convert('RGBA'); px = im.load()
    s = skin_of(who); base, dark, light = shade(s, 1.0), shade(s, 0.82), shade(s, 1.1)
    # body: top (20-28,16-20), bottom stays, front 20-28, right 16-20, left 28-32, back 32-40; rows 20-31 (row 31 = waistband stays)
    fill(px, 20, 16, 28, 20, base)
    fill(px, 16, 20, 40, 31, base)
    # right arm (40-56, 16-32) and left arm (32-48, 48-64): bare to the wrist
    fill(px, 44, 16, 52, 20, base); fill(px, 40, 20, 56, 32, base)
    fill(px, 36, 48, 44, 52, base); fill(px, 32, 52, 48, 64, base)
    belt = px[4, 21]                                                  # (the trousers' colour: the waistband)
    if not wrap:
        for x in range(16, 40): px[x, 31] = belt
    for x in range(40, 56): px[x, 31] = dark
    for x in range(32, 48): px[x, 63] = dark
    # jacket / sleeve layers off
    for (x0, y0, x1, y1) in ((16, 32, 40, 48), (40, 32, 56, 48), (48, 48, 64, 64)): fill(px, x0, y0, x1, y1, (0, 0, 0, 0))
    if wrap:   # Mikiko: a chest wrap round the body (front, sides, back), rows 21-24
        red, red2 = (150, 22, 30, 255), (110, 14, 22, 255)
        fill(px, 16, 21, 40, 25, red)
        for x in range(16, 40): px[x, 24] = red2
        for x in (23, 24): px[x, 22] = red2                           # (a knot at the front)
        for x in range(20, 28): px[x, 25] = dark                      # (shade under it)
        px[23, 28] = dark; px[24, 28] = dark                          # navel
    else:      # Rhalix: chest and abs
        for x in (21, 22, 25, 26): px[x, 24] = dark                   # under the pecs
        px[23, 23] = dark; px[24, 23] = dark
        for y in (26, 28): px[22, y] = dark; px[25, y] = dark          # abs
        for y in range(25, 30): px[23, y] = dark; px[24, y] = dark if y % 2 else base
        for x in (20, 27): px[x, 22] = light
        for y in range(20, 31): px[32, y] = dark; px[39, y] = dark     # back edges
        for y in range(21, 29): px[35, y] = dark if y % 3 == 0 else px[35, y]
    im.save(CH + who + '_ssj4.png')

make('rhalix', False)
make('mikiko', True)
print('ok')
