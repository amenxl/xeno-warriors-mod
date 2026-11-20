# Voice-chat talking mouths (5 frames) for every character, made from Mikiko's: placed over each character's own mouth.
# Their skin is taken from the face itself, pixel by pixel (the face's own shading carries on under the mouth), and where
# the face has its mouth, from the nearest skin beside it; the lip line is a darker shade of that skin. Whatever is left
# of the face's own mouth outside the frame is covered with skin too, so only the talking mouth shows.
# (Before: one skin colour per character, taken as the most common colour in the lower face - for the newer faces that
#  was a shadow tone, so a dark box sat round their mouths.)
# usage: python3 mouths.py <character face names...>   (never mikiko: its frames are the template)
import sys, collections
from PIL import Image
D = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'
SKIN0 = (150, 98, 64)          # Mikiko's skin in the template frames
LIP0 = (58, 26, 16)
TEMPLATE = [Image.open(D + f'mikiko_mouth_{n}.png').convert('RGBA') for n in range(1, 6)]
BOX = TEMPLATE[0].getbbox()     # (10, 22, 23, 29)
W, H = BOX[2] - BOX[0], BOX[3] - BOX[1]

def dist(a, b): return sum(abs(x - y) for x, y in zip(a, b))

def analyse(ch):
    f = Image.open(D + ch + '_face.png').convert('RGBA'); px = f.load()
    # the skin: the most common colour of the cheeks and chin, below the eyes
    cnt = collections.Counter(px[x, y][:3] for y in range(22, 31) for x in range(7, 25) if px[x, y][3] == 255)
    skin = cnt.most_common(1)[0][0]
    # skin tones (the face's own shading) vs. its mouth (darker, clearly different)
    def is_skin(x, y):
        c = px[x, y]
        return c[3] == 255 and dist(c[:3], skin) <= 75
    mouth = [(x, y) for y in range(20, 31) for x in range(10, 22) if px[x, y][3] and not is_skin(x, y) and sum(px[x, y][:3]) < sum(skin) + 40]
    if mouth:
        cx = sum(p[0] for p in mouth) / len(mouth); cy = sum(p[1] for p in mouth) / len(mouth)
    else:
        cx, cy = (BOX[0] + BOX[2]) / 2 - 0.5, (BOX[1] + BOX[3]) / 2 - 0.5
    def skin_at(x, y):
        if is_skin(x, y): return px[x, y][:3]
        for r in range(1, 8):                                        # the nearest skin: same row first, then above / below
            for dx, dy in ((-r, 0), (r, 0), (0, -r), (0, r), (-r, -r), (r, -r), (-r, r), (r, r)):
                q = (x + dx, y + dy)
                if 0 <= q[0] < 64 and 0 <= q[1] < 64 and is_skin(*q): return px[q][:3]
        return skin
    return skin, cx, cy, mouth, skin_at

MASKED = {'lexy'}

def shade(c, k): return tuple(max(0, min(255, int(v * k))) for v in c)

for ch in sys.argv[1:]:
    assert ch != 'mikiko', 'mikiko is the template'
    skin, cx, cy, mouth, skin_at = analyse(ch)
    x0 = int(round(cx - (W - 1) / 2)); y0 = int(round(cy - (H - 1) / 2))
    x0 = max(4, min(28 - W, x0)); y0 = max(18, min(31 - H, y0))
    for n, t in enumerate(TEMPLATE, 1):
        out = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); op = out.load(); tp = t.load()
        for y in range(H):
            for x in range(W):
                c = tp[BOX[0] + x, BOX[1] + y]
                if not c[3]: continue
                X, Y = x0 + x, y0 + y
                rgb = c[:3]
                if rgb == SKIN0:
                    if ch in MASKED: continue                                 # (a mask: only the open mouth shows through it)
                    rgb = skin_at(X, Y)                                       # skin around the mouth: theirs, as on the face
                elif rgb == LIP0: rgb = shade(skin_at(X, Y), 0.38)            # lip line: a darker shade of their skin
                op[X, Y] = rgb + (255,)
        if ch not in MASKED:
            for (X, Y) in mouth:                                              # the face's own mouth: covered where the frame isn't
                if not op[X, Y][3]: op[X, Y] = skin_at(X, Y) + (255,)
        out.save(D + f'{ch}_mouth_{n}.png')
    print(ch, 'skin', skin, 'mouth at', round(cx, 1), round(cy, 1), 'own mouth px', len(mouth))
