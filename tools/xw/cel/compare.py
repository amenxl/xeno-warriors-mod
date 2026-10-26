import sys, json
sys.path.insert(0, '/home/claude/xw/cel'); sys.path.insert(0, '/home/claude/xw/ultima')
import cel, ultima as U
from PIL import Image, ImageDraw, ImageFont
A = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'; CH = A + 'textures/entity/character/'
F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 18)
def clear(im):
    im = im.convert('RGBA'); px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            if px[x, y][:3] == (40, 44, 52): px[x, y] = (0, 0, 0, 0)
    return im
rows = []
for name in ('rhalix', 'mikiko', 'ultima'):
    mj = json.load(open(A + f'models/detail/{name}.json'))
    cel.cel_skin(CH + f'{name}.png', f'/tmp/cel_{name}.png')
    cel.cel_details(A + f'models/detail/{name}.json', CH + f'{name}_details.png', f'/tmp/cel_{name}_details.png')
    face = Image.open(CH + f'{name}_face.png').convert('RGBA')
    tiles = []
    for tag, sk, de in (('Now', CH + f'{name}.png', CH + f'{name}_details.png'), ('Cel-shaded test', f'/tmp/cel_{name}.png', f'/tmp/cel_{name}_details.png')):
        im = clear(U.preview(mj, Image.open(sk).convert('RGBA'), Image.open(de).convert('RGBA'), face, yaws=(25,), scale=7, wide=40, tall=48, top=12))
        bg = Image.new('RGBA', im.size, (236, 232, 228, 255))
        if tag != 'Now':                                   # the outline pass: a black silhouette a little bigger, behind
            from PIL import ImageFilter
            mask = im.split()[3].point(lambda a: 255 if a else 0).filter(ImageFilter.MaxFilter(5))
            bg.paste((18, 14, 18, 255), (0, 0), mask)
        bg.alpha_composite(im)
        ImageDraw.Draw(bg).text((10, 8), f'{name.title()} - {tag}', font=F, fill=(20, 20, 20)); tiles.append(bg)
    row = Image.new('RGB', (tiles[0].width * 2 + 10, tiles[0].height), (200, 30, 40))
    row.paste(tiles[0], (0, 0)); row.paste(tiles[1], (tiles[0].width + 10, 0)); rows.append(row)
sheet = Image.new('RGB', (rows[0].width, sum(r.height for r in rows) + 10 * (len(rows) - 1)), (200, 30, 40))
y = 0
for r in rows: sheet.paste(r, (0, y)); y += r.height + 10
sheet.save('/tmp/cel_compare.png'); print(sheet.size)
