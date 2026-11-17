"""Kyle (C2, Government), Zack and Kaitlyn (Ai Oni's students), Chase (the Fox King's Keeper): skins, 3D details and
HD faces from the user's Gacha refs (chars/Kyle, chars/Akuma Squad, chars/Keepers). Same tools as the SR bosses
(tools/xw/sr/sr_named.py). Run: python3 new_chars.py [--export] - previews to /tmp/new_chars.png."""
import sys, math
sys.path.insert(0, '/home/claude/xw/npcs'); sys.path.insert(0, '/home/claude/xw/outfits'); sys.path.insert(0, '/home/claude/xw/detail'); sys.path.insert(0, '/home/claude/xw/ultima')
import npcs, core
from npcs import Fit, pack, shell, pants, shoes, tee, clothes, paint_skin, face_img, export, hair, fringe_locks, crown_spikes, Adapter, TEX, MOD
from PIL import Image, ImageDraw
import ultima as U

K1, K2, WH = '#141217', '#1f1c24', '#ececec'

def lock(f, root, d, ln, w, cols, thick=0.8, taper=(0.62, 0.3)):
    core.lock(Adapter(f), 'Head', root, d, ln, w, thick, cols, wide='x', taper=taper)

# ------------------------------------------------------------------ Kyle
def kyle(f):
    """Head of C2: a black bob to his jaw with a heavy fringe, a white lab coat with black bands round the sleeves, a
    black top with the C2 star emblem under it, grey high collar with a zip; black trousers, black and white shoes."""
    HB, HB2 = '#121114', '#1e1c22'
    hair(f, HB2, HB, back_len=25.5, side_len=25.0,
         locks=fringe_locks(7, 31.6, 8.0, 4.6, 1.5, 0.25)
         + [([s * 4.55, 30.6, z], [s * 0.25, -1, 0.05], 5.4, 1.8) for s in (1, -1) for z in (-3.0, -0.6, 2.0)]
         + [([x, 30.8, 4.7], [0, -1, 0.1], 5.6, 2.0) for x in (-2.6, 0, 2.6)])
    f.add('head', [-4.75, 30.6, -4.95], [4.75, 32.2, -4.6], HB)                               # the heavy fringe
    COAT, BAND = '#eceef0', '#16151a'
    shell(f, 'body', [-4, 11.5, -2], [4, 24, 2], 0.3, '#1a191e',
          {'front': lambda x, y: ('#e8e8ec' if (abs(x - .5) < .12 and abs(y - .32) < .1) and not (abs(x - .5) < .05 and abs(y - .32) < .04) else '#1a191e')})
    for s in (1, -1):                                                                           # the open coat
        x0, x1 = (1.6, 4.5) if s > 0 else (-4.5, -1.6)
        f.add('body', [x0, 10.4, -2.55], [x1, 24.3, -2.3], COAT)
    shell(f, 'body', [-4, 10.4, -2], [4, 24.2, 2], 0.42, COAT, {'front': lambda x, y: None})
    f.add('body', [-1.4, 23.6, -2.6], [1.4, 25.6, 2.0], '#9a9ca4', {'front': lambda x, y: '#5a5c64' if abs(x - .5) < .08 else '#9a9ca4'})   # high collar + zip
    f.both('right_arm', [3.45, 12.0, -2.45], [8.55, 24.4, 2.45], COAT,
           {k: (lambda x, y: BAND if int(y * 7) % 2 == 1 else COAT) for k in ('front', 'back', 'right', 'left')})   # black bands round the sleeves
    pants(f, '#16151a', 1.6, baggy=0.25)
    shoes(f, '#16151a', '#ececec', 1.8)

# ------------------------------------------------------------------ Zack
def zack(f):
    """Ai Oni's student: messy dark maroon hair sticking up all over, a black track jacket with white stripes down the
    sleeves and across the chest, long black fingerless gloves, grey camo shorts, white socks, black-and-white sneakers."""
    H1, H2, H3 = '#3a1a2a', '#5e3048', '#7a4462'
    # (big and spiky: a few thin spikes standing up on top, the sides flaring out in points, pointed bangs between his eyes)
    hair(f, H2, H1, back_len=25.0, side_len=26.0, fringe=True,
         spikes=[([-0.6, 33.0, 0.0], [-0.15, 1, 0.1], 4.6, 0.9), ([0.6, 33.0, -0.6], [0.2, 1, -0.1], 5.4, 0.9), ([1.8, 32.8, 0.6], [0.45, 1, 0.2], 3.8, 0.9),
                 ([-1.8, 32.8, 1.0], [-0.4, 1, 0.3], 3.4, 0.9)]
                + [([s * 3.6, 32.2, z], [s * 1, 0.45, 0.1], 3.2, 1.6) for s in (1, -1) for z in (-2.2, 0.4, 2.8)]       # top flaring out
                + [([x, 32.4, 3.4], [x * 0.15, 0.4, 1], 3.2, 1.6) for x in (-2.4, 0, 2.4)])                                  # and back
    for s_ in (1, -1):                                                                                                   # the sides out in points
        for z, y in ((-2.6, 29.6), (-0.2, 28.4), (2.2, 29.2), (4.0, 30.4)):
            lock(f, [s_ * 4.5, y, z], [s_ * 1, -0.35, 0.15], 3.0, 1.5, (H1, H1, H2))
    for x, ln, tilt in ((-3.0, 2.4, -0.3), (-1.4, 2.8, -0.1), (0.2, 4.2, 0.05), (1.8, 2.8, 0.2), (3.2, 2.2, 0.35)):     # the pointed bangs (one longer between his eyes)
        lock(f, [x, 32.0, -4.8], [tilt, -1, -0.3], ln, 1.5, (H2, H2, H3))
    J, ST = '#151418', '#f0f0f2'
    shell(f, 'body', [-4, 11.4, -2], [4, 24, 2], 0.3, J, {'front': lambda x, y: ST if abs(y - .58) < .05 or (abs(x - .5) < .04 and y > .2) else J})
    f.add('body', [-1.6, 23.4, -2.6], [1.6, 24.6, -2.2], J)                                       # collar
    f.both('right_arm', [3.45, 18.4, -2.45], [8.55, 24.4, 2.45], J,
           {'front': lambda x, y: ST if abs(x - .2) < .12 else J, 'right': lambda x, y: ST if abs(x - .5) < .12 else J})   # stripes down the sleeves
    f.both('right_arm', [3.5, 11.8, -2.5], [8.5, 18.4, 2.5], '#0e0d10')                          # long fingerless gloves
    CA, CB, CC = '#3a3a40', '#5a5a62', '#26262a'
    camo = {k: (lambda x, y: (CA, CB, CC)[(int(x * 5) * 7 + int(y * 6) * 3) % 3]) for k in ('front', 'back', 'right', 'left')}
    f.both('right_leg', [-0.55, 6.0, -2.55], [4.55, 12.3, 2.55], CA, camo)                      # camo shorts
    f.both('right_leg', [-0.32, 1.6, -2.32], [4.32, 3.8, 2.32], '#f2f2f2')                        # white socks
    shoes(f, '#141316', '#f2f2f2', 1.8, lace='#f2f2f2')

# ------------------------------------------------------------------ Kaitlyn
def kaitlyn(f):
    """Ai Oni's student: brown hair fading to blonde, a big side ponytail and two little curls on top, a black hooded
    crop jacket over a white lace crop top, a white sash tied at the front, black trousers with grey cuffs, black shoes."""
    B1, B2, B3, BL = '#3e3018', '#5e4c28', '#9a8648', '#dccb80'     # (dark olive brown on top, fading to blonde at the ends)
    # a shaggy bob to her jaw (the ends blonde), long bangs with one strand down between her eyes
    hair(f, B2, B1, back_len=25.5, side_len=26.0)
    for x, ln, tilt in ((-3.4, 3.2, -0.2), (-2.0, 3.8, -0.1), (-0.6, 3.0, 0.0), (0.3, 5.6, 0.05), (1.6, 3.6, 0.12), (3.0, 3.0, 0.25)):
        lock(f, [x, 32.4, -4.7], [tilt, -1, -0.35], ln, 1.5, (B2, B2, B3), taper=(0.6, 0.3))
    for s_ in (1, -1):
        for z, ln in ((-2.8, 6.2), (-0.6, 6.6), (1.8, 6.4), (3.8, 6.0)):
            lock(f, [s_ * 4.6, 31.4, z], [s_ * 0.3, -1, 0.05], ln, 1.6, (B1, B3, BL), taper=(0.62, 0.3))   # down the sides, flicking out
    for x in (-2.8, -0.9, 1.0, 2.9):
        lock(f, [x, 31.4, 4.6], [x * 0.06, -1, 0.3], 6.2, 1.8, (B1, B3, BL), taper=(0.6, 0.3))               # the back
    # two big ponytails the same size, high on each side: out and up, then falling, blonde at the ends
    for s_ in (1, -1):
        lock(f, [s_ * 4.4, 32.0, 1.4], [s_ * 1, 0.45, 0.35], 4.6, 2.6, (B1, B2, B3), thick=1.6, taper=(0.9, 0.75))
        lock(f, [s_ * 8.2, 34.0, 2.6], [s_ * 0.9, -0.5, 0.2], 4.2, 2.4, (B3, BL, BL), thick=1.4, taper=(0.75, 0.4))
        lock(f, [s_ * 7.6, 33.4, 2.2], [s_ * 0.5, -1, 0.1], 4.8, 1.8, (B3, BL, BL), thick=1.2, taper=(0.7, 0.35))
        x0, x1 = sorted([s_ * 4.2, s_ * 5.4])
        f.add('head', [x0, 31.0, 0.6], [x1, 32.6, 2.2], '#141216')                                         # hair ties
    # the two little curls sticking up: a big one over the ponytail side, a small one on the other
    for i in range(7):
        a_ = math.pi * (0.1 + i / 6 * 0.9)
        cx, cy = 1.6 + 1.4 * (1 - math.cos(a_)), 33.0 + 2.4 * math.sin(a_)
        f.add('head', [cx - 0.45, cy - 0.45, -0.9], [cx + 0.45, cy + 0.45, -0.1], B1)
    for i in range(5):
        a_ = math.pi * (0.1 + i / 4 * 0.85)
        cx, cy = -2.0 - 0.9 * (1 - math.cos(a_)), 33.0 + 1.4 * math.sin(a_)
        f.add('head', [cx - 0.4, cy - 0.4, -0.6], [cx + 0.4, cy + 0.4, 0.2], B1)
    J = '#141217'
    shell(f, 'body', [-4, 17.6, -2], [4, 24, 2], 0.22, '#f2f2f2', {'front': lambda x, y: '#d8d8dc' if y < .12 and int(x * 10) % 2 else '#f2f2f2',
                                                                 'back': None, 'left': None, 'right': None, 'top': None})   # lace crop top (only its front shows)
    for s in (1, -1):
        x0, x1 = (1.4, 4.45) if s > 0 else (-4.45, -1.4)
        f.add('body', [x0, 17.2, -2.5], [x1, 24.3, -2.28], J)                                         # the crop jacket
    shell(f, 'body', [-4, 17.2, -2], [4, 24.2, 2], 0.4, J, {'front': lambda x, y: None})
    f.add('body', [-3.4, 22.0, 2.4], [3.4, 24.4, 3.4], J)                                             # its hood, down at the back
    f.both('right_arm', [3.45, 12.4, -2.45], [8.55, 24.4, 2.45], J)
    f.both('right_arm', [3.4, 12.0, -2.5], [8.6, 13.0, 2.5], '#f2f2f2')                               # white cuffs
    f.add('body', [-4.4, 11.8, -2.45], [4.4, 13.0, 2.45], '#f2f2f2')                                   # the white sash
    f.add('body', [-1.0, 9.2, -2.75], [1.0, 12.4, -2.5], '#f2f2f2')
    pants(f, J, 2.4, '#5a5a62', baggy=0.35)
    shoes(f, '#141216', '#f2f2f2', 1.8, lace='#f2f2f2')

# ------------------------------------------------------------------ Chase
def chase(f):
    """The old Fox King's Keeper: messy black hair, two red-and-black swords crossed on his back, a black jacket with
    red trim and red stripes on the sleeves over a white tee with a black emblem, black coat tails, black shorts, grey sneakers."""
    H1, H2, BR = '#0c0a0e', '#18161c', '#2e1c12'
    # (shaggy and hanging down: long strands over his face and eyes, down the sides to his jaw, one swept up on top)
    hair(f, H2, H1, back_len=24.0, side_len=24.0, fringe=True)
    lock(f, [0.8, 33.0, -2.4], [0.25, 0.35, 1], 4.2, 1.6, (H2, H2, H1))                              # one swept back over the top
    for x, ln, tilt in ((-3.6, 4.6, -0.25), (-2.2, 3.4, -0.1), (-0.4, 5.4, 0.0), (1.0, 3.6, 0.08), (2.4, 4.2, 0.2), (3.6, 4.8, 0.3)):
        lock(f, [x, 32.4, -4.6], [tilt, -1, -0.42], ln, 1.5, (H2, H1, H1), taper=(0.55, 0.25))       # long bangs, his eyes showing between them
    for s_ in (1, -1):
        for z, ln in ((-2.8, 7.0), (-0.6, 7.4), (1.8, 7.0), (3.8, 6.6)):
            lock(f, [s_ * 4.6, 31.6, z], [s_ * 0.18, -1, 0.05], ln, 1.6, (H2, H1, H1), taper=(0.6, 0.3))   # down the sides
        lock(f, [s_ * 4.3, 29.6, -1.6], [s_ * 0.1, -1, -0.1], 4.6, 1.0, (BR, BR, H1))                     # the brown underneath
    for x in (-2.8, -0.9, 1.0, 2.9):
        lock(f, [x, 31.6, 4.6], [x * 0.05, -1, 0.3], 7.2, 1.8, (H2, H1, H1), taper=(0.6, 0.3))           # the back
    J, RD = '#141217', '#c41e24'
    shell(f, 'body', [-4, 11.6, -2], [4, 24, 2], 0.24, '#f2f2f2',
          {'front': lambda x, y: '#141217' if abs(x - .5) < .14 and abs(y - .35) < .12 and (abs(x - .5) > .06 or abs(y - .35) > .05) else '#f2f2f2',
           'back': None, 'left': None, 'right': None, 'top': None})   # the emblem (only the tee's front shows)
    for s in (1, -1):
        x0, x1 = (1.8, 4.5) if s > 0 else (-4.5, -1.8)
        f.add('body', [x0, 11.0, -2.55], [x1, 24.3, -2.3], J, {'front': (lambda q: lambda x, y: RD if (x < .18 if q > 0 else x > .82) else J)(s)})   # red trim down the front
    shell(f, 'body', [-4, 11.0, -2], [4, 24.2, 2], 0.42, J, {'front': lambda x, y: None})
    f.add('body', [-4.6, 4.0, 2.0], [4.6, 11.4, 2.9], J, {'back': lambda x, y: '#26232c' if abs(x - .5) < .03 else J})   # coat tails
    f.both('right_arm', [3.45, 12.2, -2.45], [8.55, 24.4, 2.45], J,
           {k: (lambda x, y: RD if 0.28 < y < 0.36 or 0.5 < y < 0.56 else J) for k in ('front', 'back', 'right', 'left')})
    f.both('right_arm', [3.4, 12.0, -2.5], [8.6, 13.0, 2.5], RD)
    for s, ang in ((1, 32), (-1, -32)):                                                              # the two swords on his back
        f.add('body', [-0.45, 4.0, 3.0], [0.45, 26.0, 3.6], J, rot=[0, 0, ang], origin=[0, 16, 3.3])
        f.add('body', [-0.6, 21.0, 2.9], [0.6, 25.6, 3.7], RD, rot=[0, 0, ang], origin=[0, 16, 3.3])
        f.add('body', [-1.2, 20.4, 2.85], [1.2, 21.0, 3.75], '#2a2830', rot=[0, 0, ang], origin=[0, 16, 3.3])
    pants(f, J, 6.4, J, baggy=0.35)                                                                  # black shorts
    shoes(f, '#5a5a62', '#e8e8ec', 2.2, lace='#e8e8ec')

def kate_face(px, cm, dark):                                  # black liner down from her eyes, a little fang, a red open grin
    for cx in (5, 26):
        for j in range(3): px[cx, 21 + j] = (16, 12, 14, 255)
    for x in range(15, 18): px[x, 25] = (214, 30, 30, 255)
    for x in range(15, 17): px[x, 26] = (214, 30, 30, 255)

def bare(shirt, pantsc, shoesc, sleeve=12, pl=12):
    return clothes(shirt, sleeve, pantsc, pl, shoesc, 3)

SPECS = {
    'kyle': dict(name='Kyle', fit=kyle, skin='#f2e2e2', eyes='#8a3ad8', hair=['#121114', '#1e1c22'],
                 paint=bare('#eceef0', '#16151a', '#16151a'), eye=['.........', 'kkkkkkkkk', '.wddpddw.', '.wWoppow.', '..wOOOw..', '..kkkkk..'],
                 brow=['.........', '.kkkkkk..'], mouth=['k....k', '.kkkk.'], mouth_x=13, mouth_y=24),
    'zack': dict(name='Zack', fit=zack, skin='#f0d0c4', eyes='#8a8e98', hair=['#3a1a2a', '#5a2c40'],
                 paint=bare('#151418', '#3a3a40', '#141316', 5, 6), brow=['.........', 'kkkkkkk..'],
                 mouth=['kkkkkk', '.W....'], mouth_x=13, mouth_y=24),
    'kaitlyn': dict(name='Kaitlyn', fit=kaitlyn, skin='#e0a890', eyes='#1a120e', hair=['#3e3018', '#5e4c28'],
                    paint=clothes('#141217', 12, '#141217', 10, '#141216', 3, extra=lambda part, face, x, y, W, H, o:
                        ('#d8a888' if part == 'body' and face in ('front', 'back', 'right', 'left') and 7 <= y <= 9 else None)),   # her bare midriff eye=['..kkkkkk.', 'kkkkkkkkk', '.kddpddk.', '.kWoppok.', '.kWoopok.', '..kOOOk..', '..kkkkk..'],
                    mouth=['kkkkkk', 'kWkkkk', '.kkkk.'], mouth_x=13, mouth_y=24, face_extra=[kate_face]),
    'chase': dict(name='Chase', fit=chase, skin='#7a4a30', eyes='#d07a18', hair=['#0e0c10', '#1a1820'],
                  paint=bare('#141217', '#141217', '#5a5a62', 12, 6), brow=['.........', 'kkkkkk...'],
                  mouth=['.kk.'], mouth_x=14, mouth_y=25),
}

if __name__ == '__main__':
    tiles = []
    for key, spec in SPECS.items():
        fit = Fit(); spec['fit'](fit)
        atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); pack(fit, atlas, region=(0, 0, 256, 256))
        skin = paint_skin(spec); face = face_img(spec)
        out = '/tmp/claude-0/newchars/'
        import os; os.makedirs(out, exist_ok=True)
        if '--export' in sys.argv:
            skin.save(TEX + key + '.png'); atlas.save(TEX + key + '_details.png'); face.save(TEX + key + '_face.png')
            mj = export(fit, MOD + key + '.json')
        else:
            mj = export(fit, out + key + '.json')
        im = U.preview(mj, skin, atlas, face, yaws=(20, 200), scale=10)
        ImageDraw.Draw(im).text((6, 6), spec['name'], fill=(255, 255, 255)); tiles.append(im)
    tw, th = tiles[0].size
    sheet = Image.new('RGB', (tw * 2, th * 2), (30, 30, 36))
    for i, t in enumerate(tiles): sheet.paste(t, ((i % 2) * tw, (i // 2) * th))
    sheet.save('/tmp/claude-0/new_chars.png'); print(sheet.size)
