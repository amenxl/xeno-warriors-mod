"""Every face of a character in the chosen style (Mix 1 / 1h-7): Illustration 08 brows, glossy orange eyes with a
lower lid line, small smile. Usage: restyle.py mikiko|rhalix [outdir]"""
import sys, os
from PIL import Image
CHAR = sys.argv[1] if len(sys.argv) > 1 else 'mikiko'
DST = sys.argv[2] if len(sys.argv) > 2 else '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'
SRC_BASE = f'/home/claude/xw/faces/old_{CHAR}/{CHAR}_face.png' if os.path.exists(f'/home/claude/xw/faces/old_{CHAR}/{CHAR}_face.png') \
    else f'/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/{CHAR}_face.png'
base = Image.open(SRC_BASE).convert('RGBA')
SKIN = base.getpixel((15, 26))


def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), 255)


IRIS = {'mikiko': ((232, 128, 26), (255, 182, 66), (176, 78, 14)),
        'rhalix': ((214, 36, 44), (255, 104, 96), (140, 14, 22))}[CHAR]
P = {'s': SKIN, 'h': shade(SKIN, 0.81), 'H': shade(SKIN, 0.66), 'k': (34, 18, 12, 255), 'K': (60, 34, 24, 255),
     'w': (244, 240, 236, 255), 'W': (255, 255, 255, 255), 'o': IRIS[0] + (255,), 'O': IRIS[1] + (255,),
     'd': IRIS[2] + (255,), 'p': (70, 26, 8, 255) if CHAR == 'mikiko' else (60, 8, 10, 255),
     'r': (128, 66, 50, 255), 'R': (168, 92, 74, 255), 'g': (90, 40, 28, 255), 't': (250, 248, 244, 255),
     'B': (150, 200, 240, 255), 'b': (110, 165, 220, 255), 'c': (90, 220, 230, 255), 'm': (220, 70, 200, 255),
     'G': (120, 160, 80, 255), 'Y': (255, 230, 120, 255)}


def blank(tint=None):
    im = base.copy()
    for y in range(6, 30):
        for x in range(2, 30):
            im.putpixel((x, y), SKIN)
    if tint:
        for y in range(6, 30):
            for x in range(0, 32):
                c = im.getpixel((x, y))
                im.putpixel((x, y), tuple(int(c[i] * (1 - tint[3]) + tint[i] * tint[3]) for i in range(3)) + (255,))
    return im


def put(im, rows, x0, y0, mirror=False):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == '.': continue
            x = x0 + (len(row) - 1 - i if mirror else i)
            if 0 <= x < 32 and 0 <= y0 + j < 32:
                im.putpixel((x, y0 + j), P[ch])


def pair(im, left, x, y, right=None):
    put(im, left, x, y)
    r = right if right is not None else left
    put(im, r, 32 - x - len(r[0]), y, mirror=True)


# ---------------- parts
BROWS = {
    'conf': (['KK.......', '.KKKKk...', '...kkkkk.'], 10),
    'angry': (['KKK......', '..KKKk...', '....kkkkk'], 10),
    'sad': (['.......kk', '....kkKK.', '.KKKK....'], 10),
    'raised': (['..KKKKk..', '.K.....k.'], 9),
    'flat': (['.KKKKKkk.'], 11),
    'relax': (['KKKKkk...', '...kkkkk.'], 10),
}
OPEN = ['..kkkkkk.', 'kkkkkkkkk', '.wddpddw.', '.wWWoodw.', '.wWoopow.', '..wOOOw..', '..kkkkk..']
EYES = {
    'open': OPEN,
    'closed': ['', '', '', 'kkkkkkkk.', '.kkkkkk..'],
    'happy': ['', '', '..kkkkk..', '.k.....k.', 'k.......k'],
    'half': ['', '', 'kkkkkkkkk', 'kkkkkkkkk', '.wddpddw.', '.wWoopow.', '..kkkkk..'],
    'wide': ['..kkkkk..', '.kwwwwwk.', 'kwwoopwwk', 'kwwOWowwk', 'kwwwwwwwk', '.kwwwwwk.', '..kkkkk..'],
    'fierce': ['hhh......', 'kkkhhh...', '.kkkkkkk.', '.wdWopdk.', '.wwoOoww.', '..kkkkk..', ''],   # A4 shadowed glare: every angry face
    'up': ['..kkkkkk.', 'kkkkkkkkk', '.wWpdodw.', '.wWoopow.', '.wwwwwww.', '..wwwww..', '..kkkkk..'],
    'sparkle': ['..kkkkkk.', 'kkkkkkkkk', '.wdWdddw.', '.wWWWodw.', '.wdWopow.', '..wOOOw..', '..kkkkk..'],
    'dizzy': ['', '.k.....k.', '..k...k..', '...k.k...', '..k...k..', '.k.....k.'],
    'sad': ['', '..kkkkk..', 'kkkkkkkkk', '.wddpddw.', '.wWoopow.', '..wOOOw..', '..kkkkk..'],
    'squeeze': ['', 'k........', '.kkk.....', '....kkkk.', '.kkk.....', 'k........'],
    'robot': ['', '.kkkkkk..', '.koooOk..', '.koooOk..', '.kkkkkk..'],
}
LOOK_L = ['..kkkkkk.', 'kkkkkkkkk', '.wpddwww.', '.wWodwww.', '.woowwww.', '..wOwww..', '..kkkkk..']   # iris toward viewer's left
LOOK_R = ['..kkkkkk.', 'kkkkkkkkk', '.wwwddpw.', '.wwwWodw.', '.wwwwoow.', '..wwwOw..', '..kkkkk..']
MOUTHS = {
    'smile': (['k......k', '.kkkkkk.'], 12, 24), 'small_smile': (['k.....k', '.kkkkk.'], 12, 24),
    'grin': (['kkkkkkkk', 'k.tttt.k', '.kkkkkk.'], 12, 24), 'big': (['kkkkkkkk', 'kggggggk', '.kgRRgk.', '..kkkk..'], 12, 24),
    'shout': (['..kkkk..', '.kggggk.', 'kggggggk', 'kgRRRRgk', '.kggggk.', '..kkkk..'], 12, 23),
    'o': (['.kk.', 'kggk', '.kk.'], 14, 24), 'frown': (['.kkkkkk.', 'k......k'], 12, 24), 'flat': (['.kkkkkk.'], 12, 25),
    'wavy': (['.k.k.k.k', 'k.k.k.k.'], 12, 24), 'tongue': (['k......k', '.kkkkkk.', '..kRRk..', '...RR...'], 12, 24),
    'smirk': (['......k', 'kkkkkk.'], 13, 24), 'grit': (['kkkkkkkk', 'kttttttk', 'kkkkkkkk'], 12, 24),
    'chew1': (['.kkkkkk.', '.kRRRRk.', '..kkkk..'], 12, 24), 'chew2': (['..kkkk..'], 12, 25),
    'yawn': (['..kkkk..', '.kggggk.', 'kggggggk', 'kggggggk', '.kggggk.', '..kkkk..'], 12, 23),
    'sing': (['..kkkk..', '.kggggk.', '.kgRRgk.', '..kkkk..'], 12, 23), 'whistle': (['.k.', 'k.k', '.k.'], 15, 24),
    'small': (['.kkkk.'], 13, 25),
}


def face(brow='conf', eyes='open', mouth='smile', right_eye=None, tint=None, extras=()):
    im = blank(tint)
    b, by = BROWS[brow]
    pair(im, b, 3, by)
    if eyes == 'look_l':
        put(im, LOOK_L, 3, 14); put(im, LOOK_L, 20, 14)
    elif eyes == 'look_r':
        put(im, LOOK_R, 3, 14); put(im, LOOK_R, 20, 14)
    else:
        e = [r for r in EYES[eyes]]
        e = [r if r else '.........' for r in e]
        re = None
        if right_eye:
            re = [r if r else '.........' for r in EYES[right_eye]]
        pair(im, e, 3, 14, re)
    put(im, ['h.', 'h.', 'hH'], 15, 20)                       # nose
    m, mx, my = MOUTHS[mouth]
    put(im, m, mx, my)
    for y in range(17, 29): im.putpixel((2, y), P['h'])        # cheek shade
    for ex in extras:
        if ex == 'tears':
            for (x, y) in ((5, 21), (5, 22), (6, 23), (26, 21), (26, 22), (25, 23)): im.putpixel((x, y), P['B'])
        elif ex == 'sweat':
            put(im, ['.B.', 'BBB', 'BbB', '.B.'], 25, 7)
        elif ex == 'sweat2':
            put(im, ['.B.', 'BBB', 'BbB', '.B.'], 25, 9)
        elif ex == 'blush':
            for x in (5, 6, 7, 24, 25, 26): im.putpixel((x, 22), P['R'])
        elif ex == 'drool':
            put(im, ['B', 'b'], 18, 26)
        elif ex == 'drool2':
            put(im, ['B', 'B', 'B', 'b'], 18, 26)
        elif ex == 'cold':        # bluish, chattering cheeks
            for x in (4, 5, 6, 7, 24, 25, 26, 27): im.putpixel((x, 22), P['b'])
            for x in (5, 6, 25, 26): im.putpixel((x, 23), P['B'])
        elif ex == 'queasy':      # green under the eyes
            for x in (4, 5, 6, 7, 24, 25, 26, 27): im.putpixel((x, 22), P['G'])
            for x in (5, 6, 25, 26): im.putpixel((x, 23), P['G'])
        elif ex == 'glitch':
            for (x, y) in ((4, 16), (5, 16), (22, 17), (23, 17), (12, 24), (19, 25)): im.putpixel((x, y), P['c'])
            for (x, y) in ((7, 18), (8, 18), (25, 15), (26, 15), (15, 25)): im.putpixel((x, y), P['m'])
        elif ex == 'zz':
            put(im, ['kkk', '.k.', 'kkk'], 25, 6)
        elif ex == 'note':
            put(im, ['.kk', '.k.', 'kk.'], 24, 22)
        elif ex == 'star':
            put(im, ['.Y.', 'YYY', '.Y.'], 25, 7)
    return im


SPEC = {
    None: dict(), 'annoyed': dict(brow='flat', eyes='half', mouth='flat'), 'blink': dict(eyes='closed'),
    'bored': dict(brow='flat', eyes='half', mouth='small'), 'calm': dict(brow='relax', eyes='closed', mouth='small_smile'),
    'concentrate': dict(brow='angry', eyes='fierce', mouth='grit'), 'cool': dict(eyes='half', mouth='smirk'),
    'crying': dict(brow='sad', eyes='sad', mouth='wavy', extras=('tears',)), 'dizzy': dict(brow='raised', eyes='dizzy', mouth='wavy'),
    'eat': dict(eyes='happy', mouth='chew1'), 'eat2': dict(eyes='happy', mouth='chew2'),
    'excited': dict(brow='raised', eyes='sparkle', mouth='big'), 'exhausted': dict(brow='sad', eyes='half', mouth='o', extras=('sweat',)),
    'exhausted2': dict(brow='sad', eyes='half', mouth='small', extras=('sweat2',)), 'fierce': dict(brow='angry', eyes='fierce', mouth='grit'),
    'focused': dict(brow='angry', eyes='fierce', mouth='flat'),
    'freezing': dict(brow='sad', eyes='half', mouth='wavy', extras=('cold',)),
    'glance_l': dict(eyes='look_l'), 'glance_r': dict(eyes='look_r'), 'glitch': dict(eyes='open', mouth='flat', extras=('glitch',)),
    'grin': dict(mouth='grin'), 'happy': dict(brow='relax', eyes='happy', mouth='big'), 'headbang': dict(brow='angry', eyes='closed', mouth='grin'),
    'hum': dict(brow='relax', eyes='closed', mouth='small_smile', extras=('note',)), 'hurt': dict(brow='angry', eyes='squeeze', mouth='grit'),
    'look_up': dict(brow='raised', eyes='up', mouth='small_smile'), 'melancholy': dict(brow='sad', eyes='half', mouth='frown'),
    'panic': dict(brow='sad', eyes='wide', mouth='wavy', extras=('sweat',)), 'rage': dict(brow='angry', eyes='fierce', mouth='shout'),
    'robot': dict(brow='flat', eyes='robot', mouth='flat'), 'robot_l': dict(brow='flat', eyes='robot', mouth='flat'),
    'robot_r': dict(brow='flat', eyes='robot', mouth='flat'), 'sad': dict(brow='sad', eyes='sad', mouth='frown'),
    'scared': dict(brow='sad', eyes='wide', mouth='wavy'), 'shout': dict(brow='angry', eyes='fierce', mouth='shout'),
    'sick': dict(brow='sad', eyes='half', mouth='wavy', extras=('queasy',)), 'sing': dict(brow='relax', eyes='closed', mouth='sing', extras=('note',)),
    'sleep': dict(brow='relax', eyes='closed', mouth='small'), 'sleepy': dict(brow='flat', eyes='half', mouth='o'),
    'smug': dict(eyes='half', mouth='smirk'), 'snore': dict(brow='relax', eyes='closed', mouth='o', extras=('zz',)),
    'sparkle': dict(brow='raised', eyes='sparkle', mouth='big', extras=('star',)), 'strained': dict(brow='angry', eyes='squeeze', mouth='grit', extras=('sweat',)),
    'surprised': dict(brow='raised', eyes='wide', mouth='o'), 'thinking': dict(eyes='up', mouth='flat'),
    'tongue': dict(brow='relax', eyes='happy', mouth='tongue'), 'whistle': dict(brow='relax', eyes='closed', mouth='whistle', extras=('note',)),
    'wink': dict(eyes='open', right_eye='happy', mouth='grin'), 'yawn': dict(brow='sad', eyes='closed', mouth='yawn'),
    'hungry': dict(brow='sad', eyes='half', mouth='o', extras=('sweat', 'drool')),
    'hungry2': dict(brow='sad', eyes='half', mouth='o', extras=('sweat', 'drool2')),
    'annoyed_rain_base': dict(brow='flat', eyes='half', mouth='flat'),
}


def build_robot_shift(im, dx):
    return im


if __name__ == '__main__':
    os.makedirs(DST, exist_ok=True)
    made = []
    for expr, spec in SPEC.items():
        if expr == 'annoyed_rain_base': continue
        im = face(**spec)
        if expr in ('robot_l', 'robot_r'):            # robot eyes glance sideways
            shifted = blank()
            shifted.paste(im.crop((0, 0, 32, 32)), (0, 0))
            for y in range(14, 20):
                row = [im.getpixel((x, y)) for x in range(32)]
                for x in range(3, 29):
                    src = x + (1 if expr == 'robot_l' else -1)
                    shifted.putpixel((x, y), row[src])
            im = shifted
        name = f'{CHAR}_face.png' if expr is None else f'{CHAR}_face_{expr}.png'
        im.save(DST + name)
        made.append(name)
    print(len(made), 'faces')
