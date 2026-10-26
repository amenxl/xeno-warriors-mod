import sys
sys.path.insert(0, '/home/claude/xw/ultima')
import ultima as U
from PIL import Image, ImageDraw, ImageFont
CH = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'
title, build, gloves, sleek = U.OPTIONS['A']
fit = U.Fit(); build(fit); atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); U.pack(fit, atlas, region=(0, 0, 256, 256))
spec = dict(U.SPEC); spec['paint'] = U.body_paint(gloves, sleek)
skin = U.paint_skin(spec); mj = U.npcs.export(fit, '/tmp/u.json')
face = lambda n: Image.open(CH + f'ultima_face{n}.png').convert('RGBA')
FN = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 16)
shots = [
    ('25% - hands in pockets, bored', {'right_arm': [0.18, 0.25, 0.12], 'left_arm': [0.18, -0.25, -0.12], 'head': [0, 0, 0.1]}, ''),
    ('25% - lazy kick', {'right_arm': [0.28, 0.25, 0.12], 'left_arm': [0.28, -0.25, -0.12], 'right_leg': [-1.45, 0, 0.1], 'left_leg': [0.25, 0, 0], 'body': [0, -0.15, 0]}, '_bored'),
    ('70% - heavy slam (wind-up)', {'right_arm': [-2.9, -0.3, 0.2], 'left_arm': [-2.9, 0.3, -0.2], 'body': [-0.2, 0, 0]}, '_fierce'),
    ('70% - haymaker', {'right_arm': [-1.6, -1.0, 0], 'left_arm': [-0.6, 0, -0.3], 'body': [0, -0.5, 0], 'right_leg': [-0.3, 0, 0], 'left_leg': [0.3, 0, 0]}, '_rage'),
]
tiles = []
YAW = [20, 75, 65, 35]
for i_, (name, pose, f) in enumerate(shots):
    im = U.preview(mj, skin, atlas, face(f), yaws=(YAW[i_],), scale=8, pose=pose)
    ImageDraw.Draw(im).text((6, 6), name, font=FN, fill=(255, 255, 255)); tiles.append(im)
glow = atlas.copy(); gp = glow.load()
for y in range(256):
    for x in range(256):
        r, g, b, a = gp[x, y]
        if a and g > 150 and r < 120: gp[x, y] = (min(255, r + 90), 255, min(255, b + 90), a)
im = U.static(U.preview(mj, skin, glow, face('_overload'), yaws=(25,), scale=8, pose={'right_arm': [-1.5, -0.2, 0], 'left_arm': [-1.5, 0.2, 0]}), seed=7, n=16)
ImageDraw.Draw(im).text((6, 6), 'Overload - green static', font=FN, fill=(160, 255, 200)); tiles.append(im)
tw, th = tiles[0].size
sheet = Image.new('RGB', (tw * len(tiles), th), (30, 30, 36))
for i, t in enumerate(tiles): sheet.paste(t, (i * tw, 0))
sheet.save('/mnt/user-data/outputs/ultima_fighting_styles.png'); print(sheet.size)
