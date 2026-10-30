# Voice-chat talking mouths (5 frames) for every character, made from Mikiko's: placed over each character's own mouth
# and drawn in their own skin colour (the shared copy had Mikiko's skin baked in, a brown patch on everyone else).
import sys, collections
from PIL import Image
D = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'
SKIN0 = (150, 98, 64)          # Mikiko's skin in the template frames
TEMPLATE = [Image.open(D + f'mikiko_mouth_{n}.png').convert('RGBA') for n in range(1, 6)]
BOX = TEMPLATE[0].getbbox()     # (10, 22, 23, 29)
W, H = BOX[2] - BOX[0], BOX[3] - BOX[1]

def skin_and_mouth(ch):
    f = Image.open(D + ch + '_face.png').convert('RGBA'); px = f.load()
    cnt = collections.Counter(px[x, y][:3] for y in range(18, 30) for x in range(6, 26) if px[x, y][3] == 255)
    skin = cnt.most_common(1)[0][0]
    def dist(c): return sum(abs(a - b) for a, b in zip(c, skin))
    pts = [(x, y) for y in range(20, 30) for x in range(11, 21) if px[x, y][3] and dist(px[x, y][:3]) > 60 and sum(px[x, y][:3]) < sum(skin)]
    if pts:
        cx = sum(p[0] for p in pts) / len(pts); cy = sum(p[1] for p in pts) / len(pts)
    else:
        cx, cy = (BOX[0] + BOX[2]) / 2 - 0.5, (BOX[1] + BOX[3]) / 2 - 0.5
    return skin, cx, cy

MASKED = {'lexy'}

def shade(c, k): return tuple(max(0, min(255, int(v * k))) for v in c)

for ch in sys.argv[1:]:
    skin, cx, cy = skin_and_mouth(ch)
    x0 = int(round(cx - (W - 1) / 2)); y0 = int(round(cy - (H - 1) / 2))
    x0 = max(4, min(28 - W, x0)); y0 = max(18, min(31 - H, y0))
    for n, t in enumerate(TEMPLATE, 1):
        out = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); op = out.load(); tp = t.load()
        for y in range(H):
            for x in range(W):
                c = tp[BOX[0] + x, BOX[1] + y]
                if not c[3]: continue
                rgb = c[:3]
                if rgb == SKIN0:
                    if ch in MASKED: continue                                 # (a mask: only the open mouth shows through it)
                    rgb = skin                                                # skin around the mouth: theirs
                elif rgb == (58, 26, 16): rgb = shade(skin, 0.38)             # lip line: a darker shade of their skin
                op[x0 + x, y0 + y] = rgb + (255,)
        out.save(D + f'{ch}_mouth_{n}.png')
    print(ch, 'skin', skin, 'mouth at', round(cx, 1), round(cy, 1))
