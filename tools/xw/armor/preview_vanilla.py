import sys, json
sys.path.insert(0, '/home/claude/xw/outfits')
from kit import *
from PIL import Image, ImageDraw
ARMOR = RES + 'models/armor/'
skin = Image.open(CHAR + 'rhalix.png').convert('RGBA') if __import__('os').path.exists(CHAR + 'rhalix.png') else Image.new('RGBA', (64, 64), (120, 90, 140, 255))
rows = []
for mat in ['leather', 'chainmail', 'iron', 'gold', 'diamond', 'netherite']:
    merged = None
    for slot in ['helmet', 'chestplate', 'leggings', 'boots']:
        j = json.load(open(ARMOR + f'rhalix_{mat}_{slot}.json'))
        if merged is None: merged = j
        else:
            for b, n in j['bones'].items():
                merged['bones'][b]['cubes'] += n['cubes']; merged['bones'][b]['children'] += n['children']
    tex = Image.open(CHAR + f'vanilla_{mat}.png').convert('RGBA')
    if mat == 'leather':      # default leather dye tint
        px = tex.load()
        for y in range(tex.height):
            for x in range(tex.width):
                r, g, b, a = px[x, y]
                if a: px[x, y] = (r * 160 // 255, g * 101 // 255, b * 64 // 255, a)
    im = preview(merged, skin, tex, None, yaws=(20, 160), scale=6)
    ImageDraw.Draw(im).text((6, 4), mat, fill=(255, 255, 255))
    rows.append(im)
W = rows[0].width
sheet = Image.new('RGB', (W * 3, rows[0].height * 2))
for i, im in enumerate(rows): sheet.paste(im, ((i % 3) * W, (i // 3) * im.height))
sheet.save('/mnt/user-data/outputs/vanilla_armor_3d.png'); print(sheet.size)
