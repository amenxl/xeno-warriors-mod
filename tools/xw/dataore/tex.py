import random
from PIL import Image, ImageDraw
T = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/'
rnd = random.Random(5)
# Data Ore: deepslate-dark stone with bright green circuit traces and nodes
im = Image.new('RGBA', (16, 16)); px = im.load()
for y in range(16):
    for x in range(16):
        v = 58 + rnd.randint(-10, 10); px[x, y] = (v, v, v + 6, 255)
G, G2 = (60, 255, 140, 255), (30, 160, 90, 255)
traces = [[(2, 3), (6, 3), (6, 7), (9, 7)], [(12, 2), (12, 6), (14, 6)], [(3, 12), (7, 12), (7, 10), (11, 10), (11, 13)], [(1, 8), (3, 8)]]
d = ImageDraw.Draw(im)
for t in traces:
    for a, b in zip(t, t[1:]): d.line([a, b], fill=G2)
    for (x, y) in (t[0], t[-1]): d.rectangle([x - 1 if x > 0 else x, y - 1 if y > 0 else y, x, y], fill=G)
for (x, y) in [(9, 7), (14, 6), (11, 13), (6, 3)]: px[x, y] = (200, 255, 220, 255)
im.save(T + 'block/data_ore.png')
# Data Shard: a green crystal chip with binary-ish glints
s = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(s)
d.polygon([(8, 1), (13, 6), (11, 14), (5, 14), (3, 6)], fill=(40, 200, 110, 255), outline=(10, 80, 40, 255))
d.polygon([(8, 2), (11, 6), (8, 12), (5, 6)], fill=(110, 255, 170, 255))
for (x, y) in [(7, 4), (9, 7), (6, 9), (10, 11)]: s.putpixel((x, y), (230, 255, 240, 255))
s.save(T + 'item/data_shard.png')
# Modification Chip: dark board with gold pins and a green core
c = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(c)
d.rectangle([3, 3, 12, 12], fill=(30, 34, 44, 255), outline=(12, 14, 20, 255))
for i in range(4, 12, 2):
    d.point([(i, 2), (i, 13), (2, i), (13, i)], fill=(230, 190, 70, 255))
d.rectangle([6, 6, 9, 9], fill=(60, 230, 130, 255)); d.point([(7, 7)], fill=(220, 255, 230, 255))
c.save(T + 'item/mod_chip.png')
sheet = Image.new('RGB', (3 * 150, 170), (30, 34, 40))
for i, im2 in enumerate([im, s, c]):
    big = im2.resize((128, 128), Image.NEAREST); sheet.paste(big, (i * 150 + 11, 8), big)
ImageDraw.Draw(sheet).text((20, 145), 'Data Ore           Data Shard          Mod Chip', fill=(255, 255, 255))
sheet.save('/mnt/user-data/outputs/data_ore_items.png')
