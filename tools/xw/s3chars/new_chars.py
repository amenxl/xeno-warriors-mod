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
    H1, H2 = '#3a1a2a', '#5a2c40'
    hair(f, H2, H1, back_len=26.0, side_len=27.0, spikes=crown_spikes(12, 3.6, 1.6, seed=61) + [([0.5, 33.2, -0.5], [0.2, 1, 0.2], 4.2, 1.4)],
         locks=fringe_locks(7, 31.4, 8.0, 4.6, 1.6, 0.45)
         + [([s * 4.6, 30.0, z], [s * 0.9, -0.6, 0.2], 3.6, 1.5) for s in (1, -1) for z in (-2.0, 1.2, 3.4)])
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
    B1, B2, BL = '#5a3a22', '#7a5434', '#e8d088'
    hair(f, B2, B1, back_len=24.0, side_len=25.0,
         locks=fringe_locks(7, 31.6, 8.0, 4.6, 1.6, 0.4)
         + [([s * 4.55, 30.4, z], [s * 0.35, -1, 0.1], 5.0, 1.6) for s in (1, -1) for z in (-2.6, 0.4)])
    # the side ponytail (her right, out and down), fading to blonde
    for i, (dx, dy, dz) in enumerate(((5.0, 30.6, 1.6), (6.4, 29.4, 2.2), (7.4, 27.8, 2.6), (7.8, 26.0, 2.8), (7.6, 24.2, 2.8), (7.0, 22.6, 2.6))):
        c = (B1, B2, '#a0784c', '#c8a868', BL, BL)[i]
        r = 1.5 - i * 0.12
        f.add('head', [dx - r, dy - r, dz - r], [dx + r, dy + r, dz + r], c)
    f.add('head', [4.4, 30.4, 0.8], [5.6, 31.6, 2.4], '#141216')                                    # hair tie
    for s in (1, -1):                                                                                # the two little curls on top
        lock(f, [s * 1.6, 33.0, -0.6], [s * 0.2, 1, 0.5], 2.6, 1.0, (B1, B1, B2), taper=(0.9, 0.7))
        lock(f, [s * 1.9, 34.8, 0.6], [s * 0.4, -0.2, 1], 1.4, 0.9, (B1, B1, B1), taper=(0.9, 0.7))
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
    H1, H2 = '#0e0c10', '#1a1820'
    hair(f, H2, H1, back_len=25.0, side_len=26.0, spikes=crown_spikes(9, 3.0, 1.6, seed=73),
         locks=fringe_locks(7, 31.4, 8.0, 4.6, 1.6, 0.45)
         + [([s * 4.6, 30.2, z], [s * 0.6, -1, 0.2], 4.4, 1.5) for s in (1, -1) for z in (-2.2, 1.0, 3.4)]
         + [([x, 30.6, 4.7], [0, -1, 0.3], 4.6, 1.8) for x in (-2.4, 0.2, 2.6)])
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

def bare(shirt, pantsc, shoesc, sleeve=12, pl=12):
    return clothes(shirt, sleeve, pantsc, pl, shoesc, 3)

SPECS = {
    'kyle': dict(name='Kyle', fit=kyle, skin='#f2e2e2', eyes='#8a3ad8', hair=['#121114', '#1e1c22'],
                 paint=bare('#eceef0', '#16151a', '#16151a'), eye=['.........', 'kkkkkkkkk', '.wddpddw.', '.wWoppow.', '..wOOOw..', '..kkkkk..'],
                 brow=['.........', '.kkkkkk..'], mouth=['k....k', '.kkkk.'], mouth_x=13, mouth_y=24),
    'zack': dict(name='Zack', fit=zack, skin='#f0d0c4', eyes='#8a8e98', hair=['#3a1a2a', '#5a2c40'],
                 paint=bare('#151418', '#3a3a40', '#141316', 5, 6), brow=['.........', 'kkkkkkk..'],
                 mouth=['kkkkkk', '.W....'], mouth_x=13, mouth_y=24),
    'kaitlyn': dict(name='Kaitlyn', fit=kaitlyn, skin='#d8a888', eyes='#2a1a12', hair=['#5a3a22', '#7a5434'],
                    paint=clothes('#141217', 12, '#141217', 10, '#141216', 3, extra=lambda part, face, x, y, W, H, o:
                        ('#d8a888' if part == 'body' and face in ('front', 'back', 'right', 'left') and 7 <= y <= 9 else None)),   # her bare midriff eye=['..kkkkkk.', 'kkkkkkkkk', '.kddpddk.', '.kWoppok.', '.kWoopok.', '..kOOOk..', '..kkkkk..'],
                    mouth=['.k..k.', '..kk..', '..WW..'], mouth_x=13, mouth_y=24),
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
