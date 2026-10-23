src = open('/home/claude/xw/faces/mix1.py').read().split("variants = [")[0]
ns = {}; exec(src, ns)
face, P, OUT = ns['face'], ns['P'], ns['OUT']
from PIL import Image, ImageDraw, ImageFont
B, T, TH = ns['BROW_E'], ns['BROW_THIN'], ns['BROW_FLAT_THICK']
top = ['..kkkkkk.', 'kkkkkkkkk', '.wddpddw.', '.wWWoodw.', '.wWoopow.', '..wOOOw..']
def eye(lid, rows=top): return rows + [lid]
v = [
    ('1h (as picked)', face(eye=eye('..kkkkk..'))),
    ('1h-1  soft brown lid', face(eye=eye('..KKKKK..'))),
    ('1h-2  outer half lid', face(eye=eye('..kkk....'))),
    ('1h-3  lid + lash flick', face(eye=eye('.kkkkk...'))),
    ('1h-4  curved lid', face(eye=top[:5] + ['.kwOOOwk.', '..kkkkk..'], eye_y=14)),
    ('1h-5  bright iris', face(eye=eye('..kkkkk..', ['..kkkkkk.', 'kkkkkkkkk', '.wdopodw.', '.wWWooow.', '.wWOOpOw.', '..wOOOw..']))),
    ('1h-6  thinner brows', face(brow=T, eye=eye('..kkkkk..'))),
    ('1h-7  smile', face(eye=eye('..kkkkk..'), mouth='smile')),
    ('1h-8  thick flat brows', face(brow=TH, eye=eye('..kkkkk..'))),
]
S = 12; W = 32 * S; cols = 3; rows = 3
sheet = Image.new('RGBA', (cols * (W + 20) + 20, rows * (W + 60)), (34, 34, 42, 255))
dr = ImageDraw.Draw(sheet)
font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 22)
for i, (name, im) in enumerate(v):
    r, c = divmod(i, cols)
    crop = im.crop((0, 0, 32, 32)).resize((W, W), Image.NEAREST)
    x = 20 + c * (W + 20); y = r * (W + 60)
    sheet.paste(crop, (x, y + 46), crop)
    dr.text((x, y + 12), name, fill=(235, 235, 240), font=font)
    im.save(OUT + f'mix1h_{i}.png')
sheet.save(OUT + 'mikiko_face_1h_variants.png')
print('ok')
