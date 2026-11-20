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
def chase(f, cap=False):
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
    if cap:                                                                         # his other look: a navy cap and blue headphones,
        NV, NV2, BL = '#141a3a', '#0e1228', '#2a4ae0'                                #  a navy tee with a blue stripe, black fingerless gloves,
        f.add('head', [-4.8, 30.2, -4.8], [4.8, 34.0, 4.8], NV, {'top': lambda x, y: NV2 if abs(x - .5) < .05 else NV})   #  navy cargo trousers
        f.add('head', [-4.6, 30.2, -7.2], [4.6, 31.0, -4.8], NV2)                    # the brim
        f.add('head', [-1.0, 31.6, -4.95], [1.0, 33.0, -4.8], '#f2f2f2')            # the cap's emblem
        for s_ in (1, -1):
            f.add('head', [s_ * 4.6 - 0.8, 25.6, -1.6], [s_ * 4.6 + 0.8, 29.2, 1.6], BL)   # headphones
            f.add('head', [s_ * 4.8 - 0.4, 29.0, -0.4], [s_ * 4.8 + 0.4, 31.0, 0.4], '#1a1a20')
        shell(f, 'body', [-4, 11.6, -2], [4, 24, 2], 0.26, NV, {k: (lambda x, y: BL if abs(x - .62) < .05 else NV) for k in ('front', 'back')})
        f.both('right_arm', [3.62, 19.8, -2.38], [8.38, 24.38, 2.38], NV, {k: (lambda x, y: BL if y > .8 else NV) for k in ('front', 'back', 'right', 'left')})
        f.both('right_arm', [3.5, 12.0, -2.5], [8.5, 15.2, 2.5], J)                  # fingerless gloves
        pants(f, NV2, 1.6, baggy=0.4)
        f.both('right_leg', [-0.8, 6.0, -2.8], [4.8, 7.0, 2.8], BL)                  # blue bands at the knees
        f.both('right_leg', [3.9, 7.6, -1.2], [5.0, 10.6, 1.2], NV)                  # cargo pockets
        shoes(f, '#141216', '#e8e8ec', 2.0)
        return
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
    for s, ang in ((1, 32), (-1, -32)):                                                              # the two swords on his back (hidden while he has them drawn)
        f.add('body', [-0.45, 4.0, 3.0], [0.45, 26.0, 3.6], J, rot=[0, 0, ang], origin=[0, 16, 3.3], group=('back_swords', None))
        f.add('body', [-0.6, 21.0, 2.9], [0.6, 25.6, 3.7], RD, rot=[0, 0, ang], origin=[0, 16, 3.3], group=('back_swords', None))
        f.add('body', [-1.2, 20.4, 2.85], [1.2, 21.0, 3.75], '#2a2830', rot=[0, 0, ang], origin=[0, 16, 3.3], group=('back_swords', None))
    pants(f, J, 6.4, J, baggy=0.35)                                                                  # black shorts
    shoes(f, '#5a5a62', '#e8e8ec', 2.2, lace='#e8e8ec')

# ------------------------------------------------------------------ Fox Girl (Kitsune)
def fox_girl(f, sakura=False):
    """Kitsune, the Fox Girl - Kitsune's Farm's owner, Mikiko's old trainer (the user's Gacha ref): a choppy bob, hot pink
    over black (black underneath and at the back, the pink fringe and sides over it), a pink jacket with wide dark maroon
    cuffs open over a long white top with a boxed black-and-white hem, a little pink gem on a cord, black shorts, dark
    maroon knee socks, pink shoes. Sakura (the user's illustration): a pink caped jacket with braided dark-red trim, a
    pink cameo brooch at the collar, a long white dress."""
    BK, BK2, PK, PK2, PK3 = '#141216', '#22202a', '#d8247e', '#b0186a', '#f0509a'
    hair(f, PK, BK, back_len=25.0, side_len=25.6, fringe=True)
    for x, ln, tilt in ((-3.6, 4.0, -0.25), (-2.2, 4.8, -0.12), (-0.8, 5.4, -0.04), (0.6, 4.6, 0.05), (2.0, 5.2, 0.14), (3.4, 4.0, 0.26)):
        lock(f, [x, 32.4, -4.7], [tilt, -1, -0.35], ln, 1.6, (PK, PK, PK3), taper=(0.62, 0.3))         # the long pink fringe, choppy
    for s_ in (1, -1):
        for z, ln in ((-2.8, 6.4), (-0.6, 7.0), (1.6, 6.6)):
            lock(f, [s_ * 4.6, 31.4, z], [s_ * 0.22, -1, 0.0], ln, 1.7, (PK, PK2, PK3), taper=(0.62, 0.3))  # pink down the sides to her jaw
        lock(f, [s_ * 4.5, 31.8, 3.4], [s_ * 0.9, -0.4, 0.5], 3.2, 1.6, (BK, BK, BK2))                    # black flicking out behind
    for x in (-2.8, -0.9, 1.0, 2.9):
        lock(f, [x, 31.6, 4.6], [x * 0.08, -1, 0.35], 6.0, 1.8, (BK, BK, BK2), taper=(0.6, 0.3))           # the back, black
    for x, d in ((-1.6, [-0.4, 1, 0.5]), (0.4, [0.2, 1, 0.6]), (2.2, [0.6, 0.8, 0.5])):
        lock(f, [x, 33.0, 2.2], d, 2.6, 1.4, (BK, BK2, BK2), taper=(0.7, 0.35))                            # messy black on top, behind the pink
    f.add('head', [-4.9, 28.6, -2.4], [-4.5, 30.0, -1.4], '#f0509a')                                     # a little pink clip at her ear
    if sakura:
        P1, P2, TR, DR = '#e4628e', '#c84a76', '#7a2238', '#ece6ea'
        shell(f, 'body', [-4, 9.0, -2], [4, 24, 2], 0.22, DR)                                           # the long white dress
        trim = lambda x, y: TR if y > .93 or y < .05 else P1
        for s in (1, -1):
            x0, x1 = (0.6, 4.5) if s > 0 else (-4.5, -0.6)
            f.add('body', [x0, 8.6, -2.55], [x1, 24.3, -2.3], P1, {'front': (lambda q: lambda x, y: TR if (x < .14 if q > 0 else x > .86) or y < .05 else P1)(s)})   # the jacket fronts, braided edges
        shell(f, 'body', [-4, 8.6, -2], [4, 24.2, 2], 0.4, P1, {'front': lambda x, y: None, 'back': lambda x, y: TR if y < .04 else P1})
        f.add('body', [-4.9, 18.8, -2.9], [4.9, 24.6, 2.9], P1, {k: (lambda x, y: TR if y < .1 else P1) for k in ('front', 'back', 'left', 'right')})   # the capelet over her shoulders
        f.add('body', [-1.0, 22.2, -3.05], [1.0, 24.0, -2.9], '#d8a838')                                 # the cameo brooch: gold rim
        f.add('body', [-0.7, 22.5, -3.15], [0.7, 23.7, -3.0], '#f088b0')                                 #   and its pink cameo
        f.both('right_arm', [3.45, 12.2, -2.45], [8.55, 24.4, 2.45], P1, {k: (lambda x, y: TR if y < .06 else P1) for k in ('front', 'back', 'right', 'left')})
        f.both('right_arm', [3.4, 12.0, -2.5], [8.6, 12.8, 2.5], TR)                                       # braided cuffs
        f.both('right_arm', [3.3, 18.6, -2.9], [8.7, 24.6, 2.9], P1, {k: (lambda x, y: TR if y < .12 else P2) for k in ('front', 'back', 'right', 'left')})   # the capelet over her arms
        pants(f, DR, 3.0, baggy=0.55)                                                                     # the dress down to her shins
        f.add('body', [-4.7, 3.4, -2.75], [4.7, 12.0, 2.75], DR)                                          # (its skirt, between her legs)
        shoes(f, '#e4628e', '#7a2238', 1.6)
        return
    JK, CF = PK, '#3a0c1e'
    shell(f, 'body', [-4, 9.4, -2], [4, 24, 2], 0.22, '#f2f2f2',
          {'front': lambda x, y: ('#141216' if y < .12 and int(x * 9) % 2 == 0 else '#f2f2f2'), 'back': lambda x, y: ('#141216' if y < .12 and int(x * 9) % 2 == 0 else '#f2f2f2')})   # long white top, boxed hem
    for s in (1, -1):
        x0, x1 = (1.6, 4.5) if s > 0 else (-4.5, -1.6)
        f.add('body', [x0, 14.6, -2.55], [x1, 24.3, -2.3], JK, {'front': (lambda q: lambda x, y: '#a01860' if (x < .14 if q > 0 else x > .86) else JK)(s)})   # the open jacket
    shell(f, 'body', [-4, 14.6, -2], [4, 24.2, 2], 0.4, JK, {'front': lambda x, y: None})
    f.add('body', [-3.2, 22.2, 2.4], [3.2, 24.4, 3.3], JK)                                                 # its hood, down at the back
    f.add('body', [-0.5, 19.6, -2.45], [0.5, 20.8, -2.3], '#e05aa0')                                      # the pink gem
    f.add('body', [-1.6, 20.8, -2.38], [1.6, 21.2, -2.3], '#2a0a16')                                      #   on its cord
    f.both('right_arm', [3.45, 15.0, -2.45], [8.55, 24.4, 2.45], JK)
    f.both('right_arm', [3.3, 12.0, -2.7], [8.7, 15.2, 2.7], CF)                                          # wide dark cuffs
    pants(f, '#141216', 9.6, baggy=0.3)                                                                    # black shorts
    f.both('right_leg', [-0.35, 1.6, -2.35], [4.35, 7.4, 2.35], '#3a1020', {k: (lambda x, y: '#5a1a34' if y > .82 else '#3a1020') for k in ('front', 'back', 'right', 'left')})   # dark knee socks
    shoes(f, '#c8287a', '#2a0a16', 2.0, lace='#f0509a')

def athrea(f):
    """Princess Athrea (Season 2, the user's Gacha ref): very long messy hair, black at the crown fading to dark red,
    falling to her waist, side-swept bangs; a black-and-grey striped tee with red stripes under an open white vest with
    a black inside; red scrunchies on both wrists; black capris with red cuffs; red-and-black striped socks; black
    lace-up boots with red laces. Small dark-maroon wings."""
    HB, HR, HR2, HR3 = '#141012', '#5a1410', '#7a1c14', '#3a0c0c'
    hair(f, HB, HR3, back_len=24.0, side_len=24.0, fringe=True)
    for x, ln, tilt in ((-3.6, 5.6, -0.35), (-2.0, 6.4, -0.28), (-0.4, 5.8, -0.18), (1.2, 4.6, -0.05), (2.8, 5.0, 0.12), (3.8, 4.2, 0.3)):
        lock(f, [x, 32.4, -4.7], [tilt, -1, -0.35], ln, 1.7, (HB, HR3, HR), taper=(0.62, 0.3))          # side-swept bangs, red at the ends
    for s_ in (1, -1):
        for z, ln in ((-2.6, 13.0), (-0.4, 15.0), (1.8, 14.0)):
            lock(f, [s_ * 4.6, 31.2, z], [s_ * 0.3, -1, 0.05], ln, 2.0, (HR3, HR, HR2), taper=(0.66, 0.3))   # long, messy, down past her shoulders
        lock(f, [s_ * 4.4, 30.6, 3.6], [s_ * 0.7, -0.8, 0.4], 3.4, 1.8, (HR3, HR2, HR), taper=(0.6, 0.25))   # flicking out
    for x in (-3.0, -1.0, 1.0, 3.0):
        lock(f, [x, 31.4, 4.7], [x * 0.06, -1, 0.3], 17.0, 2.2, (HR3, HR, HR2), taper=(0.7, 0.3))            # the back, to her waist
    GREY, RED, WHT, BLK = '#3c3c42', '#8a1a14', '#f0eeee', '#141216'
    shell(f, 'body', [-4, 11.6, -2], [4, 24, 2], 0.22, GREY,
          {k: (lambda x, y: RED if int(y * 9) % 3 == 0 else GREY) for k in ('front', 'back', 'right', 'left')})   # striped tee
    for s_ in (1, -1):
        x0, x1 = (1.4, 4.5) if s_ > 0 else (-4.5, -1.4)
        f.add('body', [x0, 15.0, -2.55], [x1, 24.3, -2.3], WHT, {'front': (lambda q: lambda x, y: '#cfcdd2' if (x < .18 if q > 0 else x > .82) else WHT)(s_)})   # the open white vest
        f.add('body', [s_ * 1.0 - 0.9, 21.8, -2.75], [s_ * 1.0 + 0.9, 24.4, -2.5], WHT, rot=[0, 0, s_ * 25], origin=[s_ * 1.0, 23, -2.6])   # its collar
        f.add('body', [s_ * 4.3 - 0.25, 15.0, -2.5], [s_ * 4.3 + 0.25, 24.3, 2.5], WHT)                  # its sides
    f.add('body', [-4.5, 15.0, 2.3], [4.5, 24.3, 2.55], WHT)                                           # and back
    f.both('right_arm', [3.6, 20.6, -2.4], [8.4, 24.4, 2.4], GREY, {k: (lambda x, y: RED if int(y * 5) % 2 == 0 else GREY) for k in ('front', 'back', 'right', 'left')})
    f.both('right_arm', [3.3, 12.6, -2.7], [8.7, 14.2, 2.7], RED, {k: (lambda x, y: '#5a0c0c' if int(x * 6) % 2 == 0 else RED) for k in ('front', 'back', 'right', 'left')})   # red scrunchies
    pants(f, BLK, 6.6, RED, baggy=0.35)                                                                     # black capris, red cuffs
    f.both('right_leg', [-0.32, 2.6, -2.32], [4.32, 6.4, 2.32], BLK, {k: (lambda x, y: RED if int(y * 8) % 2 == 0 else BLK) for k in ('front', 'back', 'right', 'left')})   # striped socks
    shoes(f, BLK, '#0a0a0c', 3.0, lace='#a01c18')                                                           # lace-up boots

def ranrea(f):
    """Princess Ranrea (Season 2, the user's Gacha ref): hair black at the top fading to bright blue, spiky tufts on top,
    long side locks and short twin tails on both sides, white zigzag clips; a salmon-pink dress with a fine
    stripe under a cropped blue jacket, a black belt with square studs, a white choker; black fingerless gloves; red knee
    socks with blue stripes, pink shoes with blue soles. Wings blue with red-pink feather tips."""
    HB, HU, HU2, HU3 = '#141218', '#2a4ad0', '#3a64e8', '#1e2a8a'
    hair(f, HB, HU3, back_len=24.5, side_len=25.0, fringe=True)
    for x, ln, tilt in ((-3.4, 4.6, -0.3), (-1.6, 5.4, -0.16), (0.2, 4.2, 0.0), (1.8, 5.2, 0.14), (3.4, 4.4, 0.3)):
        lock(f, [x, 32.4, -4.7], [tilt, -1, -0.35], ln, 1.7, (HB, HU3, HU), taper=(0.62, 0.3))
    for s_ in (1, -1):
        for z, ln in ((-2.8, 8.4), (-0.4, 9.0)):
            lock(f, [s_ * 4.6, 31.2, z], [s_ * 0.2, -1, 0.0], ln, 1.8, (HU3, HU, HU2), taper=(0.62, 0.3))     # long side locks
    for x, d in ((-1.8, [-0.6, 1, 0.1]), (1.4, [0.5, 1, 0.1])):
        lock(f, [x, 33.0, -0.4], d, 2.6, 1.6, (HB, HB, '#24222c'), taper=(0.7, 0.3))                         # two little black tufts on top
    for s_ in (1, -1):                                                                                      # short twin tails, one each side
        f.add('head', [s_ * 4.3 - 0.6, 29.6, 1.6], [s_ * 4.3 + 0.6, 30.6, 2.6], '#141218')                      #   (a tie)
        lock(f, [s_ * 4.8, 30.2, 2.2], [s_ * 0.8, -0.5, 0.35], 5.5, 2.4, (HU3, HU, HU2), thick=1.8, taper=(0.7, 0.35))
    for x, y in ((-2.2, 31.4), (2.6, 31.8)):                                                                # white zigzag clips
        f.add('head', [x - 1.0, y, -5.0], [x + 1.0, y + 0.35, -4.75], '#f4f6ff', rot=[0, 0, 20], origin=[x, y, -4.9])
        f.add('head', [x - 0.2, y - 0.4, -5.0], [x + 1.2, y - 0.05, -4.75], '#f4f6ff', rot=[0, 0, -25], origin=[x, y, -4.9])
    PK, PK2, BLU, BLK = '#e08a80', '#c86e66', '#2a4cc0', '#141216'
    shell(f, 'body', [-4, 9.0, -2], [4, 24, 2], 0.22, PK, {k: (lambda x, y: PK2 if int(x * 9) % 3 == 0 else PK) for k in ('front', 'back')})   # the striped dress
    for s_ in (1, -1):
        x0, x1 = (1.0, 4.5) if s_ > 0 else (-4.5, -1.0)
        f.add('body', [x0, 16.4, -2.55], [x1, 24.3, -2.3], BLU)                                             # cropped blue jacket
    shell(f, 'body', [-4, 16.4, -2], [4, 24.2, 2], 0.4, BLU, {'front': lambda x, y: None})
    f.add('body', [-0.7, 19.6, -2.45], [0.7, 21.2, -2.3], '#f4f6ff', rot=[0, 0, 45], origin=[0, 20.4, -2.4])   # a little sparkle pin
    f.add('body', [-4.5, 13.4, -2.5], [4.5, 14.2, 2.5], BLK, {'front': lambda x, y: '#9a9aa4' if int(x * 8) % 2 == 0 else BLK})   # belt with studs
    f.add('body', [-2.0, 23.6, -2.45], [2.0, 24.2, -2.2], '#f4f4f8')                                         # white choker
    f.add('body', [-4.6, 8.4, -2.7], [4.6, 11.0, 2.7], PK, {k: (lambda x, y: PK2 if int(x * 9) % 3 == 0 else PK) for k in ('front', 'back', 'right', 'left')})   # the skirt flaring
    f.both('right_arm', [3.45, 15.2, -2.45], [8.55, 24.4, 2.45], BLU)
    f.both('right_arm', [3.35, 15.0, -2.6], [8.65, 15.8, 2.6], '#1a2a7a')
    f.both('right_arm', [3.5, 12.0, -2.5], [8.5, 13.6, 2.5], BLK)                                             # fingerless gloves
    f.both('right_leg', [-0.32, 2.0, -2.32], [4.32, 7.6, 2.32], '#d84a4a', {k: (lambda x, y: BLU if abs(y - .78) < .08 or abs(y - .58) < .06 else '#d84a4a') for k in ('front', 'back', 'right', 'left')})   # red socks, blue stripes
    shoes(f, '#f0a8a8', '#2a4cc0', 2.0)

def kate_face(px, cm, dark):                                  # black liner down from her eyes, a little fang, a red open grin
    for cx in (5, 26):
        for j in range(3): px[cx, 21 + j] = (16, 12, 14, 255)
    for x in range(15, 18): px[x, 25] = (214, 30, 30, 255)
    for x in range(15, 17): px[x, 26] = (214, 30, 30, 255)

def bare(shirt, pantsc, shoesc, sleeve=12, pl=12):
    return clothes(shirt, sleeve, pantsc, pl, shoesc, 3)

SPECS = {
    'athrea': dict(name='Athrea', fit=athrea, skin='#f2e0dc', eyes='#6a3a18', hair=['#5a1410', '#141012'], wing=('hawk_small', '#4a120c'),
                   paint=clothes('#3c3c42', 4, '#141216', 6, '#141216', 2, extra=lambda part, face, x, y, W, H, o:
                       ('#8a1a14' if part == 'body' and y < 10 and y % 3 == 0 else None)),
                   eye=['..kkkkkk.', 'kkkkkkkkk', '.wddpddw.', '.wWoppow.', '.wWoopow.', '..wOOOw..', '..kkkkk..'],
                   brow=['.........', '.kkkkkk..'], mouth=['k.kk.k', '.k..k.'], mouth_x=13, mouth_y=24),
    'ranrea': dict(name='Ranrea', fit=ranrea, skin='#c48a78', eyes='#7a7a84', hair=['#2a4ad0', '#141218'], wing=('angel_small', ('#2a3ac0', '#e05a5a')),
                   paint=clothes('#e08a80', 10, '#e08a80', 4, '#f0a8a8', 2),
                   eye=['..kkkkkk.', 'kkkkkkkkk', '.wddWddw.', '.wWoppow.', '.wWoopow.', '..wOOOw..', '..kkkkk..'],
                   brow=['.........', '.kkkkk...'], mouth=['.kkkk.', 'krrrrk', '.kkkk.'], mouth_x=13, mouth_y=24),
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
    'chase_cap': dict(name='Chase (cap)', fit=lambda f: chase(f, True), skin='#7a4a30', eyes='#d07a18', hair=['#0e0c10', '#1a1820'],
                  paint=bare('#141a3a', '#0e1228', '#141216', 4, 12), brow=['.........', 'kkkkkk...'],
                  mouth=['.kk.'], mouth_x=14, mouth_y=25),
    'fox_girl': dict(name='Fox Girl', fit=fox_girl, skin='#a8684a', eyes='#7a3a8a', hair=['#d8247e', '#141216'],
                     paint=clothes('#d8247e', 12, '#141216', 4, '#c8287a', 2, extra=lambda part, face, x, y, W, H, o:
                         ('#3a1020' if part in ('rleg', 'lleg') and face in ('front', 'back', 'right', 'left') and 6 <= y < 10
                          else '#f2f2f2' if part == 'body' and face == 'front' and 2 <= x <= 5 else None)),   # (pink under the jacket, white down the front)
                     eye=['..kkkkkk.', 'kkkkkkkkk', '.kddpddk.', '.kWoppok.', '.kWoopok.', '..kOOOk..', '..kkkkk..'],
                     brow=['.........', '.kkkkk...'], mouth=['k.kk.k', '.k..k.'], mouth_x=13, mouth_y=24),
    'fox_girl_sakura': dict(name='Fox Girl (Sakura)', fit=lambda f: fox_girl(f, True), skin='#a8684a', eyes='#7a3a8a', hair=['#d8247e', '#141216'],
                     paint=clothes('#e4628e', 12, '#ece6ea', 9, '#e4628e', 2, extra=lambda part, face, x, y, W, H, o:
                         ('#ece6ea' if part == 'body' and face == 'front' and 2 <= x <= 5 else None)),
                     eye=['..kkkkkk.', 'kkkkkkkkk', '.kddpddk.', '.kWoppok.', '.kWoopok.', '..kOOOk..', '..kkkkk..'],
                     brow=['.........', '.kkkkk...'], mouth=['k......k', '.kkkkkk.'], mouth_x=12, mouth_y=24),
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
