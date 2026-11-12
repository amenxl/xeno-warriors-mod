"""
DragonMineZ form faces for Mikiko and Rhalix (textures <who>_<form>_face*.png):
  ssj   Super Saiyan 1/2 (and the other Super Saiyan grades): teal-green irises
  ssj3  Super Saiyan 3: teal-green irises and no eyebrows (a heavy brow ridge shadow instead)
  ssj4  Super Saiyan 4: gold irises and the red shadow ringing the eyes
  lssj  Legendary / full power: blank glowing white eyes, no irises
The damage overlays stay shared with the normal faces.
"""
import glob
from collections import Counter
from PIL import Image
CH = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'

def dist(a, b):
    return sum(abs(a[i] - b[i]) for i in range(3))

def sat(c):
    return (max(c) - min(c)) / max(1, max(c))

def components(cells):
    cells = set(cells); out = []
    while cells:
        st = [cells.pop()]; comp = set(st)
        while st:
            x, y = st.pop()
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    q = (x + dx, y + dy)
                    if q in cells: cells.remove(q); comp.add(q); st.append(q)
        out.append(comp)
    return out

VIOLET = False

def analyse(px):
    skin = Counter(px[x, y][:3] for y in range(2, 30) for x in range(2, 30) if px[x, y][3]).most_common(1)[0][0]
    # irises: coloured pixels (more saturated than the skin) next to an eye white or lash
    iris = set()
    for y in range(10, 22):
        for x in range(32):
            r, g, b, a = px[x, y]
            if not a or dist((r, g, b), skin) < 60 or r + g + b < 60 or min(r, g, b) > 190: continue
            reddish = r > g * 1.8 and r > b * 1.6 and r > 110            # Rhalix's red irises (and their dark centres)
            bluish = VIOLET and b > r + 25 and b > g + 25                 # Kyrie's violet irises (not tears)
            if not reddish and not bluish and (r + g + b < 150 or sat((r, g, b)) < sat(skin) + 0.12): continue
            near = any(0 <= x + dx < 32 and 0 <= y + dy < 32 and (min(px[x + dx, y + dy][:3]) > 190 or sum(px[x + dx, y + dy][:3]) < 130)
                       for dx in (-2, -1, 0, 1, 2) for dy in (-2, -1, 0, 1, 2))
            if near: iris.add((x, y))
    # eyebrows: per side, the marks above the eye (the lowest mark from row 12 down is the eye / lash line)
    brows = set()
    for lo, hi in ((0, 16), (16, 32)):
        marks = [(x, y) for y in range(5, 22) for x in range(lo, hi)
                 if px[x, y][3] and dist(px[x, y][:3], skin) > 45 and sat(px[x, y][:3]) < sat(skin) + 0.12]
        comps = components(marks)
        eyes = [c for c in comps if max(y for _, y in c) >= 13 and len(c) >= 3]
        if not eyes: continue
        eye_top = min(min(y for _, y in c) for c in eyes)
        for c in comps:
            if c in eyes: continue
            if max(y for _, y in c) < eye_top and len(c) >= 2: brows |= c
    return skin, iris, brows

def make(src, dst, form):
    im = Image.open(src).convert('RGBA'); px = im.load()
    skin, iris, brows = analyse(px)
    out = im.copy(); po = out.load()
    for (x, y) in iris:
        v = max(px[x, y][:3]) / 255
        v = max(v, 0.45)
        if form in ('ssj', 'ssj3'): po[x, y] = (int(70 * v), int(215 * v), int(185 * v), 255)
        elif form == 'ssj4': po[x, y] = (int(255 * v), int(196 * v), int(36 * v), 255)
        elif form == 'lssj': po[x, y] = (250, 252, 255, 255)
    if form == 'lssj':                                            # pupils gone too: the whole eye glows white
        for y in range(12, 21):
            for x in range(32):
                c = px[x, y]
                if c[3] and sum(c[:3]) < 130 and any((x + dx, y + dy) in iris or min(px[x + dx, y + dy][:3]) > 190
                                                     for dx in (-1, 1) for dy in (0,) if 0 <= x + dx < 32) \
                        and any((x, y + dy) in iris or min(px[x, y + dy][:3]) > 190 for dy in (-1, 1) if 0 <= y + dy < 32):
                    po[x, y] = (250, 252, 255, 255)
    if form == 'ssj3':
        ridge = tuple(int(c * 0.9) for c in skin)
        for (x, y) in brows:
            po[x, y] = skin + (255,)
        for (x, y) in brows:                                      # a heavy brow ridge: a soft shade where the brow was
            if (x, y + 1) not in brows and y + 1 < 32 and dist(px[x, y + 1][:3], skin) < 45: po[x, y + 1] = ridge + (255,)
    if form == 'ssj4':
        red = (170, 28, 30); shade = set()
        for y in range(13, 21):
            for x in range(32):
                if px[x, y][3] and sum(px[x, y][:3]) < 130 and (x, y) not in brows:
                    for dx, dy in ((0, -1), (-1, -1), (1, -1), (-1, 0), (1, 0), (0, -2)):
                        q = (x + dx, y + dy)
                        if 0 <= q[0] < 32 and 0 <= q[1] < 32 and px[q][3] and dist(px[q][:3], skin) < 45: shade.add(q)
        for q in shade:
            r, g, b, a = px[q]
            po[q] = (int(r * 0.35 + red[0] * 0.65), int(g * 0.35 + red[1] * 0.65), int(b * 0.35 + red[2] * 0.65), a)
    out.save(dst)

n = 0
for who in ('rhalix', 'mikiko', 'kyrie'):
    VIOLET = who == 'kyrie'
    for f in sorted(glob.glob(CH + who + '_face*.png')):
        if '_damage' in f: continue
        for form in ('ssj', 'ssj3', 'ssj4', 'lssj'):
            make(f, f.replace(who + '_face', who + '_' + form + '_face'), form); n += 1
print(n)
