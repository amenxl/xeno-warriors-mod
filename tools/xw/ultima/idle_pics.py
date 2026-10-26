import sys, math
sys.path.insert(0, '/home/claude/xw/ultima')
import ultima as U
from PIL import Image, ImageDraw, ImageFont
CH = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'
title, build, gloves, sleek = U.OPTIONS['A']
fit = U.Fit(); build(fit); atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); U.pack(fit, atlas, region=(0, 0, 256, 256))
spec = dict(U.SPEC); spec['paint'] = U.body_paint(gloves, sleek)
skin = U.paint_skin(spec); mj = U.npcs.export(fit, '/tmp/u.json')
face = lambda n: Image.open(CH + f'ultima_face{n}.png').convert('RGBA')
FN = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 15)
POCKETS = dict(pose={'right_arm': [0.18, 0.25, 0.12], 'left_arm': [0.18, -0.25, -0.12], 'head': [0, 0, 0.08]}, bends={})
CROSSED = dict(pose={'right_arm': [-0.95, -0.62, 0.12], 'left_arm': [-1.05, 0.62, -0.12], 'head': [0, 0, 0.12], 'left_leg': [-0.08, 0, -0.08], 'right_leg': [0.05, 0, 0.06]},
               bends={'right_arm': -0.9, 'left_arm': -0.9})
LEAN = dict(pose={'right_arm': [-0.95, -0.62, 0.12], 'left_arm': [-1.05, 0.62, -0.12], 'head': [0, 0, 0.12], 'body': [-0.12, 0, 0], 'right_leg': [0.15, 0, -0.22], 'left_leg': [-0.1, 0, -0.04]},
            bends={'right_arm': -0.9, 'left_arm': -0.9})
NECK = dict(pose={'right_arm': [-2.55, 0.15, -0.75], 'left_arm': [0.18, -0.25, -0.12], 'head': [0.25, 0, -0.15], 'left_leg': [-0.08, 0, -0.08], 'right_leg': [0.05, 0, 0.06]},
            bends={'right_arm': -1.7, 'left_arm': -0.6})
SQUAT = dict(pose={'right_leg': [-1.45, 0.3, 0.12], 'left_leg': [-1.45, -0.3, -0.12], 'right_arm': [-1.1, -0.25, 0.15], 'left_arm': [-0.7, 0.1, -0.1], 'head': [0.2, 0, 0]},
             bends={'right_leg': 2.3, 'left_leg': 2.3, 'right_arm': -0.6, 'left_arm': -0.3},
             offset={k: [0, 5.5, -2 if 'leg' in k else 0] for k in ('head', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg')})
KICK = dict(pose={'right_arm': [0.28, 0.25, 0.12], 'left_arm': [0.28, -0.25, -0.12], 'right_leg': [-1.45, 0, 0.1], 'left_leg': [0.25, 0, 0], 'body': [0, -0.15, 0]}, bends={})
def shot(p, f, yaw, label, scale=8):
    im = U.preview(mj, skin, atlas, face(f), yaws=(yaw,), scale=scale, **p)
    ImageDraw.Draw(im).text((6, 6), label, font=FN, fill=(255, 255, 255)); return im
tiles = [shot(POCKETS, '', 25, '25%: hands in pockets (walking)'), shot(LEAN, '_bored', 30, 'Leaning on a wall, arms crossed'),
         shot(CROSSED, '_annoyed', 20, 'Arms crossed'), shot(NECK, '_bored', 20, 'Rubbing his neck, hand in pocket'),
         shot(SQUAT, '_bored', 60, 'Squatting (crouch still)'), shot(KICK, '_bored', 75, 'Lazy kick (attack)')]
tw, th = tiles[0].size
sheet = Image.new('RGB', (tw * 3, th * 2), (30, 30, 36))
for i, t in enumerate(tiles): sheet.paste(t, ((i % 3) * tw, (i // 3) * th))
sheet.save('/mnt/user-data/outputs/ultima_idle_poses.png'); print(sheet.size)

# animation: pockets -> arms crossed -> neck rub -> pockets -> lazy kick -> pockets
def lerp_pose(a, b, t):
    out = {}
    for key in ('pose', 'bends', 'offset'):
        A, B = a.get(key, {}), b.get(key, {})
        keys = set(A) | set(B)
        d = {}
        for k in keys:
            va, vb = A.get(k), B.get(k)
            if isinstance(va, list) or isinstance(vb, list):
                va = va or [0, 0, 0]; vb = vb or [0, 0, 0]
                d[k] = [x + (y - x) * t for x, y in zip(va, vb)]
            else:
                d[k] = (va or 0) + ((vb or 0) - (va or 0)) * t
        out[key] = d
    return out
seq = [(POCKETS, CROSSED, 8, '_bored'), (CROSSED, CROSSED, 10, '_annoyed'), (CROSSED, NECK, 8, '_bored'), (NECK, NECK, 10, '_bored'),
       (NECK, POCKETS, 8, ''), (POCKETS, KICK, 5, '_bored'), (KICK, POCKETS, 6, '_bored'), (POCKETS, SQUAT, 8, '_bored'), (SQUAT, SQUAT, 10, '_blink'), (SQUAT, POCKETS, 8, '')]
frames = []
for a, b, n, f in seq:
    for i in range(n):
        t = i / max(1, n - 1); t = t * t * (3 - 2 * t)
        im = U.preview(mj, skin, atlas, face(f), yaws=(35,), scale=6, **lerp_pose(a, b, t))
        frames.append(im.convert('P', palette=Image.ADAPTIVE))
frames[0].save('/mnt/user-data/outputs/ultima_idle_animation.gif', save_all=True, append_images=frames[1:], duration=70, loop=0)
print('gif', len(frames))
