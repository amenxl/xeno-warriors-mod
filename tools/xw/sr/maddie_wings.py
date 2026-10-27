"""Maddie's wings: the angel wings, white, with the long flight feathers' outer half black (each feather split in two)."""
import json, math
from PIL import Image, ImageDraw
R = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
d = json.load(open(R + 'models/detail/npc/wing_angel.json'))
im = Image.open(R + 'textures/entity/npc/wing_angel_details.png').convert('RGBA'); px = im.load()
for y in range(im.height):                       # base: brighten to white
    for x in range(im.width):
        r, g, b, a = px[x, y]
        if a: l = (r + g + b) / 765; px[x, y] = (int(205 + 50 * l),) * 3 + (a,)
dr = ImageDraw.Draw(im)
for y in range(128, 160):                        # black block for the tips, faint feather lines
    for x in range(0, 64): px[x, y] = (26, 24, 30, 255) if (x + y) % 7 else (44, 42, 50, 255)
for wing in d['bones']['body']['children']:
    for c in wing['children']:
        i = int(c['name'][1:]) % 49
        if not (3 <= i <= 30): continue
        new = []
        for cube in c['cubes']:
            fx, fy, fz = cube['from']; w, h, dd = cube['size']
            tip = w * (0.62 if i <= 30 else 0.4)
            if fx < -0.01:      # right wing: runs -w..0, tip at the far (negative) end
                new.append(dict(uv=[0, 128], **{'from': [fx, fy, fz]}, size=[round(tip, 4), h, dd]))
                new.append(dict(uv=cube['uv'], **{'from': [round(fx + tip, 4), fy, fz]}, size=[round(w - tip, 4), h, dd]))
            else:
                new.append(dict(uv=cube['uv'], **{'from': [fx, fy, fz]}, size=[round(w - tip, 4), h, dd]))
                new.append(dict(uv=[0, 128], **{'from': [round(fx + w - tip, 4), fy, fz]}, size=[round(tip, 4), h, dd]))
        c['cubes'] = new
im.save(R + 'textures/entity/npc/wing_angel_maddie_details.png')
json.dump(d, open(R + 'models/detail/npc/wing_angel_maddie.json', 'w'))
