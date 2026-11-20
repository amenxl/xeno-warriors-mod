"""Possessed hosts' red eyes: Lexy with Persephone inside, Kyrie with Ava inside.
Faces (every expression) -> <host>_possessed_face*.png, the dialogue portrait -> portrait_<host>_possessed_normal.png,
and the SR boss look sr_kyrie_ava (Kyrie's body, skin, hair and details, red eyes)."""
import colorsys, glob, json, os, shutil
from PIL import Image
R = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
CH, NPC, HUD = R + 'textures/entity/character/', R + 'textures/entity/npc/', R + 'textures/gui/hud/'

def lexy_iris(r, g, b):      # her gold eyes (her skin is a redder brown: hue < 30)
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255); h *= 360
    return 30 <= h <= 62 and s > 0.3 and v > 0.15
def kyrie_iris(r, g, b):     # her purple eyes
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255); h *= 360
    return 235 <= h <= 305 and s > 0.2
IRIS = {'lexy': lexy_iris, 'kyrie': kyrie_iris}

def red(r, g, b):
    v = max(r, g, b) / 255
    return (255, 96, 84) if v > 0.88 else (232, 42, 40) if v > 0.6 else (160, 20, 26) if v > 0.33 else (72, 8, 12)

def recolor(src, dst, host, rows, cols=None):
    im = Image.open(src).convert('RGBA'); px = im.load(); W, H = im.size
    for y in range(*rows):
        for x in range(*(cols or (0, W))):
            r, g, b, a = px[x, y]
            if a and IRIS[host](r, g, b): px[x, y] = red(r, g, b) + (a,)
    im.save(dst)

n = 0
for host in IRIS:
    for f in sorted(glob.glob(CH + host + '_face*.png')):
        recolor(f, f.replace(host + '_face', host + '_possessed_face'), host, (8, 24)); n += 1
    recolor(HUD + 'portrait_%s_normal.png' % host, HUD + 'portrait_%s_possessed_normal.png' % host, host, (60, 86), (38, 92))
# the SR boss: Kyrie's body with Ava inside
for a, b in (('kyrie.png', 'sr_kyrie_ava.png'), ('kyrie_cel.png', 'sr_kyrie_ava_cel.png'),
             ('kyrie_details.png', 'sr_kyrie_ava_details.png'), ('kyrie_details_cel.png', 'sr_kyrie_ava_details_cel.png'),
             ('kyrie_possessed_face.png', 'sr_kyrie_ava_face.png')):
    shutil.copy(CH + a, NPC + b)
shutil.copy(R + 'models/detail/kyrie.json', R + 'models/detail/npc/sr_kyrie_ava.json')
print(n, 'faces')
