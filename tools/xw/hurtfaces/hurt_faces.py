"""
Hurt faces in three strengths for every face set (each character, their form / drawn / Lunar / Demon sets):
  <set>_face_wince.png       a light hit: their strained face with a bead of sweat
  <set>_face_hurt.png        (already there) a normal hit
  <set>_face_hurt_heavy.png  a big hit: their panicked face with tears streaming and cheeks flushed red
The damage overlays stay shared.
"""
import glob, os
from collections import Counter
from PIL import Image
CH = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'
SWEAT, SWEAT_HI = (120, 200, 255, 255), (220, 245, 255, 255)
TEAR, TEAR_DK = (130, 200, 255, 255), (80, 150, 230, 255)

def skin(px):
    return Counter(px[x, y][:3] for y in range(2, 30) for x in range(2, 30) if px[x, y][3]).most_common(1)[0][0]

def eye_bottoms(px):
    out = []
    for lo, hi in ((3, 16), (16, 29)):
        best = None
        for y in range(12, 22):
            xs = [x for x in range(lo, hi) if px[x, y][3] and sum(px[x, y][:3]) < 130]
            if xs: best = (y, xs)
        if best: out.append((best[0], sum(best[1]) // len(best[1]), min(best[1]), max(best[1])))
    return out

def wince(src, dst):
    im = Image.open(src).convert('RGBA'); px = im.load()
    for (x, y, c) in ((27, 7, SWEAT_HI), (27, 8, SWEAT), (26, 9, SWEAT), (27, 9, SWEAT), (28, 9, SWEAT), (27, 10, SWEAT)):
        px[x, y] = c
    im.save(dst)

def heavy(src, dst):
    im = Image.open(src).convert('RGBA'); px = im.load()
    s = skin(px)
    for (y, cx, x0, x1) in eye_bottoms(px):
        for (tx, length) in ((x0 + 1, 5), (x1 - 1, 4)):                 # two tear streaks from each eye
            for k in range(1, length + 1):
                yy = y + k
                if yy >= 30 or not px[tx, yy][3]: break
                if sum(px[tx, yy][:3]) < 130: continue                      # (not over the mouth line)
                px[tx, yy] = TEAR_DK if k == length else TEAR
        for dx in range(x0 - 1, x1 + 2):                                  # flushed cheeks under the eye
            for yy in (y + 3, y + 4):
                if 0 <= dx < 32 and yy < 30:
                    r, g, b, a = px[dx, yy]
                    if a and abs(r - s[0]) + abs(g - s[1]) + abs(b - s[2]) < 40:
                        px[dx, yy] = (min(255, int(r * 0.7 + 200 * 0.3)), int(g * 0.75), int(b * 0.75), a)
    im.save(dst)

n = 0
for hurt in sorted(glob.glob(CH + '*_face_hurt.png')):
    base = hurt[:-len('_face_hurt.png')]
    strained = base + '_face_strained.png'
    panic = base + '_face_panic.png'
    wince(strained if os.path.exists(strained) else hurt, base + '_face_wince.png')
    heavy(panic if os.path.exists(panic) else hurt, base + '_face_hurt_heavy.png')
    n += 1
print(n, 'face sets')
