"""Battle-damage overlays: scratches on skin, dirt/soot smudges and small tears on clothes. Level 1 = hurt, 2 = badly hurt."""
from PIL import Image
import random

TEX = '/home/claude/xw/tex/'
OUT = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'
SKIN = {'mikiko': (0x96, 0x62, 0x40), 'rhalix': (0x5a, 0x36, 0x21)}

def near(c, ref, tol=38):
    return sum(abs(a - b) for a, b in zip(c[:3], ref)) < tol * 3

def overlay(src, name, level, seed, density):
    img = Image.open(TEX + src).convert('RGBA')
    w, h = img.size
    out = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    px, op = img.load(), out.load()
    rnd = random.Random(seed)
    opaque = [(x, y) for y in range(h) for x in range(w) if px[x, y][3] > 0]
    if not opaque: return out
    n = int(len(opaque) * density * (1 if level == 1 else 2.2))
    def put(x, y, c):
        if 0 <= x < w and 0 <= y < h and px[x, y][3] > 0 and op[x, y][3] == 0:
            op[x, y] = c
    for _ in range(n):
        x, y = rnd.choice(opaque)
        if near(px[x, y], SKIN[name]):
            # scratch: a short diagonal cut with a lighter edge
            d = rnd.choice([(1, 1), (1, -1)])
            for i in range(rnd.randint(2, 3)):
                put(x + d[0] * i, y + d[1] * i, (122, 30, 26, 210))
            put(x + 1, y, (190, 110, 90, 120))
        else:
            r = rnd.random()
            if r < 0.45:     # dirt / soot smudge
                c = (60, 46, 34, 110) if rnd.random() < 0.6 else (24, 20, 20, 120)
                for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1), (-1, 0)):
                    if rnd.random() < 0.75: put(x + dx, y + dy, c)
            elif r < 0.8:    # little tear showing darkness underneath
                put(x, y, (18, 14, 14, 230)); put(x + 1, y, (40, 30, 28, 160))
                if level == 2: put(x, y + 1, (18, 14, 14, 200))
            else:            # scuffed, frayed light edge
                put(x, y, (220, 210, 200, 70))
    return out

for name in ('mikiko', 'rhalix'):
    for level in (1, 2):
        overlay(f'{name}.png', name, level, 11 * level + len(name), 0.035).save(OUT + f'{name}_damage{level}.png')
        overlay(f'{name}_details.png', name, level, 17 * level + len(name), 0.02).save(OUT + f'{name}_details_damage{level}.png')
    # face: a cut on the cheek and some grime; badly hurt adds a plaster and a bruise
    for level in (1, 2):
        f = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); p = f.load()
        for i, (x, y) in enumerate([(24, 19), (25, 20), (26, 21)]):
            p[x, y] = (130, 30, 26, 220)
        for x, y in [(6, 24), (7, 24), (7, 25), (26, 26), (27, 26)]:
            p[x, y] = (60, 46, 34, 120)
        if level == 2:
            for x in range(4, 10):
                for y in (20, 21):
                    p[x, y] = (236, 214, 180, 255) if x not in (4, 9) else (200, 176, 140, 255)
            p[6, 20] = (210, 186, 150, 255); p[7, 21] = (210, 186, 150, 255)
            for x, y in [(20, 20), (21, 20), (22, 21), (20, 21)]:
                p[x, y] = (90, 50, 90, 110)
            p[16, 27] = (130, 30, 26, 200)       # split lip
        f.save(OUT + f'{name}_face_damage{level}.png')

prev = Image.new('RGBA', (64 * 4 * 3, 64 * 3), (70, 70, 80, 255))
for i, name in enumerate(('mikiko', 'rhalix')):
    for level in (0, 1, 2):
        base = Image.open(TEX + f'{name}.png').convert('RGBA')
        if level: base.alpha_composite(Image.open(OUT + f'{name}_damage{level}.png'))
        prev.paste(base.resize((192, 192), Image.NEAREST), ((i * 3 + level) * 64 * 0 + (i * 3 + level) * 128, 0))
prev.save('/tmp/claude-0/-home-claude/b2fe60cb-fd40-5a94-8ee2-7d1a501fd998/scratchpad/damage.png')
print('damage ok')
