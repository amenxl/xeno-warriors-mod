"""Demon's Remnants faces: Rhalix's eyes glowing red (bright red irises, red glow on the skin around them)."""
import glob
from PIL import Image
CH = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'
def demon(src, dst):
    im = Image.open(src).convert('RGBA'); px = im.load(); W, H = im.size
    eye = set()
    for y in range(10, 22):
        for x in range(32):
            r, g, b, a = px[x, y]
            if a and r > 140 and g < 90 and b < 90: eye.add((x, y))
    for (x, y) in eye: px[x, y] = (255, 70, 60, 255)
    for (x, y) in list(eye):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
            q = (x + dx, y + dy)
            if q in eye or not (0 <= q[0] < 32 and 0 <= q[1] < 32): continue
            r, g, b, a = px[q]
            if a and not (r < 40 and g < 40 and b < 40):           # glow onto skin (not the dark lash lines)
                px[q] = (min(255, int(r * 0.55 + 120)), int(g * 0.5), int(b * 0.5), a)
    im.save(dst)
n = 0
for f in sorted(glob.glob(CH + 'rhalix_face*.png')):
    demon(f, f.replace('rhalix_face', 'rhalix_demon_face')); n += 1
print(n)
