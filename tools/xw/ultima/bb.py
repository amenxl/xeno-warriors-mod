"""Ultima (Option A) as a Blockbench model, like Rhalix's and Mikiko's."""
import sys, os
sys.path.insert(0, '/home/claude/xw/ultima'); sys.path.insert(0, '/home/claude/xw/detail')
import ultima as U
import core
from PIL import Image, ImageDraw, ImageFont
BONE = {'head': 'Head', 'body': 'Body', 'right_arm': 'RightArm', 'left_arm': 'LeftArm', 'right_leg': 'RightLeg', 'left_leg': 'LeftLeg'}
title, build, gloves, sleek = U.OPTIONS['A']
fit = U.Fit(); build(fit)
atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); U.pack(fit, atlas, region=(0, 0, 256, 256))
spec = dict(U.SPEC); spec['paint'] = U.body_paint(gloves, sleek)
skin = U.paint_skin(spec); face = U.face_img(spec)
m = core.Model('ultima')
names = {'head': 'Hair', 'body': 'Outfit', 'right_arm': 'R Arm', 'left_arm': 'L Arm', 'right_leg': 'R Leg', 'left_leg': 'L Leg'}
for i, c in enumerate(fit.cubes):
    m.add(BONE[c['bone']], f"{names[c['bone']]} {i}", c['lo'], c['hi'], c['col'], c['rot'] or [0, 0, 0], c['origin'])
    m.cubes[-1]['uv'] = c['uv']
os.makedirs('/mnt/user-data/outputs/blockbench', exist_ok=True)
n = core.write_bbmodel(m, skin, atlas, '/mnt/user-data/outputs/blockbench/ultima.bbmodel', [], face)
out = '/home/claude/xw/ultima/out/'
skin.save(out + 'ultima.png'); atlas.save(out + 'ultima_details.png'); face.save(out + 'ultima_face.png')
U.overload_face(face).save(out + 'ultima_face_overload.png')
mj = U.npcs.export(fit, out + 'ultima.json')
im = U.preview(mj, skin, atlas, face, yaws=(0, 35, 90, 180), scale=8)
ImageDraw.Draw(im).text((10, 8), 'Ultima - Option A with his bracelet (Blockbench model)', font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 20), fill=(255, 255, 255))
im.save('/mnt/user-data/outputs/ultima_option_A.png')
print('elements', n)
