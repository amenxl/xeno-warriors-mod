import sys
sys.argv = ['restyle.py', 'mikiko', '/tmp/x/']
src = open('/home/claude/xw/faces/restyle.py').read().split("if __name__ == '__main__':")[0]
ns = {'__name__': 'x'}; exec(src, ns)
face, EYES, BROWS = ns['face'], ns['EYES'], ns['BROWS']
from PIL import Image, ImageDraw, ImageFont
OUT = '/tmp/claude-0/-home-claude/b2fe60cb-fd40-5a94-8ee2-7d1a501fd998/scratchpad/faces/'
BROWS['steep'] = (['K........', 'KKK......', '..KKKk...', '....kkkk.'], 9)
V = {
    'Current (fierce)': 'fierce',
    'A1  slanted lid': ['', 'kk.......', '.kkkkk...', '.wwkkkkk.', '.wWoopok.', '..wOOOw..', '..kkkkk..'],
    'A2  narrow glare': ['', '', 'kkk......', '.kkkkkkkk', '.wWoopdkk', '..kkkkk..'],
    'A3  small fierce iris': ['', 'kkk......', '.kkkkkkk.', 'kwwWopwwk', 'kwwoOowwk', '.kwwwwwk.', '..kkkkk..'],
    'A4  shadowed glare': ['hhh......', 'kkkhhh...', '.kkkkkkk.', '.wdWopdk.', '.wwoOoww.', '..kkkkk..', ''],
    'A5  sharp triangle': ['', 'k........', 'kkkk.....', '.wwkkkk..', '.wWookkk.', '..wOOk...', '..kkk....'],
    'A6  glossy, lids pressed': ['', '', 'kkkkk....', '.kkkkkkkk', '.wWWopdw.', '.wWoopow.', '..kkkkk..'],
}
rows = []
for brow, mouth in (('angry', 'grit'), ('steep', 'shout')):
    row = []
    for name, e in V.items():
        if isinstance(e, list):
            EYES['tmp'] = e
            im = face(brow=brow, eyes='tmp', mouth=mouth)
        else:
            im = face(brow=brow, eyes=e, mouth=mouth)
        row.append((name if brow == 'angry' else name.split()[0] + '  (shouting)', im))
    rows.append(row)
S = 10; W = 32 * S
sheet = Image.new('RGBA', (len(V) * (W + 16) + 16, 2 * (W + 52)), (34, 34, 42, 255))
dr = ImageDraw.Draw(sheet)
font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 17)
for r, row in enumerate(rows):
    for i, (name, im) in enumerate(row):
        crop = im.crop((0, 0, 32, 32)).resize((W, W), Image.NEAREST)
        x = 16 + i * (W + 16); y = r * (W + 52)
        sheet.paste(crop, (x, y + 40), crop)
        dr.text((x, y + 12), name, fill=(235, 235, 240), font=font)
sheet.save(OUT + 'mikiko_angry_eyes.png')
print('ok')
