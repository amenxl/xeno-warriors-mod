import sys
sys.argv = ['restyle.py', 'rhalix', '/tmp/claude-0/-home-claude/b2fe60cb-fd40-5a94-8ee2-7d1a501fd998/scratchpad/faces/rhalix/']
src = open('/home/claude/xw/faces/restyle.py').read().split("if __name__ == '__main__':")[0]
ns = {'__name__': 'x'}; exec(src, ns)
face, EYES, BROWS, MOUTHS, P, base = ns['face'], ns['EYES'], ns['BROWS'], ns['MOUTHS'], ns['P'], ns['base']
from PIL import Image, ImageDraw, ImageFont
OUT = '/tmp/claude-0/-home-claude/b2fe60cb-fd40-5a94-8ee2-7d1a501fd998/scratchpad/faces/'
# extra parts for Rhalix's laid-back look
EYES['lazy'] = ['', '', 'kkkkkkkkk', '.kkkkkkkk', '.wddpddw.', '.wWoopow.', '..kkkkk..']
EYES['narrow'] = ['', '', '.kkkkkkk.', 'kkkkkkkkk', '.wWoopdw.', '..kkkkk..']
BROWS['low'] = (['.KKKKKk..', '...kkkkk.'], 11)
BROWS['thick'] = (['KKKK.....', 'KKKKKKk..', '...kkkkk.'], 10)
MOUTHS['side_smirk'] = (['.......k', '.kkkkkk.'], 12, 24)
cur = base.copy()
items = [
    ('Current Rhalix', cur),
    ('Same style as Mikiko', face()),
    ('R1  laid-back lids', face(eyes='lazy', mouth='side_smirk')),
    ('R2  narrow + low brows', face(brow='low', eyes='narrow', mouth='flat')),
    ('R3  thick brows', face(brow='thick', eyes='open', mouth='smirk')),
    ('R4  half lids + smile', face(brow='relax', eyes='half', mouth='small_smile')),
]
exprs = [('happy', dict(brow='relax', eyes='happy', mouth='big')), ('fierce', dict(brow='angry', eyes='fierce', mouth='grit')),
         ('smug', dict(eyes='half', mouth='smirk')), ('surprised', dict(brow='raised', eyes='wide', mouth='o')),
         ('sad', dict(brow='sad', eyes='sad', mouth='frown')), ('shout', dict(brow='angry', eyes='fierce', mouth='shout'))]
row2 = [(f'Mikiko style: {n}', face(**sp)) for n, sp in exprs]
S = 10; W = 32 * S
rows = [items, row2]
sheet = Image.new('RGBA', (6 * (W + 16) + 16, 2 * (W + 52)), (34, 34, 42, 255))
dr = ImageDraw.Draw(sheet)
font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 17)
for r, row in enumerate(rows):
    for i, (name, im) in enumerate(row):
        crop = im.crop((0, 0, 32, 32)).resize((W, W), Image.NEAREST)
        x = 16 + i * (W + 16); y = r * (W + 52)
        sheet.paste(crop, (x, y + 40), crop)
        dr.text((x, y + 12), name, fill=(235, 235, 240), font=font)
sheet.save(OUT + 'rhalix_face_styles.png')
print('ok')
