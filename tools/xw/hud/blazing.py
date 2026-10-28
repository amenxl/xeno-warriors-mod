# Blazing Wind HUD textures: the emblem (cut from the user's mock-up, mirrored, made white so the game tints it in the
# character's colour, centre cleared for the portrait) and the label bubbles (HP, KP, MP, DP, HG, KI, ST, LV, EXP).
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import math, os
OUT = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/gui/hud/'
SRC = os.path.join(os.path.dirname(__file__), 'blazing_src.png')
src = Image.open(SRC).convert('RGBA')
# --- emblem: x 0..119, y 38..152 of the mock-up
em = src.crop((0, 38, 120, 152))
p = em.load()
W, H = em.size
cx, cy, r = 55, 52, 31                    # the inner circle (portrait window)
hx, hy, hr = 125, 75 - 38, 18             # the HP bubble overlapping the right edge
for y in range(H):
    for x in range(W):
        R, G, B, A = p[x, y]
        if A == 0: continue
        if ((x - hx) ** 2 + (y - hy) ** 2 < hr * hr or (x - 122) ** 2 + (y - 99) ** 2 < 17 * 17 or (x - 27) ** 2 + (y - 124) ** 2 < 18 * 18
                or y > 106 and x < 100 and (R > 200 and G > 80)):   # the HP / TP / LV bubbles, the LV plate
            p[x, y] = (0, 0, 0, 0); continue
        d = math.hypot(x - cx, y - cy)
        if d < r * 0.98: p[x, y] = (0, 0, 0, 0); continue          # the glow in the middle: the portrait goes there
        v = max(R, G, B)
        lum = int(min(255, v * 1.05))
        p[x, y] = (lum, lum, lum, A)
em = em.transpose(Image.FLIP_LEFT_RIGHT)
big = em.resize((W * 4, H * 4), Image.LANCZOS)
big.save(OUT + 'blazing_emblem.png')
print('emblem', big.size, 'centre', (W - cx) * 4, cy * 4, 'r', r * 4)
# --- bubbles
font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf', 30)
small = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf', 22)
COLS = {'hp': (200, 30, 60), 'kp': (30, 70, 200), 'mp': (140, 50, 210), 'dp': (20, 170, 110), 'hg': (205, 190, 20),
        'ki': (230, 150, 20), 'st': (60, 170, 60), 'lv': (40, 170, 40), 'exp': (170, 110, 40)}
N = 64
for name, (cr, cg, cb) in COLS.items():
    im = Image.new('RGBA', (N, N), (0, 0, 0, 0)); q = im.load()
    for y in range(N):
        for x in range(N):
            dx, dy = (x - N / 2 + .5) / (N / 2 - 2), (y - N / 2 + .5) / (N / 2 - 2)
            d = math.hypot(dx, dy)
            if d > 1: continue
            if d > 0.9: q[x, y] = (12, 12, 18, 255); continue          # dark rim
            light = max(0, 1 - math.hypot(dx + 0.35, dy + 0.4) * 0.9)       # shine top-left
            shade = 0.55 + 0.45 * (1 - d * d)
            k = shade + light * 0.6
            q[x, y] = (min(255, int(cr * k + 80 * light)), min(255, int(cg * k + 80 * light)), min(255, int(cb * k + 80 * light)), 255)
    d = ImageDraw.Draw(im)
    label = name.upper()
    f = small if len(label) > 2 else font
    tw = d.textlength(label, font=f)
    bb = d.textbbox((0, 0), label, font=f)
    tx, ty = (N - tw) / 2, (N - (bb[3] - bb[1])) / 2 - bb[1]
    for ox in (-2, -1, 0, 1, 2):
        for oy in (-2, -1, 0, 1, 2):
            if ox or oy: d.text((tx + ox, ty + oy), label, font=f, fill=(20, 16, 24, 255))
    d.text((tx, ty), label, font=f, fill=(255, 255, 250, 255))
    im.save(OUT + 'blazing_' + name + '.png')
print('ok')
