"""Ultima face options, round 2: more detailed, anime-style eyes (shaded iris, double highlights, thick lash line with
an outer flick, lid crease, lower lash), tapered brows, shaded mouths."""
import sys
sys.path.insert(0, '/home/claude/xw/ultima')
import ultima as U
from npcs import C, mul
from PIL import Image, ImageDraw, ImageFont

def lerp(a, b, t): return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))

# eye templates: 11 wide, drawn for his left side (outer corner = column 0), mirrored for the right
# k outline/lash  l lash (soft)  c lid crease  U iris top (dark)  u iris mid  o iris  O iris low (light)  p pupil
# W highlight  w sclera  s sclera shadow  L lower lash
EYES = {
 'open': ['...kkkkkkk.', 'lkkkkkkkkkk', 'k.sUUUUUsk.', '..sUuWuUs..', '..suupWus..', '..soppops..', '..wsOOOsw..', '...wsOsw...', '....LLL....'],
 'half': ['...........', 'ccccccccc..', 'lkkkkkkkkkk', 'k.kkkkkkkk.', '..sUuWuUs..', '..soppops..', '..wsOOOsw..', '...wsOsw...', '....LLL....'],
 'half_side': ['...........', 'ccccccccc..', 'lkkkkkkkkkk', 'k.kkkkkkkk.', '..sssUuWuU.', '..wsssopps.', '..wwsssOOs.', '...wwssOs..', '....LLL....'],
 'narrow': ['...........', '...........', 'lkkkkkkkkkk', 'kkkkkkkkkk.', '..sUupWUs..', '..wsOpOsw..', '...wsssw...', '....LLL....', '...........'],
 'cold': ['...kkkkkkk.', 'lkkkkkkkkkk', 'k.sssUssk..', '..swUuWsw..', '..swupuws..', '..swOOOsw..', '..wwsssww..', '...wwwww...', '....LLL....'],
 'sharp': ['.........k.', 'lkkkkkkkkk.', 'kkkkkkkkkkk', '.k.sUuWUsk.', '...supWus..', '...sOppOs..', '...wsOOsw..', '....LLLL...', '...........'],
 'smug': ['...........', '...........', 'ccccccccc..', 'lkkkkkkkkkk', 'k.kkkkkkkk.', '..sUuWuUs..', '..wsOpOsw..', '...wsOsw...', '....LLL....'],
}
BROWS = {
 'flat': ['..........', 'hkkkkkkkk.', '.hhhhhhh..'],
 'low': ['..........', '.........k', 'hkkkkkkkk.'],
 'angry': ['.........k', '.....kkkk.', 'hkkkk.....'],
 'raised': ['...kkkkk..', 'hkk.....k.', '..........'],
 'one_up': None,                       # (the right brow raised, the left flat - drawn specially)
}
MOUTHS = {
 'line': (['.kkkkk.'], 12),
 'small_frown': (['..kkkk..', '.k....k.'], 12),
 'flat_off': (['...kkkkk'], 12),
 'smirk': (['.......k', '.....kk.', 'kkkkk...', '.mmm....'], 12),
 'open_sigh': (['.kkkk.', 'kddddk', '.kkkk.'], 13),
 'tch': (['kkkkkkk', '.mmmm..'], 12),
 'teeth_grit': (['kkkkkkkk', 'kWkWkWkk', 'kkkkkkkk'], 12),
}
OPTS = [
    ('Bored (detailed)', 'flat', 'half', 'line'),
    ('Bored, looking away', 'flat', 'half_side', 'flat_off'),
    ('Unimpressed', 'low', 'half', 'small_frown'),
    ('Cold stare', 'low', 'cold', 'line'),
    ('Sharp / annoyed', 'angry', 'sharp', 'tch'),
    ('Smug', 'one_up', 'smug', 'smirk'),
    ('Calm, eyes open', 'flat', 'open', 'line'),
    ('Narrow, distant', 'flat', 'narrow', 'flat_off'),
    ('Sigh', 'raised', 'half', 'open_sigh'),
    ('Battle (gritted)', 'angry', 'sharp', 'teeth_grit'),
]

def face(spec, brow, eye, mouth):
    S = C(spec['skin']); iris = C(spec['eyes'])
    K = (22, 16, 20); LASH = (52, 40, 48)
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); px = im.load()
    for y in range(32):
        for x in range(32):
            if 6 <= y < 30 and 2 <= x < 30: px[x, y] = S + (255,)
    cm = {'k': K, 'l': LASH, 'L': mul(S, .55), 'c': mul(S, .8), 'h': mul(S, .78), 'm': mul(S, .7), 'd': (90, 36, 44),
          'U': mul(iris, .32), 'u': mul(iris, .62), 'o': iris, 'O': lerp(iris, (255, 255, 255), .45), 'p': mul(iris, .18),
          'W': (255, 255, 255), 'w': (246, 244, 244), 's': (200, 206, 214)}
    def put(rows, x0, y0, mirror=False):
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == '.': continue
                x = x0 + (len(row) - 1 - i if mirror else i)
                if 0 <= x < 32: px[x, y0 + j] = cm[ch] + (255,)
    if brow == 'one_up':
        put(BROWS['flat'], 3, 10); put(BROWS['raised'], 19, 9, True)
    else:
        put(BROWS[brow], 3, 10); put(BROWS[brow], 19, 10, True)
    put(EYES[eye], 3, 13); put(EYES[eye], 18, 13, True)
    rows, mx = MOUTHS[mouth]
    put(rows, mx, 24)
    # soft shading: under the eyes and along the jaw sides (anime cel shade), tiny cheek tint
    for x in list(range(4, 12)) + list(range(20, 28)):
        if px[x, 22][:3] == S: px[x, 22] = lerp(S, mul(S, .9), .5) + (255,)
    for (x, y) in ((5, 21), (6, 21), (25, 21), (26, 21)): px[x, y] = lerp(S, (230, 120, 130), .18) + (255,)
    return im

if __name__ == '__main__':
    title, build, gloves, sleek = U.OPTIONS['A']
    fit = U.Fit(); build(fit)
    atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); U.pack(fit, atlas, region=(0, 0, 256, 256))
    mj = U.npcs.export(fit, '/tmp/ult_tmp.json')
    spec = dict(U.SPEC); spec['paint'] = U.body_paint(gloves, sleek)
    skin = U.paint_skin(spec)
    F = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 15)
    tiles = []
    import os; os.makedirs('/home/claude/xw/ultima/out/anime', exist_ok=True)
    for i, (name, b, e, m) in enumerate(OPTS):
        f = face(spec, b, e, m); f.save(f'/home/claude/xw/ultima/out/anime/face_{i + 1}.png')
        im = U.preview(mj, skin, atlas, f, yaws=(0,), scale=14)
        W = im.width; im = im.crop((int(W * .2), int(im.height * .06), int(W * .8), int(im.height * .44)))
        d = ImageDraw.Draw(im); d.rectangle((0, 0, im.width, 22), fill=(20, 20, 28)); d.text((8, 3), f'{i + 1}. {name}', font=F, fill=(255, 255, 255))
        tiles.append(im)
    tw, th = tiles[0].size
    sheet = Image.new('RGB', (tw * 5, th * 2), (30, 30, 36))
    for i, t in enumerate(tiles): sheet.paste(t, ((i % 5) * tw, (i // 5) * th))
    sheet.save('/tmp/ultima_anime_faces.png'); print(sheet.size)
