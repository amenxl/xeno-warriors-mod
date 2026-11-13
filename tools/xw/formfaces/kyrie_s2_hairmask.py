"""kyrie_s2_details_hairmask.png: Kyrie's hair (the head's own strands in her detail model) from her details atlas, as bright
greyscale - tinted in game for DragonMineZ hair colours (Super Saiyan gold...). Everything else transparent."""
import json, math
from PIL import Image
CH = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
model = json.load(open(CH + 'models/detail/kyrie_s2.json'))
atlas = Image.open(CH + 'textures/entity/character/kyrie_s2_details.png').convert('RGBA'); px = atlas.load()
mask = Image.new('RGBA', atlas.size, (0, 0, 0, 0)); mp = mask.load()
n = 0
def cubes(node):
    for c in node.get('cubes', []): yield c
    for ch in node.get('children', []): yield from cubes(ch)
for strand in model['bones']['head'].get('children', []):
    if strand.get('name') in ('glasses', 'goggles', 'hood_up'): continue
    for c in cubes(strand):
        w, h, d = [max(1, math.ceil(v - 1e-6)) for v in c['size']]
        u, v = c['uv']
        for x in range(u, u + 2 * d + 2 * w):
            for y in range(v, v + d + h):
                if x >= atlas.width or y >= atlas.height: continue
                r, g, b, a = px[x, y]
                if a == 0: continue
                lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255
                gv = int(255 * (0.62 + 0.38 * min(1, lum * 1.6)))
                mp[x, y] = (gv, gv, gv, 255); n += 1
mask.save(CH + 'textures/entity/character/kyrie_s2_details_hairmask.png')
print(n, 'hair pixels')
