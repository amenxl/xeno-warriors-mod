"""Previews drawn faces on Ultima's real model: renders the head front-on and pastes the hi-res face over it."""
import sys
sys.path.insert(0, '/home/claude/xw/ultima'); sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/ultima')
import ultima as U, drawn_faces as D
from PIL import Image, ImageDraw, ImageFont
title, build, gloves, sleek = U.OPTIONS['A']
fit = U.Fit(); build(fit)
atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); U.pack(fit, atlas, region=(0, 0, 256, 256))
mj = U.npcs.export(fit, '/tmp/ult_tmp.json')
spec = dict(U.SPEC); spec['paint'] = U.body_paint(gloves, sleek)
skin = U.paint_skin(spec)
R = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'
skin = Image.open(R + 'ultima.png').convert('RGBA'); atlas = Image.open(R + 'ultima_details.png').convert('RGBA')
import json
mj = json.load(open('/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/models/detail/ultima.json'))
def shot(face_tex, scale=14, crop=True):
    marker = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); marker.paste((255, 0, 255, 255), (0, 0, 32, 32))
    m = U.preview(mj, skin, atlas, marker, yaws=(0,), scale=scale).convert('RGB')
    px = m.load(); xs = []; ys = []
    for y in range(m.height):
        for x in range(m.width):
            if px[x, y] == (255, 0, 255): xs.append(x); ys.append(y)
    box = (min(xs), min(ys), max(xs) + 1, max(ys) + 1)
    blank = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    base = U.preview(mj, skin, atlas, blank, yaws=(0,), scale=scale).convert('RGBA')
    hi = face_tex.crop((0, 0, 128, 128)).resize((box[2] - box[0], box[3] - box[1]), Image.LANCZOS)
    # the face only shows where the hair doesn't cover it: paste only over pixels that were the marker
    mask = Image.new('L', base.size, 0); mp = mask.load()
    for y in range(box[1], box[3]):
        for x in range(box[0], box[2]):
            if px[x, y] == (255, 0, 255): mp[x, y] = 255
    layer = Image.new('RGBA', base.size, (0, 0, 0, 0)); layer.paste(hi, box[:2])
    a = layer.split()[3]; from PIL import ImageChops
    base.paste(layer, (0, 0), ImageChops.multiply(a, mask))
    if crop:
        W, H = base.size
        cx, cy = (box[0] + box[2]) // 2, (box[1] + box[3]) // 2
        s = int((box[2] - box[0]) * 0.95)
        base = base.crop((cx - s, cy - int(s * 1.05), cx + s, cy + int(s * 0.9)))
    return base
