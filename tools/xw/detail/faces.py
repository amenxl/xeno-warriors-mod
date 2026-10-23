"""High-res (32x32) faces painted on a separate face layer. Image is 64x64 (front face = top-left 32x32)."""
from PIL import Image

def hx(h):
    h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)

class Face:
    def __init__(self, skin, skin_d, skin_h):
        self.img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        self.S, self.SD, self.SH = skin, skin_d, skin_h
        for y in range(32):
            for x in range(32):
                c = skin
                if x <= 1 or x >= 30 or y >= 30:
                    c = skin_d
                elif (x == 2 or x == 29) and y > 20:
                    c = skin_d
                self.px(x, y, c)

    def px(self, x, y, c):
        if 0 <= x < 32 and 0 <= y < 32:
            self.img.putpixel((x, y), hx(c))

    def row(self, y, x0, x1, c):
        for x in range(x0, x1 + 1):
            self.px(x, y, c)

    def grid(self, x0, y0, rows, pal, mirror=False):
        """Paint a small char grid. '.' = leave as is. With mirror, also paint it flipped on the other side."""
        for dy, r in enumerate(rows):
            for dx, ch in enumerate(r):
                if ch != '.':
                    self.px(x0 + dx, y0 + dy, pal[ch])
                    if mirror:
                        self.px(31 - (x0 + dx), y0 + dy, pal[ch])

    def save(self, path):
        self.img.save(path)


# ------------------------------------------------------------ Mikiko: big amber anime eyes, calm smile
f = Face('#966240', '#84553a', '#a46c48')
pal = {'O': '#120b0a', 'W': '#f2f0ee', 'w': '#cfcbca', 'D': '#6a2c0c', 'I': '#e8801a', 'Y': '#ffc23c',
       'P': '#2a1206', 'H': '#ffffff', 'l': '#6e4228', 'B': '#22140e'}
# thin brows
f.grid(4, 10, ['..BBBBBB.',
               '.B......B'], pal, mirror=True)
# eyes: thick lash line with a flick at the outer corner, iris dark at the top fading to yellow
f.grid(4, 13, ['OO.......',
               '.OOOOOOOO',
               'OOOOOOOOO',
               '.WwDDDDw.',
               '.WWDPPHW.',
               '.WWIPPIW.',
               '.WWIIIIW.',
               '..wYYYYw.',
               '...llll..'], pal, mirror=True)
# calm, closed smile
f.row(25, 11, 20, '#3a1a10'); f.px(10, 24, '#3a1a10'); f.px(21, 24, '#3a1a10')
f.save('/home/claude/xw/tex/mikiko_face.png')

# Mikiko asleep: relaxed brows, closed eyes (curved lash lines), soft blush, mouth slightly open
f = Face('#966240', '#84553a', '#a46c48')
pal = {'O': '#120b0a', 'B': '#22140e', 'b': '#3a2216', 'K': '#b0604a'}
f.grid(4, 11, ['..BBBBBB.',
               '.B......B'], pal, mirror=True)
f.grid(4, 17, ['.........',
               'O.......O',
               '.OOOOOOO.',
               '...b.b...'], pal, mirror=True)
f.grid(4, 21, ['.KK.KK...'], pal, mirror=True)
f.row(25, 14, 17, '#3a1a10'); f.px(14, 26, '#5a2a1a'); f.px(15, 26, '#5a2a1a'); f.px(16, 26, '#5a2a1a'); f.px(17, 26, '#5a2a1a')
f.save('/home/claude/xw/tex/mikiko_face_sleep.png')

# Mikiko snoring: same closed eyes, mouth hanging open, and a snot bubble at his nose
f = Face('#966240', '#84553a', '#a46c48')
f.grid(4, 11, ['..BBBBBB.',
               '.B......B'], pal, mirror=True)
f.grid(4, 17, ['.........',
               'O.......O',
               '.OOOOOOO.',
               '...b.b...'], pal, mirror=True)
f.grid(4, 21, ['.KK.KK...'], pal, mirror=True)
for y, (x0, x1) in zip(range(24, 28), ((14, 17), (13, 18), (13, 18), (14, 17))):
    f.row(y, x0, x1, '#3a1a10')
f.row(25, 14, 17, '#7a2a24'); f.row(26, 14, 17, '#7a2a24')       # inside of the mouth
bub = {'L': '#cfe8ff', 'W': '#ffffff', 'E': '#9cc8f0'}
f.grid(17, 19, ['.EEE.',
                'ELLWE',
                'ELLLE',
                'ELLLE',
                '.EEE.'], bub)
f.save('/home/claude/xw/tex/mikiko_face_snore.png')

# ------------------------------------------------------------ Rhalix: half-lidded red eyes, smirk with teeth
f = Face('#5a3621', '#4c2d1c', '#684029')
pal = {'O': '#0e0809', 'W': '#efeceb', 'w': '#c9c4c4', 'D': '#7a0a0e', 'I': '#d8141c', 'Y': '#ff4a46',
       'P': '#2a0604', 'H': '#ffffff', 'B': '#140c10'}
# thin, cocky brows
f.grid(4, 10, ['...BBBB..',
               'BBB....B.'], pal, mirror=True)
# heavy-lidded eyes
f.grid(4, 13, ['OOO......',
               '.OOOOOOOO',
               '.OOOOOOOO',
               '.WwDDDHW.',
               '..WIPPIW.',
               '...YYYY..'], pal, mirror=True)
# smirk pulled up on the twig side, showing a few teeth
dark = '#1e0c0a'
f.row(24, 11, 18, dark)
f.row(23, 19, 22, dark)
f.px(19, 24, '#f0ece4'); f.px(20, 24, '#f0ece4'); f.px(21, 24, '#f0ece4'); f.px(22, 24, dark)
f.row(25, 19, 21, dark)
f.save('/home/claude/xw/tex/rhalix_face.png')

# preview: both faces enlarged, with the parts hidden by hair/goggles dimmed
prev = Image.new('RGBA', (32 * 10 * 2 + 20, 32 * 10), (20, 20, 26, 255))
for i, (n, cover) in enumerate((('mikiko', 12), ('rhalix', 8))):
    im = Image.open(f'/home/claude/xw/tex/{n}_face.png').crop((0, 0, 32, 32)).resize((320, 320), Image.NEAREST)
    shade = Image.new('RGBA', (320, cover * 10), (0, 0, 0, 170))
    im.alpha_composite(shade, (0, 0))
    prev.alpha_composite(im, (i * 340, 0))
prev.save('/home/claude/xw/detail/faces_preview.png')
print('faces ok')


EYE_T = {
    'mikiko': ['OO.......', '.OOOOOOOO', 'OOOOOOOOO', '.WwDDDDw.', '.WWDPPHW.', '.WWIPPIW.', '.WWIIIIW.', '..wYYYYw.'],
    'rhalix': ['OOO......', '.OOOOOOOO', '.WwDDDHW.', '..WIPPIW.', '...YYYY..'],
}

# ============================================================ expressions (blink / fierce / shout / hurt / sick)
def expressions(name, skin, eye_pal, base_eyes, eye_row, mouth_default):
    """Paints the expression set for one character, reusing their eye colors."""
    S, SD, SH = skin
    pal = dict(eye_pal)
    pal.update({'B': pal.get('B', '#22140e'), 'm': '#3a1a10', 'T': '#f4efe6', 'R': '#7a2a24', 'G': '#9cc8f0', 'g': '#d8ecff'})

    def new():
        f = Face(S, SD, SH)
        return f

    def brows(f, kind):
        if kind == 'angry':      # slanted down toward the middle
            f.grid(4, 9, ['BB.......', '.BBBB....', '....BBBBB'], pal, mirror=True)
        elif kind == 'worried':  # inner ends raised
            f.grid(4, 9, ['......BBB', '...BBB...', 'BBB......'], pal, mirror=True)
        elif kind == 'raised':   # high arches
            f.grid(4, 8, ['..BBBBB..', '.B.....B.'], pal, mirror=True)
        elif kind == 'flat':     # flat, unimpressed
            f.grid(4, 11, ['.BBBBBBB.'], pal, mirror=True)
        else:
            f.grid(4, 10, ['..BBBBBB.', '.B......B'], pal, mirror=True)

    # blink: normal brows and mouth, eyes shut
    f = new(); brows(f, 'normal')
    f.grid(4, eye_row + 3, ['.........', 'O.......O', '.OOOOOOO.'], pal, mirror=True)
    mouth_default(f)
    f.save(f'/home/claude/xw/tex/{name}_face_blink.png')

    # fierce (attacking): angry brows, narrowed eyes, gritted teeth
    f = new(); brows(f, 'angry')
    f.grid(4, eye_row + 1, ['.OOOOOOOO', 'OOOOOOOOO', '.WDDPPDW.', '.WIPPPIW.', '..wYYYw..'], pal, mirror=True)
    f.row(24, 11, 20, '#3a1a10'); f.row(25, 12, 19, '#f4efe6'); f.px(11, 25, '#3a1a10'); f.px(20, 25, '#3a1a10')
    f.px(14, 25, '#b9b2a6'); f.px(17, 25, '#b9b2a6'); f.row(26, 11, 20, '#3a1a10')
    f.save(f'/home/claude/xw/tex/{name}_face_fierce.png')

    # shout (using a skill): angry brows, wide focused eyes, open shouting mouth
    f = new(); brows(f, 'angry')
    base_eyes(f)
    for y, (x0, x1) in zip(range(23, 28), ((13, 18), (12, 19), (12, 19), (12, 19), (13, 18))):
        f.row(y, x0, x1, '#3a1a10')
    f.row(24, 13, 18, '#f4efe6'); f.row(25, 13, 18, '#7a2a24'); f.row(26, 14, 17, '#b04040')
    f.save(f'/home/claude/xw/tex/{name}_face_shout.png')

    # hurt (getting hit): worried brows, eyes squeezed shut > <, grimace
    f = new(); brows(f, 'worried')
    f.grid(4, eye_row + 2, ['.OO......', '...OOO...', '......OO.', '...OOO...', '.OO......'], pal, mirror=True)
    f.row(24, 12, 19, '#3a1a10'); f.row(25, 13, 18, '#f4efe6'); f.px(12, 25, '#3a1a10'); f.px(19, 25, '#3a1a10')
    f.row(26, 12, 19, '#3a1a10')
    f.save(f'/home/claude/xw/tex/{name}_face_hurt.png')

    # sick (poisoned / weakened): greenish, droopy half-closed eyes with dark rings, wavy frown, sweat drop
    f = new()
    for y in range(32):
        for x in range(32):
            r, g, b, a = f.img.getpixel((x, y))
            f.img.putpixel((x, y), (int(r * 0.86), min(255, int(g * 1.05 + 10)), int(b * 0.86), a))
    brows(f, 'worried')
    f.grid(4, eye_row + 2, ['.........', 'OOOOOOOOO', '.WWDDPWW.', '..wIIIw..', '..ddddd..'],
           dict(pal, d='#5a3a2a'), mirror=True)
    for i, x in enumerate(range(11, 21)):
        f.px(x, 25 + (i % 2), '#3a1a10')
    f.grid(26, 8, ['.g.', 'gGg', 'GGG', '.G.'], pal)
    f.save(f'/home/claude/xw/tex/{name}_face_sick.png')

    # ---------------- extra moods ----------------
    sweat = {'g': '#d8ecff', 'G': '#9cc8f0'}
    def drop(f, x, y):
        f.grid(x, y, ['.g.', 'gGg', 'GGG', '.G.'], sweat)
    def cheeks(f, puffed=False):
        k = {'K': '#c46a58', 'k': SH}
        f.grid(4, 21, (['.kkkk....', 'kKKKKk...', '.kkkk....'] if puffed else ['.KK.KK...']), k, mirror=True)
    def happy_eyes(f):
        f.grid(4, eye_row + 2, ['...OOO...', '.OO...OO.', 'O.......O'], pal, mirror=True)
    def bored_eyes(f):
        f.grid(4, eye_row + 2, ['.........', 'OOOOOOOOO', 'OOOOOOOOO', '.WwDDPPw.', '..wIIIw..'], pal, mirror=True)
    def wide_eyes(f, tiny_pupil=False):
        rows = ['.OOOOOOO.', 'OWWWWWWWO', 'OWWWWWWWO', 'OWWWPWWWO', 'OWWWWWWWO', '.OWWWWWO.', '..OOOOO..'] if tiny_pupil else \
               ['.OOOOOOO.', 'OWWIIIWWO', 'OWIPPHIWO', 'OWIPPIIWO', 'OWWIYIWWO', '.OWWWWWO.', '..OOOOO..']
        f.grid(4, eye_row, rows, pal, mirror=True)
    def mouth(f, kind):
        D, Wt, Pk, Rd = '#3a1a10', '#f4efe6', '#d86a6a', '#7a2a24'
        if kind == 'smile':
            f.row(24, 12, 19, D); f.px(11, 25, D); f.row(25, 12, 19, Wt); f.px(20, 25, D)
            f.px(12, 26, D); f.row(26, 13, 18, Pk); f.px(19, 26, D); f.row(27, 13, 18, D)
        elif kind == 'cat':
            for x, y in ((12, 25), (13, 26), (14, 26), (15, 25), (16, 25), (17, 26), (18, 26), (19, 25)):
                f.px(x, y, D)
        elif kind == 'o':
            f.row(24, 15, 16, D); f.px(14, 25, D); f.row(25, 15, 16, Rd); f.px(17, 25, D); f.row(26, 15, 16, D)
        elif kind == 'yawn':
            for y, (x0, x1) in zip(range(22, 29), ((14, 17), (13, 18), (12, 19), (12, 19), (12, 19), (13, 18), (14, 17))):
                f.row(y, x0, x1, D)
            for y, (x0, x1) in zip(range(23, 28), ((14, 17), (13, 18), (13, 18), (13, 18), (14, 17))):
                f.row(y, x0, x1, Rd)
            f.row(26, 14, 17, Pk)
        elif kind == 'wobble':
            f.row(24, 12, 19, D); f.px(11, 25, D); f.row(25, 12, 19, Rd); f.px(20, 25, D)
            for i, x in enumerate(range(12, 20)):
                f.px(x, 26 + (i % 2), D)
            f.row(25, 13, 18, Wt)
        elif kind == 'flat':
            f.row(25, 14, 19, D)
        elif kind == 'line':
            f.row(25, 13, 18, D); f.px(12, 26, D); f.px(19, 26, D)
        elif kind == 'smirk':
            f.row(25, 12, 18, D); f.px(19, 24, D); f.px(20, 23, D)
        elif kind == 'grit':
            f.row(24, 11, 20, D); f.row(25, 12, 19, Wt); f.px(11, 25, D); f.px(20, 25, D)
            f.px(14, 25, '#b9b2a6'); f.px(17, 25, '#b9b2a6'); f.row(26, 11, 20, D)

    def make(tag, brow, eyes, mth, extra=None):
        f = new(); brows(f, brow); eyes(f); mouth(f, mth)
        if extra: extra(f)
        f.save(f'/home/claude/xw/tex/{name}_face_{tag}.png')

    make('happy', 'normal', happy_eyes, 'smile', lambda f: cheeks(f))
    make('eat', 'normal', happy_eyes, 'cat', lambda f: cheeks(f, True))
    make('eat2', 'normal', happy_eyes, 'o', lambda f: cheeks(f, True))
    make('scared', 'worried', lambda f: wide_eyes(f, True), 'wobble', lambda f: (drop(f, 26, 8), drop(f, 3, 10)))
    make('surprised', 'raised', lambda f: wide_eyes(f), 'o')
    make('bored', 'flat', bored_eyes, 'flat')
    make('yawn', 'flat', lambda f: f.grid(4, eye_row + 3, ['O.......O', '.OOOOOOO.'], pal, mirror=True), 'yawn',
         lambda f: f.grid(3, eye_row + 5, ['G', 'g'], sweat))
    make('strained', 'worried', lambda f: f.grid(4, eye_row + 1, ['.OOOOOOOO', 'OOOOOOOOO', '.WDDPPDW.', '..wIIIw..'], pal, mirror=True),
         'grit', lambda f: drop(f, 26, 9))
    make('focused', 'angry', lambda f: f.grid(4, eye_row + 1, ['.OOOOOOOO', 'OOOOOOOOO', '.WwDDPPw.', '.WWIPPIW.', '..wYYYw..'], pal, mirror=True), 'line')
    make('smug', 'flat', bored_eyes, 'smirk')

    # ---------------- even more moods ----------------
    tears = {'t': '#7fc0f0', 'T': '#cfe8ff'}
    def panic_extra(f):
        for y in range(eye_row + 6, 30):
            f.px(6, y, '#7fc0f0'); f.px(25, y, '#7fc0f0')
            if y % 3 == 0: f.px(7, y, '#cfe8ff'); f.px(24, y, '#cfe8ff')
        drop(f, 27, 7)
    make('panic', 'worried', lambda f: wide_eyes(f, True), 'yawn', panic_extra)
    spiral = ['.OOOOOO..', 'O......O.', 'O.OOOO.O.', 'O.O..O.O.', 'O.O.OO.O.', 'O.O....O.', 'O.OOOOOO.', 'O........']
    make('dizzy', 'worried', lambda f: f.grid(4, eye_row, spiral, pal, mirror=True), 'wobble')
    def grin_mouth(f):
        D, Wt = '#3a1a10', '#f4efe6'
        f.px(10, 23, D); f.px(21, 23, D)
        f.row(24, 10, 21, D); f.row(25, 11, 20, Wt); f.px(10, 25, D); f.px(21, 25, D)
        for x in (13, 15, 17, 19): f.px(x, 25, '#c9c2b6')
        f.row(26, 11, 20, D)
    def grin(f):
        brows(f, 'angry')
        f.grid(4, eye_row + 1, ['.OOOOOOOO', 'OOOOOOOOO', '.WwDDPPw.', '.WWIPPIW.', '..wYYYw..'], pal, mirror=True)
        grin_mouth(f)
    f = new(); grin(f); f.save(f'/home/claude/xw/tex/{name}_face_grin.png')
    star = {'s': '#fff6b0', 'S': '#ffffff'}
    def sparkle_eyes(f):
        wide_eyes(f)
        f.grid(5, eye_row + 1, ['..S..', '.sSs.', 'SSSSS', '.sSs.', '..S..'], star, mirror=True)
    make('sparkle', 'raised', sparkle_eyes, 'smile', lambda f: cheeks(f))
    make('sleepy', 'flat', lambda f: f.grid(4, eye_row + 3, ['OOOOOOOOO', 'OOOOOOOOO', '.wwDDPww.'], pal, mirror=True), 'o')
    def freezing_extra(f):
        f.grid(4, 21, ['.CC.CC...'], {'C': '#7fa8d8'}, mirror=True)
        f.row(24, 11, 20, '#3a1a10'); f.row(26, 11, 20, '#3a1a10')
        for i, x in enumerate(range(11, 21)):
            f.px(x, 25, '#f4efe6' if i % 2 == 0 else '#3a1a10')
    make('freezing', 'worried', lambda f: f.grid(4, eye_row + 1, ['.OOOOOOOO', 'OOOOOOOOO', '.WDDPPDW.', '..wIIIw..'], pal, mirror=True),
         'flat', freezing_extra)
    def annoyed_eyes(f):
        f.grid(4, eye_row + 2, ['.........', 'OOOOOOOOO', 'OOOOOOOOO', '.WwwDPPW.', '..wwIIW..'], pal, mirror=True)
    make('annoyed', 'flat', annoyed_eyes, 'line')

    # robot (dancing the robot to "cat"): square screen-like eyes with a dot pupil, grid mouth, dead serious
    def robot_eyes(f):
        f.grid(4, eye_row, ['OOOOOOOO.', 'OWWWWWWO.', 'OWWPPWWO.', 'OWWPPWWO.', 'OWWWWWWO.', 'OOOOOOOO.'], pal, mirror=True)
    def robot_extra(f):
        D, Wt = '#3a1a10', '#f4efe6'
        f.row(24, 12, 19, D); f.row(26, 12, 19, D); f.px(12, 25, D); f.px(19, 25, D)
        for x in range(13, 19):
            f.px(x, 25, Wt if x % 2 else D)
        f.grid(2, 9, ['.g', 'gG'], {'g': '#9cf0ff', 'G': '#d8fcff'})   # a little "beep" glint
    make('robot', 'flat', robot_eyes, 'flat', robot_extra)

    # ---------------- neutral idle faces: glancing around, looking up, thinking, calm, humming
    T = EYE_T[name]
    IRIS = 'DPHIY'
    def shift_row(row, d):
        chars = list(row)
        iris = [(k, c) for k, c in enumerate(chars) if c in IRIS]
        if not iris or d == 0: return row
        for k, c in iris: chars[k] = 'W'
        for k, c in iris:
            t = k + d
            if 0 <= t < len(chars) and chars[t] in 'Ww' + IRIS: chars[t] = c
        return ''.join(chars)
    def eyes_shifted(f, d=0, up=0):
        rows = list(T)
        if up:
            first = next(k for k, r in enumerate(rows) if any(c in IRIS for c in r))
            iris_rows = rows[first:]
            moved = iris_rows[up:] + [r.replace('D', 'W').replace('P', 'W').replace('I', 'W').replace('Y', 'w').replace('H', 'W') for r in iris_rows[:up]]
            rows = rows[:first] + moved
        left = [shift_row(r, d) for r in rows]
        right = [shift_row(r, -d)[::-1] for r in rows]
        for dy, (lr, rr) in enumerate(zip(left, right)):
            for dx, ch in enumerate(lr):
                if ch != '.': f.px(4 + dx, eye_row + dy, pal[ch])
            for dx, ch in enumerate(rr):
                if ch != '.': f.px(31 - 4 - len(rr) + 1 + dx, eye_row + dy, pal[ch])
    def calm_eyes(f):
        rows = list(T)
        rows = rows[:1] + [r.replace('W', 'O').replace('w', 'O').replace('D', 'O') if k == 1 else r for k, r in enumerate(rows[1:], 1)]
        lids = ['.........'] + ['OOOOOOOOO'] + rows[2:]
        f.grid(4, eye_row, lids, pal, mirror=True)
    for tag, d, up, brow, mth in (('glance_l', -2, 0, 'normal', None), ('glance_r', 2, 0, 'normal', None),
                                  ('look_up', 0, 1, 'raised', None)):
        f = new(); brows(f, brow); eyes_shifted(f, d, up)
        mouth_default(f)
        f.save(f'/home/claude/xw/tex/{name}_face_{tag}.png')
    # thinking: one brow up, glancing to the side, mouth pulled to one side
    f = new()
    f.grid(4, 8, ['..BBBBB..', '.B.....B.'], pal)                      # raised (his right)
    f.grid(19, 10, ['.BBBBBB..', 'B......B.'], pal)                    # normal (left)
    eyes_shifted(f, 2, 1)
    mouth(f, 'smirk')
    f.save(f'/home/claude/xw/tex/{name}_face_thinking.png')
    # calm: relaxed half-closed eyes, small content smile
    f = new(); brows(f, 'normal'); calm_eyes(f)
    f.row(25, 13, 18, '#3a1a10'); f.px(12, 24, '#3a1a10'); f.px(19, 24, '#3a1a10')
    f.save(f'/home/claude/xw/tex/{name}_face_calm.png')
    # humming: eyes closed contentedly, little "o"
    make('hum', 'normal', happy_eyes, 'o')
    # flying moods (Mikiko once he's got it): excited open grin, cool smirk
    def excited_mouth(f):
        D, Wt, Pk = '#3a1a10', '#f4efe6', '#d86a6a'
        f.row(23, 12, 19, D); f.px(11, 24, D); f.px(20, 24, D)
        f.row(24, 12, 19, Wt); f.row(25, 12, 19, Pk); f.px(11, 25, D); f.px(20, 25, D); f.row(26, 13, 18, D)
    f = new(); brows(f, 'raised'); wide_eyes(f); excited_mouth(f); cheeks(f)
    f.save(f'/home/claude/xw/tex/{name}_face_excited.png')
    f = new(); brows(f, 'flat'); calm_eyes(f); mouth(f, 'smirk')
    f.save(f'/home/claude/xw/tex/{name}_face_cool.png')

    # exhausted (badly hurt, worn out): worried brows, heavy droopy half-closed eyes with tired rings,
    # flushed cheeks, sweat running down, small open panting mouth (two frames: breathing in / out)
    def tired_eyes(f):
        rows = list(T)
        first = next(k for k, r in enumerate(rows) if any(c in IRIS for c in r))
        lids = ['.........'] * first
        lids[-1] = 'OOOOOOOOO'
        f.grid(4, eye_row, lids + rows[first + 1:], pal, mirror=True)
        f.grid(4, eye_row + len(rows) - 1, ['..ddddd..'], {'d': '#6a4a3a' if name == 'mikiko' else '#3a2418'}, mirror=True)
    for frame in (0, 1):
        f = new(); brows(f, 'worried'); tired_eyes(f)
        f.grid(4, 21, ['.KK.KK...'], {'K': '#c46a58'}, mirror=True)        # flushed
        D, Rd = '#3a1a10', '#7a2a24'
        if frame == 0:
            f.row(24, 14, 17, D); f.row(25, 13, 18, Rd); f.px(13, 25, D); f.px(18, 25, D); f.row(26, 14, 17, D)
        else:
            f.row(25, 14, 17, D); f.row(26, 14, 17, Rd); f.row(27, 15, 16, D)
        drop(f, 26, 9 + frame * 2)
        drop(f, 3, 15 + frame)
        f.save(f'/home/claude/xw/tex/{name}_face_exhausted{"" if frame == 0 else "2"}.png')

    # sad: brows up in the middle, eyes lowered and half-closed, a little frown, a tear on the cheek
    f = new(); brows(f, 'worried')
    rows = list(T)
    first = next(k for k, r in enumerate(rows) if any(c in IRIS for c in r))
    lowered = ['.........'] * first
    lowered[-1] = 'OOOOOOOOO'
    f.grid(4, eye_row, lowered + rows[first + 1:], pal, mirror=True)
    D = '#3a1a10'
    f.row(25, 13, 18, D); f.px(12, 26, D); f.px(19, 26, D)
    f.grid(7, eye_row + len(rows), ['t', 't', 'T'], {'t': '#9fd0f5', 'T': '#cfe8ff'})
    f.save(f'/home/claude/xw/tex/{name}_face_sad.png')

    # ---------------- dance faces
    def closed_happy(f, side):     # one ^ eye (side: 0 = his right / image left, 1 = other)
        x0 = 4 if side == 0 else 19
        f.grid(x0, eye_row + 2, ['...OOO...', '.OO...OO.', 'O.......O'], pal)
    def open_eye(f, side):
        x0 = 4 if side == 0 else 19
        rows = list(T)
        if side == 1: rows = [r[::-1] for r in rows]
        f.grid(x0, eye_row, rows, pal)
    D, Wt, Pk, Rd = '#3a1a10', '#f4efe6', '#d86a6a', '#7a2a24'
    # wink: one eye shut, big grin
    f = new(); brows(f, 'normal'); open_eye(f, 0); closed_happy(f, 1); grin_mouth(f)
    f.save(f'/home/claude/xw/tex/{name}_face_wink.png')
    # tongue out, playful
    f = new(); brows(f, 'raised'); happy_eyes(f)
    f.row(24, 12, 19, D); f.px(11, 23, D); f.px(20, 23, D); f.row(25, 14, 17, Pk); f.row(26, 14, 17, Pk); f.px(15, 27, Pk); f.px(16, 27, Pk)
    cheeks(f); f.save(f'/home/claude/xw/tex/{name}_face_tongue.png')
    # whistling: pursed lips, glancing aside, a little note
    f = new(); brows(f, 'raised'); eyes_shifted(f, 2, 0)
    f.px(17, 25, D); f.px(18, 25, D); f.px(17, 26, D); f.px(18, 26, D); f.px(16, 25, D)
    f.grid(23, 22, ['.NN', '.N.', 'NN.'], {'N': '#ffe070'}); f.save(f'/home/claude/xw/tex/{name}_face_whistle.png')
    # singing: eyes closed, mouth open
    f = new(); brows(f, 'raised'); happy_eyes(f)
    for y, (x0, x1) in zip(range(23, 28), ((14, 17), (13, 18), (13, 18), (13, 18), (14, 17))): f.row(y, x0, x1, D)
    for y, (x0, x1) in zip(range(24, 27), ((14, 17), (14, 17), (15, 16))): f.row(y, x0, x1, Rd)
    f.save(f'/home/claude/xw/tex/{name}_face_sing.png')
    # headbang: eyes squeezed > <, shouting, sweat flying
    f = new(); brows(f, 'angry')
    f.grid(4, eye_row + 2, ['.OO......', '...OOO...', '......OO.', '...OOO...', '.OO......'], pal, mirror=True)
    for y, (x0, x1) in zip(range(23, 28), ((13, 18), (12, 19), (12, 19), (12, 19), (13, 18))): f.row(y, x0, x1, D)
    f.row(24, 13, 18, Wt); f.row(25, 13, 18, Rd); f.row(26, 14, 17, '#b04040')
    drop(f, 27, 6); drop(f, 2, 8); f.save(f'/home/claude/xw/tex/{name}_face_headbang.png')
    # concentrating: biting his lip, eyes narrowed
    f = new(); brows(f, 'angry')
    f.grid(4, eye_row + 1, ['.OOOOOOOO', 'OOOOOOOOO', '.WwDDPPw.', '..wIIIw..'], pal, mirror=True)
    f.row(25, 13, 18, D); f.px(15, 26, Wt); f.px(16, 26, Wt)
    f.save(f'/home/claude/xw/tex/{name}_face_concentrate.png')
    # crying: tears streaming, wobbly mouth
    f = new(); brows(f, 'worried'); rows = list(T)
    first = next(k for k, r in enumerate(rows) if any(c in IRIS for c in r))
    f.grid(4, eye_row, ['.........'] * (first - 1) + ['OOOOOOOOO'] + rows[first:], pal, mirror=True)
    for y in range(eye_row + len(rows), 30):
        f.px(7, y, '#7fc0f0'); f.px(24, y, '#7fc0f0')
        if y % 2 == 0: f.px(8, y, '#cfe8ff'); f.px(23, y, '#cfe8ff')
    mouth(f, 'wobble'); f.save(f'/home/claude/xw/tex/{name}_face_crying.png')
    # melancholy: looking up, wistful, small frown
    f = new(); brows(f, 'worried'); eyes_shifted(f, 0, 1)
    f.row(26, 13, 18, D); f.px(12, 27, D); f.px(19, 27, D)
    f.save(f'/home/claude/xw/tex/{name}_face_melancholy.png')
    # rage: furious yell with an anger mark and a red flush
    f = new()
    for y in range(32):
        for x in range(32):
            r_, g_, b_, a_ = f.img.getpixel((x, y))
            f.img.putpixel((x, y), (min(255, int(r_ * 1.12 + 10)), int(g_ * 0.88), int(b_ * 0.85), a_))
    brows(f, 'angry')
    f.grid(4, eye_row + 1, ['.OOOOOOOO', 'OOOOOOOOO', '.WWWPWWW.', '..wWWWw..'], pal, mirror=True)
    for y, (x0, x1) in zip(range(22, 29), ((14, 17), (12, 19), (11, 20), (11, 20), (11, 20), (12, 19), (14, 17))): f.row(y, x0, x1, D)
    f.row(23, 13, 18, Wt); f.row(27, 13, 18, Wt)
    for y in range(24, 27): f.row(y, 12, 19, Rd)
    f.grid(24, 3, ['R.R', '.R.', 'R.R'], {'R': '#e02020'})      # anger mark
    f.save(f'/home/claude/xw/tex/{name}_face_rage.png')
    # glitch: eyes split into cyan / magenta copies, flat line mouth with a skip
    f = new(); brows(f, 'flat')
    gp = dict(pal)
    f.grid(3, eye_row, [r.replace('.', '.') for r in T], {k: '#40f0ff' for k in pal}, mirror=True)
    f.grid(5, eye_row, list(T), {k: '#ff40d0' for k in pal}, mirror=True)
    f.grid(4, eye_row, list(T), gp, mirror=True)
    f.row(25, 12, 15, D); f.row(24, 16, 19, D); f.px(20, 25, '#40f0ff')
    f.save(f'/home/claude/xw/tex/{name}_face_glitch.png')
    # robot 2 / 3: screen eyes looking left / right (for the robot dance), "blink" scanline
    for tag, dx in (('robot_l', -1), ('robot_r', 1)):
        f = new(); brows(f, 'flat')
        f.grid(4, eye_row, ['OOOOOOOO.', 'OWWWWWWO.', 'OWWWWWWO.', 'OWWWWWWO.', 'OWWWWWWO.', 'OOOOOOOO.'], pal, mirror=True)
        for side_x in (4, 19):
            px0 = side_x + 3 + dx
            for yy in (eye_row + 2, eye_row + 3):
                f.px(px0, yy, '#120b0a'); f.px(px0 + 1, yy, '#120b0a')
        f.row(24, 12, 19, D); f.row(26, 12, 19, D); f.px(12, 25, D); f.px(19, 25, D)
        for x in range(13, 19): f.px(x, 25, Wt if x % 2 else D)
        f.save(f'/home/claude/xw/tex/{name}_face_{tag}.png')

    # rain: soaked and not happy about it - raindrops running down the face (two frames so they drip)
    rain_drops = {'g': '#bfe0ff', 'G': '#7fb4e8'}
    def drips(f, frame):
        spots = [(5, 8), (24, 6), (9, 20), (22, 19), (15, 4)] if frame == 0 else [(5, 11), (24, 9), (9, 23), (22, 22), (17, 6)]
        for x, y in spots:
            f.grid(x, y, ['g', 'G'], rain_drops)
        for x in range(4, 28, 5):          # wet fringe line dripping at the top
            f.px(x + frame, 0, '#7fb4e8')
    def rain_eyes(f):
        f.grid(4, eye_row + 2, ['.........', 'OOOOOOOOO', 'OOOOOOOOO', '.WwDDPPw.', '..wIIIw..'], pal, mirror=True)
    for frame in (0, 1):
        f = new(); brows(f, 'worried'); rain_eyes(f)
        if name == 'mikiko':
            mouth(f, 'wobble')                                 # fire boy hates getting wet: pouty, wobbly
            f.grid(4, 21, ['.CC.CC...'], {'C': '#8ab0d8'}, mirror=True)
        else:
            mouth(f, 'line')                                   # Rhalix: just unimpressed
        drips(f, frame)
        f.save(f'/home/claude/xw/tex/{name}_face_rain{"" if frame == 0 else "2"}.png')

    # ---------------- talking mouths (voice chat): drawn over whatever face is showing
    def mouth_overlay(n):
        img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        f = Face(S, SD, SH); f.img = img
        for y in range(22, 29):
            f.row(y, 10, 22, S)
        D, Rd, Pk, Wt = '#3a1a10', '#7a2a24', '#c45a5a', '#f4efe6'
        shapes = {
            1: [(25, 13, 18, D), (26, 14, 17, Rd)],                                                        # barely parted
            2: [(24, 13, 18, D), (25, 13, 18, Rd), (26, 14, 17, D)],                                       # small
            3: [(23, 14, 17, D), (24, 13, 18, D), (25, 13, 18, Rd), (26, 13, 18, Rd), (27, 14, 17, D)],    # open
            4: [(22, 14, 17, D), (23, 13, 18, D), (24, 12, 19, D), (25, 12, 19, D), (26, 12, 19, D), (27, 13, 18, D), (28, 14, 17, D)],
            5: [(22, 14, 17, D), (23, 13, 18, D), (24, 13, 18, D), (25, 13, 18, D), (26, 13, 18, D), (27, 14, 17, D)],  # round "ooh"
        }
        for y, x0, x1, c in shapes[n]:
            f.row(y, x0, x1, c)
        if n == 3:
            f.row(24, 14, 17, Wt)            # top teeth
        if n == 4:
            f.row(23, 14, 17, Wt)
            for y, x0, x1 in ((24, 13, 18), (25, 13, 18), (26, 13, 18)):
                f.row(y, x0, x1, Rd)
            f.row(26, 14, 17, Pk)            # tongue
            f.row(27, 14, 17, Rd)
        if n == 5:
            for y in (23, 24, 25, 26):
                f.row(y, 14, 17, Rd)
            f.row(25, 15, 16, '#4a1410')     # deep inside
            f.row(26, 15, 16, Pk)
        img.save(f'/home/claude/xw/tex/{name}_mouth_{n}.png')
    for n in (1, 2, 3, 4, 5):
        mouth_overlay(n)


def mik_eyes(f):
    p = {'O': '#120b0a', 'W': '#f2f0ee', 'w': '#cfcbca', 'D': '#6a2c0c', 'I': '#e8801a', 'Y': '#ffc23c', 'P': '#2a1206', 'H': '#ffffff', 'l': '#6e4228'}
    f.grid(4, 13, ['OO.......', '.OOOOOOOO', 'OOOOOOOOO', '.WwDDDDw.', '.WWDPPHW.', '.WWIPPIW.', '.WWIIIIW.', '..wYYYYw.'], p, mirror=True)


def rha_eyes(f):
    p = {'O': '#0e0809', 'W': '#efeceb', 'w': '#c9c4c4', 'D': '#7a0a0e', 'I': '#d8141c', 'Y': '#ff4a46', 'P': '#2a0604', 'H': '#ffffff'}
    f.grid(4, 13, ['OOO......', '.OOOOOOOO', '.WwDDDHW.', '..WIPPIW.', '...YYYY..'], p, mirror=True)


def mik_mouth(f):
    f.row(25, 11, 20, '#3a1a10'); f.px(10, 24, '#3a1a10'); f.px(21, 24, '#3a1a10')


def rha_mouth(f):
    dark = '#1e0c0a'
    f.row(24, 11, 18, dark); f.row(23, 19, 22, dark)
    f.px(19, 24, '#f0ece4'); f.px(20, 24, '#f0ece4'); f.px(21, 24, '#f0ece4'); f.px(22, 24, dark); f.row(25, 19, 21, dark)


expressions('mikiko', ('#966240', '#84553a', '#a46c48'),
            {'O': '#120b0a', 'W': '#f2f0ee', 'w': '#cfcbca', 'D': '#6a2c0c', 'I': '#e8801a', 'Y': '#ffc23c', 'P': '#2a1206', 'H': '#ffffff', 'B': '#22140e'},
            mik_eyes, 13, mik_mouth)
expressions('rhalix', ('#5a3621', '#4c2d1c', '#684029'),
            {'O': '#0e0809', 'W': '#efeceb', 'w': '#c9c4c4', 'D': '#7a0a0e', 'I': '#d8141c', 'Y': '#ff4a46', 'P': '#2a0604', 'H': '#ffffff', 'B': '#140c10'},
            rha_eyes, 13, rha_mouth)
