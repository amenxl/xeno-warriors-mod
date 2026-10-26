from PIL import Image, ImageDraw
T = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/'
# Aurora Medicine: a small white pill bottle with a cyan cap and an Aurora-blue label + cross
im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
d.rectangle([5, 1, 10, 3], fill=(40, 190, 210)); d.line([(5, 1), (10, 1)], fill=(120, 230, 240))
d.rectangle([4, 4, 11, 14], fill=(236, 240, 244)); d.line([(4, 4), (4, 14)], fill=(255, 255, 255)); d.line([(11, 4), (11, 14)], fill=(170, 176, 186))
d.line([(4, 14), (11, 14)], fill=(150, 156, 166))
d.rectangle([4, 7, 11, 11], fill=(52, 120, 200)); d.line([(7, 8), (8, 8)], fill=(255, 255, 255)); d.line([(6, 9), (9, 9)], fill=(255, 255, 255)); d.line([(7, 10), (8, 10)], fill=(255, 255, 255))
im.save(T + 'item/aurora_medicine.png')
# effect icons 18x18
v = Image.new('RGBA', (18, 18), (0, 0, 0, 0)); d = ImageDraw.Draw(v)
d.ellipse([3, 3, 14, 14], fill=(90, 160, 190)); d.ellipse([5, 5, 12, 12], fill=(60, 120, 150))
for (x, y) in [(9, 1), (9, 16), (1, 9), (16, 9), (3, 3), (14, 3), (3, 14), (14, 14)]:
    d.ellipse([x - 1, y - 1, x + 1, y + 1], fill=(120, 200, 220))
d.point([(7, 7), (10, 8), (8, 10)], fill=(180, 230, 240))
v.save(T + 'mob_effect/aurora_virus.png')
s = Image.new('RGBA', (18, 18), (0, 0, 0, 0)); d = ImageDraw.Draw(s)
d.polygon([(9, 1), (16, 4), (15, 11), (9, 17), (3, 11), (2, 4)], fill=(80, 200, 190)); d.polygon([(9, 3), (14, 5), (13, 10), (9, 15), (5, 10), (4, 5)], fill=(150, 235, 225))
d.rectangle([8, 5, 9, 13], fill=(255, 255, 255)); d.rectangle([5, 8, 12, 9], fill=(255, 255, 255))
s.save(T + 'mob_effect/virus_immunity.png')
big = Image.new('RGBA', (16 * 3 + 18 * 2 * 3 + 40, 60), (40, 44, 52)); big.paste(im.resize((48, 48), 0), (6, 6), im.resize((48, 48), 0))
big.paste(v.resize((54, 54), 0), (64, 3), v.resize((54, 54), 0)); big.paste(s.resize((54, 54), 0), (126, 3), s.resize((54, 54), 0))
big.save('/tmp/virus_icons.png')
