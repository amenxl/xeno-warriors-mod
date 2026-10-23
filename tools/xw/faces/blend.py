import importlib.util, sys
spec = importlib.util.spec_from_file_location('styles', '/home/claude/xw/faces/styles.py')
# reuse helpers without re-running the sheets: exec only the top half
src = open('/home/claude/xw/faces/styles.py').read().split("styles = [('Current'")[0]
ns = {}
exec(src, ns)
blank, both, grid, P, OUT = ns['blank'], ns['both'], ns['grid'], ns['P'], ns['OUT']
from PIL import Image, ImageDraw, ImageFont

BROW_E = ['KK.......', '.KKKKk...', '...kkkkk.']          # Illustration 08's confident angled brows
BROW_SOFT = ['.KKk.....', 'KKKKKkk..', '....kkkk.']        # a little lighter, same angle

def face(brow, eye, brow_y=10, eye_y=14, mouth='smirk'):
    im = blank()
    both(im, brow, 3, brow_y)
    both(im, eye, 3, eye_y)
    grid(im, ['h.', 'h.', 'hH'], 15, 19)                     # E's nose
    if mouth == 'smirk':
        grid(im, ['......k', 'kkkkkk.'], 13, 24)
    else:
        grid(im, ['..kkkk..', '.k....k.', '..RRRR..'], 12, 24)
    for y in range(17, 29): im.putpixel((2, y), P['h'])
    return im

# 1: E's sharp lash and brows, A's bigger glossy iris with the 2x2 shine
v1 = face(BROW_E, ['..kkkkkk.',
                   'kkkkkkkkk',
                   '.wddpddw.',
                   '.wWWoodw.',
                   '.wWoopow.',
                   '..wOOOw..',
                   '...hhh...'])
# 2: almond eye one row taller than E, gloss highlight + lower sparkle, winged outer lash
v2 = face(BROW_E, ['.......k.',
                   '.kkkkkkkk',
                   'kkddddddk',
                   '.wdWWpdw.',
                   '.woWoooW.',
                   '..wOOOOw.',
                   '...kkk...'])
# 3: closer to A's round eye, E's brows softened, small smile
v3 = face(BROW_SOFT, ['..kkkkk..',
                      '.kkkkkkk.',
                      'kwdWWpdwk',
                      '.woWWoow.',
                      '.wooopow.',
                      '..wOOOw..',
                      '...kkk...'], mouth='smile')
# 4: E's eye shape with A's warm gradient and two shines, eyes a touch lower and brows closer (more intense)
v4 = face(BROW_E, ['..kkkkkk.',
                   'kkkkkkkkk',
                   '.wdWpddw.',
                   '.woWoodw.',
                   '.wOooOOw.',
                   '..wOOWw..',
                   '...hhhh..'], brow_y=10, eye_y=15)

items = [('A  Anime glossy', ns['style_anime']()), ('E  (no blush)', None), ('Mix 1', v1), ('Mix 2', v2), ('Mix 3', v3), ('Mix 4', v4)]
# E without the pink pixels
e = blank()
both(e, BROW_E, 3, 10)
both(e, ['..kkkkkk.', 'kkkkkkkkk', '.wwdpdddw', '.wwoWoodw', '..wOOOOw.', '...hhhh..'], 3, 14)
grid(e, ['h.', 'h.', 'hH'], 15, 19); grid(e, ['......k', 'kkkkkk.'], 13, 24)
for y in range(17, 29): e.putpixel((2, y), P['h'])
items[1] = ('E  (no blush)', e)
S = 12; W = 32 * S
sheet = Image.new('RGBA', (3 * (W + 20) + 20, 2 * (W + 60)), (34, 34, 42, 255))
dr = ImageDraw.Draw(sheet)
font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 22)
for i, (name, im) in enumerate(items):
    r, c = divmod(i, 3)
    crop = im.crop((0, 0, 32, 32)).resize((W, W), Image.NEAREST)
    x = 20 + c * (W + 20); y = r * (W + 60)
    sheet.paste(crop, (x, y + 46), crop)
    dr.text((x, y + 12), name, fill=(235, 235, 240), font=font)
    im.save(OUT + f'mix_{i}.png')
sheet.save(OUT + 'mikiko_face_mixes.png')
print('ok')
