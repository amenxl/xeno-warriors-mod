"""Mikiko face style samples (32x32 HD face). Each style: brows/eyes as character grids (left eye; mirrored for
the right), plus nose/mouth/shading."""
from PIL import Image, ImageDraw, ImageFont
SRC = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/mikiko_face.png'
OUT = '/tmp/claude-0/-home-claude/b2fe60cb-fd40-5a94-8ee2-7d1a501fd998/scratchpad/faces/'
import os; os.makedirs(OUT, exist_ok=True)
base = Image.open(SRC).convert('RGBA')
SKIN = base.getpixel((15, 26))
P = {
    '.': None, 's': SKIN, 'h': (122, 78, 50, 255), 'H': (100, 62, 40, 255), 'l': (172, 118, 82, 255),
    'k': (34, 18, 12, 255), 'K': (60, 34, 24, 255), 'w': (244, 240, 236, 255), 'W': (255, 255, 255, 255),
    'o': (232, 128, 26, 255), 'O': (255, 182, 66, 255), 'd': (176, 78, 14, 255), 'p': (70, 26, 8, 255),
    'r': (128, 66, 50, 255), 'R': (168, 92, 74, 255), 'b': (196, 100, 82, 255), 't': (250, 248, 244, 255),
    'n': (186, 120, 70, 255), 'g': (90, 40, 28, 255),
}


def blank():
    im = base.copy()
    for y in range(6, 30):
        for x in range(2, 30):
            im.putpixel((x, y), SKIN)
    return im


def grid(im, rows, x0, y0, mirror=False):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            c = P.get(ch)
            if c is None: continue
            x = x0 + (len(row) - 1 - i if mirror else i)
            im.putpixel((x, y0 + j), c)


def both(im, rows, xl, y0):
    """left eye at xl, right eye mirrored at the matching spot on the right"""
    grid(im, rows, xl, y0)
    w = len(rows[0])
    grid(im, rows, 32 - xl - w, y0, mirror=True)


def style_anime():
    im = blank()
    both(im, ['..kkkk...',
              'kkK..Kkk.',
              '.........'], 3, 10)                                # tapered, arched brows
    both(im, ['.......kk',
              '.kkkkkkk.',
              'kkdddddk.',
              'wdoWWodw.',
              'wooWopow.',
              'wooopooW.',
              '.wOOOOOw.',
              '..wOOOw..',
              '...kkk...'], 3, 13)                                # big glossy orange eyes, winged lash
    grid(im, ['h', 'h', 'hn'], 16, 19)                            # nose
    grid(im, ['..kkkk..', '.k....k.', '..RRRR..'], 12, 24)        # small mouth, lip shine
    for y in range(18, 28):                                       # cheek shading at the sides
        im.putpixel((2, y), P['h']); im.putpixel((29, y), P['h'])
    return im


def style_cool():
    im = blank()
    both(im, ['.kkkk....',
              'kKKKkkkk.',
              '.....kkk.'], 3, 11)                                # low, angled brows: focused
    both(im, ['kkkkkkkk.',
              '.kkkkkkkk',
              'wddoWdw..',
              '.wooopow.',
              '..hhhh...'], 3, 15)                                # half-lidded, sharp
    grid(im, ['h', 'h', 'h', 'h', 'hn'], 17, 16)                   # nose bridge shadow, lit tip
    im.putpixel((15, 21), P['n'])
    grid(im, ['.kkkkkk.', '..RRRR..'], 12, 25)                    # flat, cool mouth
    for y in range(22, 30):                                       # jaw shadow
        for x in (2, 3, 28, 29): im.putpixel((x, y), P['h'] if x in (3, 28) else P['H'])
    return im


def style_cute():
    im = blank()
    both(im, ['..kkk....', '.k...k...'], 4, 9)                    # little high, curved brows
    both(im, ['..kkkk...',
              '.kddddk..',
              'kdWWoodk.',
              'kdWWoodk.',
              'koooopok.',
              'kOooopOk.',
              'kOOOOWOk.',
              '.kOOOOk..',
              '..kkkk...'], 4, 12)                                # big round sparkly eyes
    both(im, ['bbb', '.b.'], 4, 22)                               # blush
    grid(im, ['h'], 16, 20)
    grid(im, ['.kkkkkkkk.',
              'k.tttttt.k',
              'k.tttttt.k',
              '.kggggggk.',
              '..kkkkkk..'], 11, 23)                              # big grin
    return im


def style_portrait():
    im = blank()
    both(im, ['.KKKKK...',
              'KKKKKKK..'], 4, 11)                                # fuller natural brows
    both(im, ['..hhhhh..',
              '.kkkkkkk.',
              'kwddpdwwk',
              '.wooWoww.',
              '..wOOww..',
              '...hhh...'], 3, 13)                                # crease, lash line, natural eye
    grid(im, ['.h', '.h', 'hh', 'hk'], 15, 17)                    # nose side shadow + nostril
    im.putpixel((17, 20), P['k'])
    grid(im, ['..rrrr..', '.rrrrrr.', '..RlRR..'], 12, 24)        # full lips
    for y in range(14, 29):                                       # soft shading down one side
        im.putpixel((2, y), P['h'])
        if y > 20: im.putpixel((3, y), P['h'])
    for x in range(10, 22): im.putpixel((x, 29), P['h'])          # chin shadow
    return im


styles = [('Current', base.copy()), ('A  Anime glossy', style_anime()), ('B  Cool / sharp', style_cool()),
          ('C  Cute / chibi', style_cute()), ('D  Pixel portrait', style_portrait())]
S = 12
W = 32 * S
sheet = Image.new('RGBA', (len(styles) * (W + 20) + 20, W + 60), (34, 34, 42, 255))
dr = ImageDraw.Draw(sheet)
try:
    font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 22)
except Exception:
    font = ImageFont.load_default()
for i, (name, im) in enumerate(styles):
    crop = im.crop((0, 0, 32, 32)).resize((W, W), Image.NEAREST)
    x = 20 + i * (W + 20)
    sheet.paste(crop, (x, 46), crop)
    dr.text((x, 12), name, fill=(235, 235, 240), font=font)
    im.save(OUT + f'style_{i}.png')
sheet.save(OUT + 'mikiko_face_styles.png')
print('ok')


# ------------------------------------------------------------------ more styles (from Illustration_08 and the chibi sketches)
def style_illustration():
    im = blank()
    both(im, ['KK.......',
              '.KKKKk...',
              '...kkkkk.'], 3, 10)                                 # thick brows angled in: confident
    both(im, ['..kkkkkk.',
              'kkkkkkkkk',
              '.wwdpdddw',
              '.wwoWoodw',
              '..wOOOOw.',
              '...hhhh..'], 3, 13)                                 # sharp almond eyes, amber iris
    for (x, y) in ((20, 21), (21, 22), (22, 21), (23, 22), (24, 21), (25, 22)):
        im.putpixel((x, y), P['b'])                                # blush strokes under one eye
    grid(im, ['h.', 'h.', 'hH'], 15, 19)                           # nose shadow + nostril
    grid(im, ['......k', 'kkkkkk.'], 13, 24)                       # small confident smirk
    for y in range(17, 29): im.putpixel((2, y), P['h'])
    return im


def style_soft():
    im = blank()
    both(im, ['.KKKK....',
              'KKKKKKk..'], 3, 10)
    both(im, ['.hhhhhhh.',                                         # soft shade under the brow
              'kkkkkkkk.',
              'kwdddpdw.',
              '.wodWodw.',
              '.woOOOow.',
              '..wOOOw..',
              '...hhh...'], 3, 12)
    grid(im, ['h', 'h', 'hn'], 16, 19)
    grid(im, ['.kkkkkk.', '..rRRr..'], 12, 24)
    for y in range(16, 29):
        im.putpixel((2, y), P['h']); im.putpixel((29, y), P['h'])
        if y > 21: im.putpixel((3, y), P['l'] if y < 24 else SKIN)
    for x in (5, 6, 25, 26): im.putpixel((x, 22), P['R'])         # faint rosy cheeks
    return im


def style_cel():
    im = blank()
    for y in range(13, 30):                                        # hard cel shadow on one side of the face
        for x in range(2, 9 if y < 22 else 11):
            im.putpixel((x, y), P['h'])
    both(im, ['.kkkkk...',
              'kKKKKKKk.'], 3, 10)
    both(im, ['kkkkkkkkk',
              'kkkkkkkkk',
              'kwdpddwwk',
              'kwoWoowwk',
              'kkOOOOkkk',
              '..kkkk...'], 3, 13)                                 # bold outlined eyes
    grid(im, ['k', 'k', 'kk'], 15, 19)
    grid(im, ['kkkkkkkk'], 12, 25)
    return im


def chibi(expr):
    im = blank()
    if expr == 'happy':
        both(im, ['..kkk..', '.k...k.', 'k.....k'], 4, 15)          # ^ ^ eyes
        grid(im, ['..kkkkkk..',
                  '.kggggggk.',
                  'kgggggggk.',
                  'kgggRRggk.',
                  '.kgRRRRk..',
                  '..kkkkk...'], 11, 22)                           # big open laugh
        both(im, ['bb'], 4, 20)
    elif expr == 'shocked':
        both(im, ['.kkkkk..', 'kwwwwwk.', 'kwwwwwk.', 'kwwwwwk.', '.kkkkk..'], 4, 13)   # blank white eyes
        grid(im, ['.k.k.k.k.k', 'k.k.k.k.k.', 'twtwtwtwt.', '.k.k.k.k.k'], 11, 23)     # wavy, gritted mouth
    else:  # angry
        both(im, ['kk.......', '.kkkk....', '....kkk..'], 3, 10)   # brows slammed down
        both(im, ['.kkkkk..', 'kwwwwwk.', '.kwwwwk.', '..kkkk..'], 4, 14)
        grid(im, ['..kkkkkk..', '.kggggggk.', 'kggggggggk', '.kggggggk.', '..kkkkkk..'], 11, 22)
    return im


row2 = [('E  Illustration 08', style_illustration()), ('F  Soft shaded', style_soft()), ('G  Bold cel', style_cel()),
        ('H  Chibi: happy', chibi('happy')), ('H  Chibi: shocked', chibi('shocked')), ('H  Chibi: angry', chibi('angry'))]
allrows = [styles, row2]
cols = max(len(r) for r in allrows)
sheet = Image.new('RGBA', (cols * (W + 20) + 20, len(allrows) * (W + 60)), (34, 34, 42, 255))
dr = ImageDraw.Draw(sheet)
for r, row in enumerate(allrows):
    for i, (name, im) in enumerate(row):
        crop = im.crop((0, 0, 32, 32)).resize((W, W), Image.NEAREST)
        x = 20 + i * (W + 20); y = r * (W + 60)
        sheet.paste(crop, (x, y + 46), crop)
        dr.text((x, y + 12), name, fill=(235, 235, 240), font=font)
sheet.save(OUT + 'mikiko_face_styles2.png')
print('sheet2 ok')
