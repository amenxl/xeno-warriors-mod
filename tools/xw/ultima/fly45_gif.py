"""Ultima's new 45% flying attacks: guard, jab-jab, spinning roundhouse kick (same numbers as UltimaAnim.flying)."""
import sys, math, json
sys.path.insert(0, '/home/claude/xw/ultima')
import ultima as U
from PIL import Image, ImageDraw, ImageFont
CH = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'
mj = json.load(open('/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/models/detail/ultima.json'))
skin = Image.open(CH + 'ultima.png').convert('RGBA'); det = Image.open(CH + 'ultima_details.png').convert('RGBA')
face = Image.open(CH + 'ultima_face_focused.png').convert('RGBA')
F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 16)
def lerp(a, b, t): return a + (b - a) * t
def frame_pose(age, atk, step):
    bob = math.sin(age * 0.18)
    p = {'body': [0.32, 0, 0], 'head': [-0.25, 0, 0],
         'right_leg': [0.55 + bob * .08, 0, 0.06], 'left_leg': [0.3 - bob * .08, 0, -0.06],
         'right_arm': [-1.35 + bob * .05, -0.35, 0.1], 'left_arm': [-1.25 - bob * .05, 0.4, -0.1]}
    bends = {'right_arm': -1.25, 'left_arm': -1.3, 'right_leg': 0.45, 'left_leg': 0.6}
    off = {'right_leg': [0, 0, 3.2], 'left_leg': [0, 0, 3.0]}
    label = 'guard'
    if atk > 0:
        s = math.sin(atk * math.pi)
        if step % 3 == 0:
            sweep = atk / .5 if atk < .5 else 1; label = 'roundhouse kick!'
            p['body'][1] = 0.85 * s; p['head'][1] = -0.4 * s
            p['right_leg'] = [-1.35 * s, -0.9 + 1.6 * sweep, 0.75 * s]; p['left_leg'] = [0.45, 0, 0]
            p['right_arm'] = [-0.4, 0, 1.1 * s]; p['left_arm'] = [-0.4, 0, -1.1 * s]
            bends = {'right_arm': -0.3, 'left_arm': -0.3, 'right_leg': 0.1, 'left_leg': 0.9}
        else:
            right = step % 2 == 1; label = 'jab (' + ('right' if right else 'left') + ')'
            out = math.sin(min(1, atk * 1.6) * math.pi / 2); back = (atk - .62) / .38 if atk > .62 else 0; ext = out * (1 - back)
            hit, oth = ('right_arm', 'left_arm') if right else ('left_arm', 'right_arm')
            p[hit] = [lerp(p[hit][0], -1.62, ext), lerp(p[hit][1], 0.12 if right else -0.12, ext), p[hit][2]]
            p[oth] = [lerp(p[oth][0], -0.75, ext), p[oth][1], p[oth][2]]
            p['body'][1] = (-0.38 if right else 0.38) * ext
            bends[hit] = bends[hit] * (1 - ext); bends[oth] = bends[oth] - 0.3 * ext
    return p, bends, off, label
frames = []
seq = []                                   # (atk frames per hit) guard, jab R, jab L, kick, ... ; 8 frames per hit
step = 0
for cycle in range(2):
    seq += [(0, step)] * 6
    for h in range(3):
        step += 1
        seq += [((i + 1) / 9, step) for i in range(9)]
        seq += [(0, step)] * 2
for i, (atk, st) in enumerate(seq):
    pose, bends, off, label = frame_pose(i * 2, atk, st)
    imgs = []
    for yaw in (60, 15):
        im = U.preview(mj, skin, det, face, yaws=(yaw,), scale=5, pose=pose, bends=bends, offset=off, wide=48, tall=56, top=12).convert('RGBA')
        px = im.load()
        for y in range(im.height):
            for x in range(im.width):
                if px[x, y][:3] == (40, 44, 52): px[x, y] = (0, 0, 0, 0)
        imgs.append(im)
    W, H = imgs[0].width * 2, imgs[0].height + 30
    bg = Image.new('RGBA', (W, H)); d = ImageDraw.Draw(bg)
    for y in range(H): d.line([(0, y), (W, y)], fill=(90 + y // 8, 140 + y // 10, 200, 255))
    for k in range(5):
        cx = (k * 160 - i * 9) % (W + 200) - 100; d.ellipse([cx, 40 + k * 50, cx + 120, 70 + k * 50], fill=(235, 240, 250, 180))
    bobv = math.sin(i * 0.36) * 4
    for j, im in enumerate(imgs): bg.alpha_composite(im, (j * imgs[0].width, int(20 + bobv)))
    d.rectangle([0, 0, W, 24], fill=(16, 18, 26, 255)); d.text((8, 3), 'Ultima 45% - flying: ' + label, font=F, fill=(140, 255, 200))
    frames.append(bg.convert('RGB').convert('P', palette=Image.ADAPTIVE))
frames[0].save('/tmp/ultima_45_flying.gif', save_all=True, append_images=frames[1:], duration=60, loop=0)
print(len(frames))
