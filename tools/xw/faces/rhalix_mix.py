import sys
from PIL import Image, ImageDraw, ImageFont
CUR = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/rhalix_face.png'
OUT = '/tmp/claude-0/-home-claude/b2fe60cb-fd40-5a94-8ee2-7d1a501fd998/scratchpad/faces/'
base = Image.open(CUR).convert('RGBA')
SK = base.getpixel((15, 26))
P = {'1': (14, 8, 9), '2': (239, 236, 235), '3': (201, 196, 196), '4': (122, 10, 14), '5': (255, 255, 255),
     '6': (216, 20, 28), '7': (42, 6, 4), '8': (255, 74, 70), '9': (20, 12, 16), 'h': tuple(int(c * .82) for c in SK[:3])}
def make(brow, eye, by=10, ey=13):
    im = base.copy()
    for y in range(9, 21):
        for x in range(3, 29): im.putpixel((x, y), SK)
    for rows, y0 in ((brow, by), (eye, ey)):
        for j, r in enumerate(rows):
            for i, ch in enumerate(r):
                if ch == '.': continue
                im.putpixel((4 + i, y0 + j), P[ch] + (255,)); im.putpixel((27 - i, y0 + j), P[ch] + (255,))
    return im
CUR_BROW = ['...9999..', '999....9.']
FLAT_BROW = ['.........', '.9999999.']
LOW_BROW = ['.........', '..99999..', '99.....9.']
V = [('Current', base),
     ('Mix A  heavy lids', make(CUR_BROW, ['111......', '.11111111', '.11111111', '.2344452.', '..26776..', '...8888..'], ey=14)),
     ('Mix B  lids + lash line', make(LOW_BROW, ['', '111111111', '.11111111', '.2446642.', '..26776..', '...1111..'], ey=14)),
     ('Mix C  droopy corners', make(CUR_BROW, ['', '.1111111.', '111111111', '11344452.', '..26776..', '...8888..'], ey=14)),
     ('Mix D  half-lid, flat brow', make(FLAT_BROW, ['', 'hhh......', '111111111', '.11111111', '.2445642.', '..h8888h.'], ey=13)),
     ]
S = 10; W = 320
sheet = Image.new('RGBA', (len(V) * (W + 16) + 16, W + 56), (34, 34, 42, 255))
dr = ImageDraw.Draw(sheet); font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 17)
for i, (n, im) in enumerate(V):
    c = im.crop((0, 0, 32, 32)).resize((W, W), Image.NEAREST); x = 16 + i * (W + 16)
    sheet.paste(c, (x, 44), c); dr.text((x, 14), n, fill=(235, 235, 240), font=font)
sheet.save(OUT + 'rhalix_mix.png'); print('ok')
