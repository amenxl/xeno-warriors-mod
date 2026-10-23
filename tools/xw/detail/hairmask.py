"""<name>_details_hairmask.png: just the hair from the details atlas, as bright greyscale (tinted in game for
DragonMineZ hair colors like Super Saiyan gold); everything else transparent."""
import json, math, sys
from PIL import Image
OUT = sys.argv[1]
HAIR = ('Dread', 'Lock', 'Strand')
for name in ('mikiko', 'mikiko_foxking', 'rhalix'):
    spec = json.load(open(f'/home/claude/xw/detail/{name}_spec.json'))
    atlas = Image.open(f'{OUT}/{name}_details.png').convert('RGBA')
    px = atlas.load()
    mask = Image.new('RGBA', atlas.size, (0, 0, 0, 0)); mp = mask.load()
    n = 0
    for c in spec['cubes']:
        nm = c['name']
        if not (nm in HAIR or nm.startswith('Hair')):
            continue
        w, h, d = [max(1, math.ceil(c['to'][k] - c['frm'][k] - 1e-6)) for k in range(3)]
        u, v = c['uv']
        for x in range(u, u + 2 * d + 2 * w):
            for y in range(v, v + d + h):
                if x >= atlas.width or y >= atlas.height: continue
                r, g, b, a = px[x, y]
                if a == 0: continue
                lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255
                gv = int(255 * (0.62 + 0.38 * min(1, lum * 1.6)))
                mp[x, y] = (gv, gv, gv, 255)
                n += 1
    mask.save(f'{OUT}/{name}_details_hairmask.png')
    print(name, n, 'hair pixels')
