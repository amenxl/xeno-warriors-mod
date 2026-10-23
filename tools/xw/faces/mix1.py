src = open('/home/claude/xw/faces/styles.py').read().split("styles = [('Current'")[0]
ns = {}; exec(src, ns)
blank, both, grid, P, OUT = ns['blank'], ns['both'], ns['grid'], ns['P'], ns['OUT']
from PIL import Image, ImageDraw, ImageFont

BROW_E = ['KK.......', '.KKKKk...', '...kkkkk.']
BROW_THIN = ['K........', '.KKKk....', '...kkkk..']
BROW_STRAIGHT = ['.KKKKKK..', '..kkkkkk.']
BROW_FLAT_THICK = ['KKKKk....', '.KKKKKkk.']
EYE1 = ['..kkkkkk.', 'kkkkkkkkk', '.wddpddw.', '.wWWoodw.', '.wWoopow.', '..wOOOw..', '...hhh...']

def face(brow=BROW_E, eye=EYE1, brow_y=10, eye_y=14, mouth='smirk', blush=False, nose='E', shade=True):
    im = blank()
    both(im, brow, 3, brow_y)
    both(im, eye, 3, eye_y)
    if nose == 'E': grid(im, ['h.', 'h.', 'hH'], 15, eye_y + 6)
    elif nose == 'tip': grid(im, ['.h', 'hn'], 15, eye_y + 7)
    m = {'smirk': (['......k', 'kkkkkk.'], 13, 24), 'flat': (['kkkkkk'], 13, 25),
         'smile': (['k......k', '.kkkkkk.'], 12, 24), 'grin': (['kkkkkkkk', 'k.tttt.k', '.kkkkkk.'], 12, 24),
         'frown': (['.kkkkkk.', 'k......k'], 12, 24)}[mouth]
    grid(im, m[0], m[1], m[2])
    if blush:
        for x in (5, 6, 7, 24, 25, 26): im.putpixel((x, eye_y + 8), P['R'])
    if shade:
        for y in range(17, 29): im.putpixel((2, y), P['h'])
    return im

variants = [
    ('Mix 1 (as picked)', face()),
    ('1a  thinner brows', face(brow=BROW_THIN)),
    ('1b  thick flat brows', face(brow=BROW_FLAT_THICK)),
    ('1c  winged lash', face(eye=['.......kk', '.kkkkkkk.', 'kkddpddw.', '.wWWoodw.', '.wWoopow.', '..wOOOw..', '...hhh...'])),
    ('1d  taller eyes', face(eye=['..kkkkkk.', 'kkkkkkkkk', '.wddpddw.', '.wWWoodw.', '.wWoopow.', '.wooooow.', '..wOOOw..', '...hhh...'], eye_y=13)),
    ('1e  bright iris', face(eye=['..kkkkkk.', 'kkkkkkkkk', '.wdopodw.', '.wWWooow.', '.wWOOpOw.', '..wOOOw..', '...hhh...'])),
    ('1f  smile', face(mouth='smile')),
    ('1g  soft cheeks', face(blush=True)),
    ('1h  lower lid line', face(eye=['..kkkkkk.', 'kkkkkkkkk', '.wddpddw.', '.wWWoodw.', '.wWoopow.', '..wOOOw..', '..kkkkk..'])),
]
S = 12; W = 32 * S
cols = 3
rows = (len(variants) + cols - 1) // cols
sheet = Image.new('RGBA', (cols * (W + 20) + 20, rows * (W + 60)), (34, 34, 42, 255))
dr = ImageDraw.Draw(sheet)
font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 22)
for i, (name, im) in enumerate(variants):
    r, c = divmod(i, cols)
    crop = im.crop((0, 0, 32, 32)).resize((W, W), Image.NEAREST)
    x = 20 + c * (W + 20); y = r * (W + 60)
    sheet.paste(crop, (x, y + 46), crop)
    dr.text((x, y + 12), name, fill=(235, 235, 240), font=font)
    im.save(OUT + f'mix1_{i}.png')
sheet.save(OUT + 'mikiko_face_mix1_variants.png')
print('ok')
