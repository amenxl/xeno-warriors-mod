"""Season 3 enemies: the Spell Caster, and the Vessels' Cynic Dancer (hooded and shadow-black, hiding among the
C-souls; evolved: white skin and a new outfit).

Older note: Season 3 enemies, from the user's own reference art: the Spell Caster (hooded, button eyes, a wide grin, purple and
cyan) and the Vessel (white hair with pink tips, pointed ears, shark teeth, black outfit, striped stockings)."""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
import npcs
from npcs import clothes, shell, Fit, pack, paint_skin, face_img, darken, export, preview, TEX, MOD, CH, hair, fringe_locks, crown_spikes
from kit import C
from PIL import Image

# ------------------------------------------------------------------ the Spell Caster
P1, P2, P3 = '#2a1240', '#3c1a5c', '#5a2a86'          # purples: deep, robe, rim
CY, MG, BK = '#3ee8e8', '#e040e8', '#0c0a10'           # cyan glow, magenta gems, black

def caster_face(px, cm, dark):
    ring = (255, 70, 70) if dark else C(CY); inner = (40, 6, 8) if dark else (12, 60, 66)
    for cx, cy in ((9, 16), (22, 16)):                  # the button eyes, an X across each
        for y in range(cy - 4, cy + 5):
            for x in range(cx - 4, cx + 5):
                d2 = (x - cx) ** 2 + (y - cy) ** 2
                if d2 <= 16: px[x, y] = (ring if d2 >= 9 else inner) + (255,)
        for k in range(-2, 3):
            px[cx + k, cy + k] = ring + (255,); px[cx + k, cy - k] = ring + (255,)
    W = (250, 250, 250) if not dark else (220, 60, 60); K = (5, 4, 6)
    for x in range(6, 27):                               # the grin: wide, curling up at the ends
        lift = 1 if x < 9 or x > 23 else 0
        top, bot = 21 - lift, 25 - lift
        px[x, top] = K + (255,); px[x, bot] = K + (255,)
        for y in range(top + 1, bot): px[x, y] = W + (255,)
        z = (x - 6) % 4                                  # sharp teeth: a zigzag
        if z in (0, 3): px[x, top + 1] = K + (255,)
        if z in (1, 2): px[x, bot - 1] = K + (255,)
        px[x, top + 2] = K + (255,) if z == 0 else px[x, top + 2]

def caster_paint(part, face, x, y, W, H, o):
    if part == 'body':
        if face == 'front' and (abs(x - 3.5) + abs(y - 3) < 1.6): return MG          # the chest gem
        if face in ('front', 'back') and (x in (1, 6) and y > 2): return CY if face == 'front' else P1
        if y in (8, 9): return P3                                                     # the belt
        return '#1a1028'
    if part in ('rarm', 'larm'):
        if y >= 7: return '#1a1028' if (x + y) % 5 else CY                            # gauntlets, glowing seams
        return P2
    if part in ('rleg', 'lleg'):
        if y >= 7: return P1                                                          # armoured boots
        return BK
    return None

def caster_fit(f):
    # the hood: deep, with a lighter rim around the face and a peak flopping back
    f.add('head', [-4.85, 32.0, -4.85], [4.85, 33.0, 4.85], P2)
    for s in (1, -1): f.add('head', [s * 4.1 if s > 0 else -4.85, 24.2, -4.85], [4.85 if s > 0 else -4.1, 32.0, 4.85], P2)
    f.add('head', [-4.85, 24.2, 4.1], [4.85, 32.0, 4.85], P2)
    f.add('head', [-4.9, 31.0, -5.5], [4.9, 32.4, -4.5], P3)                              # rim over the brow
    for s in (1, -1): f.add('head', [s * 4.0 if s > 0 else -4.95, 24.2, -5.5], [4.95 if s > 0 else -4.0, 31.0, -4.5], P3)
    f.add('head', [-1.4, 33.0, 0.4], [1.4, 35.6, 3.2], P2, rot=[-35, 0, 0], origin=[0, 33.0, 1.6])   # the peak
    f.add('head', [-0.8, 35.0, 2.0], [0.8, 37.6, 3.6], P2, rot=[-60, 0, 0], origin=[0, 35.0, 2.6])
    # a mantle over the shoulders and down the chest, cyan-edged
    shell(f, 'body', [-4, 19.5, -2], [4, 24, 2], 0.55, P2, {'front': lambda x, y: CY if y > .9 else P2})
    f.add('body', [-1.0, 20.6, -2.9], [1.0, 22.6, -2.4], MG)                              # the clasp gem
    shell(f, 'body', [-4, 12, -2], [4, 19.5, 2], 0.3, '#1a1028', {'front': lambda x, y: CY if abs(x - .5) < .05 else '#1a1028'})
    f.add('body', [-4.5, 12.4, -2.5], [4.5, 13.6, 2.5], P3)                              # the belt
    f.add('body', [-0.9, 11.9, -2.95], [0.9, 14.1, -2.45], MG)                            # its gem
    # the cloak: down to the heels, its hem torn into points
    f.add('body', [-5.2, 2.0, 2.4], [5.2, 24.2, 3.2], P2, {'back': lambda x, y: CY if x < .04 or x > .96 else P2})
    for i, x0 in enumerate([-5.2, -3.1, -1.0, 1.1, 3.2]):
        f.add('body', [x0, 0.0 if i % 2 == 0 else 0.8, 2.5], [x0 + 2.0, 2.0, 3.1], P1)
    # gauntlets with claws, armoured boots
    f.both('right_arm', [3.5, 12.0, -2.5], [8.5, 16.6, 2.5], '#1a1028', {k: (lambda x, y: CY if y > .85 or abs(x - .5) < .06 else '#1a1028') for k in ('front', 'back', 'right', 'left')})
    for dx in (4.2, 5.4, 6.6, 7.8):
        f.both('right_arm', [dx - 0.25, 10.4, -2.2], [dx + 0.25, 12.0, -1.6], MG)
    f.both('right_leg', [-0.35, 0, -2.6], [4.35, 4.8, 2.35], P1, {'front': lambda x, y: P3 if y > .7 else P1})
    f.both('right_leg', [0.6, 4.4, -2.75], [3.4, 6.6, -2.35], P3)                         # knee guards

CH['spellcaster'] = dict(skin='#0c0a10', eyes='#3ee8e8', hair=['#2a1240', '#2a1240'], paint=caster_paint,
                         brow=[''], eye=[''], mouth=[''], face_extra=[caster_face], fit=caster_fit)

# ------------------------------------------------------------------ the Vessel
SKIN, HAIR, PINK, BLK, SIL = '#d6d6dc', '#f2f2f4', '#f08aa8', '#1a1a1e', '#b8b8c4'

def vessel_face(px, cm, dark):
    W = (250, 250, 250) if not dark else (220, 60, 60); K = (20, 14, 18)
    for x in range(10, 23):                               # the shark-tooth grin
        px[x, 24] = K + (255,); px[x, 27] = K + (255,)
        for y in (25, 26): px[x, y] = W + (255,)
        if (x - 10) % 3 == 0: px[x, 25] = K + (255,)
        if (x - 10) % 3 == 1: px[x, 26] = K + (255,)

def vessel_paint(part, face, x, y, W, H, o):
    if part == 'body':
        if face == 'front' and 2 <= x <= 5 and 2 <= y <= 4: return SIL                  # the emblem
        if y in (8, 9): return '#2a2a30'                                                   # belt
        return BLK
    if part in ('rarm', 'larm'):
        if y < 2: return SKIN                                                              # bare shoulders
        return BLK                                                                         # long gloves
    if part in ('rleg', 'lleg'):
        if y < 3: return BLK                                                               # shorts
        return '#3a3a42' if y % 2 else '#16161a'                                          # striped stockings
    return None

def vessel_fit(f):
    hair(f, HAIR, '#dcdce2', back_len=24.5, side_len=25.5, spikes=[([0.5, 33.2, -1], [0.4, 1, -0.6], 3.6, 0.7)],     # the stray strand on top
         locks=fringe_locks(6, 31.2, 7.8, 4.4, 1.5, 0.35) + [([s * 4.6, 30, z], [s * 0.35, -1, 0.1], 6.0, 1.6) for s in (1, -1) for z in (-2.5, 0, 2.5)])
    for s in (1, -1):                                                                                                # pink tips
        for z in (-2.5, 0, 2.5): f.add('head', [s * 4.6 - 0.75 + s * 0.5, 23.6, z - 0.7], [s * 4.6 + 0.75 + s * 0.5, 25.4, z + 0.7], PINK)
    for x in (-2.8, 0, 2.8): f.add('head', [x - 0.8, 24.0, 4.2], [x + 0.8, 26.0, 5.0], PINK)
    for s in (1, -1):                                                                                                # pointed ears
        f.add('head', [s * 4.0 if s > 0 else -5.6, 27.6, -0.6], [5.6 if s > 0 else -4.0, 28.6, 0.6], SKIN, rot=[0, 0, s * 25], origin=[s * 4.2, 28.0, 0])
    shell(f, 'body', [-4, 12, -2], [4, 24, 2], 0.25, BLK, {'front': lambda x, y: SIL if abs(x - .5) < .14 and .66 < y < .82 else BLK})
    f.add('body', [-2.2, 23.6, -2.4], [2.2, 25.0, 2.4], BLK)                                                       # high collar
    f.add('body', [-4.45, 12.6, -2.45], [4.45, 13.6, 2.45], '#2a2a30'); f.add('body', [-0.9, 12.4, -2.6], [0.9, 13.8, -2.35], SIL)   # belt + buckle
    f.add('body', [-4.4, 16.5, -2.4], [4.4, 17.3, 2.4], '#2a2a30', rot=[0, 0, -20], origin=[0, 17, 0])              # a second, slanted belt
    f.both('right_arm', [3.62, 12.0, -2.38], [7.38, 21.6, 2.38], BLK)                                              # long gloves
    f.both('right_arm', [3.5, 20.8, -2.5], [7.5, 21.6, 2.5], SIL)
    for dx in (4.2, 5.2, 6.2):                                                                                      # claws
        f.both('right_arm', [dx - 0.2, 10.9, -2.1], [dx + 0.2, 12.0, -1.6], '#f2f2f4')
    f.both('right_leg', [-0.35, 8.6, -2.4], [4.35, 12.3, 2.4], BLK)                                                # shorts
    f.both('right_leg', [-0.4, 7.2, -2.45], [4.4, 7.8, 2.45], '#2a2a30')                                           # thigh strap
    f.both('right_leg', [-0.32, 0, -2.6], [4.32, 1.8, 2.32], '#2a2a30')                                            # boots

CH['cynic_evolved'] = dict(skin=SKIN, eyes='#e8487a', hair=[HAIR, '#dcdce2'], paint=vessel_paint,
                    eye=['..kkkkkk.', 'kkkkkkkkk', '.wwwwwww.', '.wwwopww.', '.wwwwwww.', '..wwwww..', '..kkkkk..'],
                    brow=['.kkkk....'], mouth=[''], face_extra=[vessel_face], fit=vessel_fit)

# ------------------------------------------------------------------ the Cynic Dancer (a Vessel, disguised as a C-soul)
MR, MR2, MR3, HB, RD = '#4a1028', '#36091c', '#6a1a3a', '#3a2620', '#e01010'

def cynic_face(px, cm, dark):
    R = (230, 10, 10)
    for x in range(14, 18): px[x, 24] = R + (255,)          # a small mouth, a little ^ in the middle
    px[15, 23] = R + (255,); px[16, 23] = R + (255,)
    for y in range(6, 12):                                    # messy bangs dropping over the face
        for x in range(2, 30):
            if y < 9 or (x * 7 + y * 3) % 5 < 2 and y < 12: px[x, y] = (20, 14, 14, 255)

def cynic_paint(part, face, x, y, W, H, o):
    if part == 'body':
        if y >= 9: return '#0a0a0c'
        return MR
    if part in ('rarm', 'larm'):
        if y >= 8: return MR2 if (x + y) % 3 else '#8a1a2a'                 # clawed hands, red-lined
        return MR
    if part in ('rleg', 'lleg'):
        if y >= 9: return '#140c0e' if y != 10 else '#6a1420'                 # boots, a red band
        return '#0a0a0c'
    return None

def cynic_fit(f):
    # messy dark hair under the hood, and a wavy ponytail out of the back of it
    f.add('head', [-4.55, 29.0, -4.55], [4.55, 32.0, 4.55], '#161012')
    for i, (z, y) in enumerate(((4.8, 29.5), (6.0, 28.4), (7.0, 27.0), (7.4, 25.4), (7.0, 23.8))):
        f.add('head', [-1.2 - (i % 2) * .4, y - 1.4, z - .8], [1.2 + (i % 2) * .4, y + .2, z + .8], HB if i % 2 else '#2c1c18')
    # the hood: maroon, a lighter rim, black horns poking up through it
    f.add('head', [-4.85, 32.0, -4.85], [4.85, 33.1, 4.85], MR)
    for s in (1, -1): f.add('head', [4.1 if s > 0 else -4.85, 23.6, -4.85], [4.85 if s > 0 else -4.1, 32.0, 4.85], MR)
    f.add('head', [-4.85, 24.0, 4.1], [4.85, 32.0, 4.85], MR)
    f.add('head', [-4.9, 30.9, -5.4], [4.9, 32.3, -4.6], MR3)
    for s in (1, -1): f.add('head', [3.9 if s > 0 else -4.95, 23.6, -5.4], [4.95 if s > 0 else -3.9, 30.9, -4.6], MR3)
    for s in (1, -1):
        f.add('head', [s * 2.6 - 1.0, 32.6, -1.6], [s * 2.6 + 1.0, 35.0, 0.4], '#141416', rot=[0, 0, -s * 15], origin=[s * 2.6, 33, 0])
        f.add('head', [s * 3.0 - 0.55, 34.6, -1.1], [s * 3.0 + 0.55, 36.8, -0.1], '#141416', rot=[0, 0, -s * 25], origin=[s * 3.0, 34.6, 0])
    # a hood-cape over the shoulders, its hem in points; a tunic below with a torn hem
    shell(f, 'body', [-4, 19.0, -2], [4, 24, 2], 0.6, MR, {'front': lambda x, y: MR3 if y < .12 else MR})
    for i, x0 in enumerate([-4.6, -2.3, 0.0, 2.3]):
        f.add('body', [x0, 17.6 + (i % 2) * .7, -2.65], [x0 + 2.3, 19.0, -2.35], MR)
    shell(f, 'body', [-4, 12.5, -2], [4, 19, 2], 0.35, MR2)
    for i, x0 in enumerate([-4.4, -2.2, 0.0, 2.2]):
        f.add('body', [x0, 10.6 + (i % 2) * 0.8, -2.4], [x0 + 2.2, 12.5, -2.2], MR2)
        f.add('body', [x0, 10.6 + ((i + 1) % 2) * 0.8, 2.2], [x0 + 2.2, 12.5, 2.4], MR2)
    # the long torn cloak behind
    f.add('body', [-4.8, 4.0, 2.3], [4.8, 23.8, 3.0], MR2, {'back': lambda x, y: MR if (x * 9 + y * 4) % 1 < .5 else MR2})
    for i, x0 in enumerate([-4.8, -2.9, -1.0, 0.9, 2.8]):
        f.add('body', [x0, 1.4 + (i % 3) * 1.2, 2.35], [x0 + 1.9, 4.0, 2.95], MR2)
    # sleeves, and claws
    f.both('right_arm', [3.62, 14.0, -2.38], [7.38, 22.0, 2.38], MR)
    f.both('right_arm', [3.5, 13.4, -2.5], [7.5, 14.4, 2.5], MR2)
    for dx in (4.0, 5.1, 6.2, 7.2):
        f.both('right_arm', [dx - 0.22, 9.6, -2.1], [dx + 0.22, 12.0, -1.6], '#1a0a0e')
    f.both('right_leg', [-0.35, 0, -2.6], [4.35, 2.4, 2.35], '#140c0e')
    f.both('right_leg', [-0.4, 1.4, -2.65], [4.4, 1.9, 2.4], '#7a1424')

CH['cynic'] = dict(skin='#0a0a0c', eyes='#e01010', hair=['#161012', '#161012'], paint=cynic_paint,
                   eye=['.........', '.........', '..oooooo.', '.ooooooo.', '..ooooo..', '.........', '.........'],
                   brow=[''], mouth=[''], face_extra=[cynic_face], fit=cynic_fit)

# ------------------------------------------------------------------ the Ancient Swordstress (a C-soul with a human shape)
G1, G2, G3, GS, GB, GL = '#1e3a16', '#2c5020', '#3e6a2c', '#14260e', '#7ee06a', '#b8ff9a'     # hood greens, stitches, blade
WH, WG = '#f4f4f6', '#bcbcc4'

def sword_face(px, cm, dark):
    G = (120, 230, 90); D = (20, 40, 16)
    for cx, cy in ((9, 17), (22, 17)):                  # big ring eyes
        for y in range(cy - 5, cy + 6):
            for x in range(cx - 5, cx + 6):
                d2 = (x - cx) ** 2 + (y - cy) ** 2
                if d2 <= 25: px[x, y] = (G if d2 >= 15 else D) + (255,)
    px[15, 22] = (90, 50, 40, 255); px[16, 22] = (90, 50, 40, 255)      # the little nose
    for y in range(6, 12):                               # the blunt white fringe
        for x in range(2, 30): px[x, y] = ((244, 244, 246) if y < 10 else (200, 200, 208)) + (255,)
    for x in (6, 11, 20, 25): px[x, 10] = (150, 150, 160, 255); px[x, 11] = (150, 150, 160, 255)

def sword_paint(part, face, x, y, W, H, o):
    if part == 'body': return G2 if y < 8 else '#0c0c10'
    if part in ('rarm', 'larm'): return '#0c0c10' if y >= 9 else G2
    if part in ('rleg', 'lleg'): return '#0c0c10'
    return None

def sword_fit(f):
    # the white bob: straight sides to the jaw, grey toward the ends
    hair(f, WH, WG, back_len=23.4, side_len=23.4, fringe=True)
    for s in (1, -1): f.add('head', [s * 4.7 - .4, 22.6, -4.8], [s * 4.7 + .4, 27.0, 0.0], WG)
    # the hood with cat ears, stitched
    f.add('head', [-5.0, 32.2, -5.0], [5.0, 33.4, 5.0], G1)
    for s in (1, -1): f.add('head', [4.2 if s > 0 else -5.0, 24.0, -5.0], [5.0 if s > 0 else -4.2, 32.2, 5.0], G1,
                            {('right' if s < 0 else 'left'): lambda x, y: GS if abs(y - .5) < .05 or abs(x - .4) < .04 else G1})
    f.add('head', [-5.0, 23.6, 4.2], [5.0, 32.2, 5.0], G1)
    f.add('head', [-5.05, 31.2, -5.6], [5.05, 32.6, -4.7], G2)
    for s in (1, -1):
        f.add('head', [s * 3.0 - 1.4, 32.8, -2.0], [s * 3.0 + 1.4, 36.4, 1.0], G2, {'front': lambda x, y: GS if abs(x - .5) < .08 else G2},
              rot=[0, 0, -s * 12], origin=[s * 3.0, 33, 0])
        f.add('head', [s * 3.2 - 0.7, 36.0, -1.4], [s * 3.2 + 0.7, 37.8, 0.4], G2, rot=[0, 0, -s * 20], origin=[s * 3.2, 36, 0])
    # the scarf, the coat with its long skirt, the cape
    shell(f, 'body', [-4, 21.6, -2], [4, 24.6, 2], 0.8, G2, {'front': lambda x, y: GS if abs(y - .5) < .08 else G2})
    f.add('body', [0.6, 15.0, -3.1], [2.4, 21.6, -2.6], G2)                                # its loose end
    shell(f, 'body', [-4, 12, -2], [4, 21.6, 2], 0.35, G1, {'front': lambda x, y: '#0c0c10' if abs(x - .5) < .12 else G1})
    f.add('body', [-4.5, 12.6, -2.5], [4.5, 13.8, 2.5], '#0c0c10')                         # a black sash
    for i, x0 in enumerate([-4.6, -2.3, 0.0, 2.3]):                                         # the skirt, in points
        f.add('body', [x0, 6.4 + (i % 2) * 1.2, -2.6], [x0 + 2.3, 12.6, -2.2], G1 if i % 2 else G2)
        f.add('body', [x0, 6.4 + ((i + 1) % 2) * 1.2, 2.2], [x0 + 2.3, 12.6, 2.6], G1)
        f.add('body', [-4.7 if i < 2 else 4.2, 6.6 + (i % 2), -2.4 + (i % 2) * 2.4], [-4.2 if i < 2 else 4.7, 12.6, 0.0 + (i % 2) * 2.4], G2)
    f.add('body', [-5.0, 2.0, 2.7], [5.0, 23.6, 3.3], G1, {'back': lambda x, y: GB if x < .04 or x > .96 else G1})
    for i, x0 in enumerate([-5.0, -2.5, 0.0, 2.5]): f.add('body', [x0, 0.6 + (i % 2) * 1.2, 2.75], [x0 + 2.5, 2.0, 3.25], G1)
    # sleeves, white fur cuffs, black gloves
    f.both('right_arm', [3.6, 15.0, -2.4], [7.4, 22.0, 2.4], G2)
    f.both('right_arm', [3.2, 13.6, -2.8], [7.8, 15.6, 2.8], WH, {k: (lambda x, y: WH if (int(x * 7) + int(y * 3)) % 3 else '#dcdce4') for k in ('front', 'back', 'right', 'left')})
    f.both('right_leg', [-0.35, 0, -2.5], [4.35, 3.0, 2.35], '#0c0c10')
    # the giant sword, in the right hand: its blade out in front, an arrow-tipped hilt behind
    A = lambda frm, to, c, faces=None, **k: f.add('right_arm', frm, to, c, faces, **k)
    A([5.2, 10.6, -1.8], [6.4, 12.0, 4.0], '#2a4a1e')                                       # grip
    A([4.4, 10.2, -2.8], [7.2, 12.4, -1.8], GB)                                             # guard: a crystal knot
    for s in (1, -1): A([5.6 + s * 1.6 - .5, 10.6, -3.2], [5.6 + s * 1.6 + .5, 12.0, -1.4], GL, rot=[0, s * 25, 0], origin=[5.6, 11.3, -2.3])
    A([4.0, 10.1, -24.0], [7.6, 12.5, -2.8], GB, {'top': lambda x, y: GL if abs(x - .5) < .1 else GB, 'bottom': lambda x, y: GL if abs(x - .5) < .1 else GB})
    A([4.6, 10.4, -26.0], [7.0, 12.2, -24.0], GB)                                            # the point
    A([5.2, 10.7, -27.4], [6.4, 11.9, -26.0], GL)
    A([5.5, 10.9, 4.0], [6.1, 11.7, 8.0], '#2a4a1e')                                        # the shaft behind
    A([4.6, 10.6, 7.6], [7.0, 12.0, 9.0], GB); A([5.3, 10.8, 9.0], [6.3, 11.8, 10.4], GL)  # its arrow tip

CH['swordstress'] = dict(skin='#0a0a0c', eyes='#78e65a', hair=[WH, WG], paint=sword_paint,
                         brow=[''], eye=[''], mouth=[''], face_extra=[sword_face], fit=sword_fit)

if __name__ == '__main__':
    sheets = []
    for key in (sys.argv[1:] or ('spellcaster', 'cynic', 'cynic_evolved', 'swordstress')):
        spec = CH[key]
        fit = Fit(); spec['fit'](fit)
        atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); pack(fit, atlas, region=(0, 0, 256, 256))
        skin = paint_skin(spec); face = face_img(spec)
        skin.save(TEX + key + '.png'); atlas.save(TEX + key + '_details.png'); face.save(TEX + key + '_face.png')
        darken(skin).save(TEX + key + '_dark.png'); darken(atlas).save(TEX + key + '_dark_details.png'); face_img(spec, dark=True).save(TEX + key + '_face_dark.png')
        mj = export(fit, MOD + key + '.json')
        sheets.append(preview(mj, skin, atlas, face, yaws=(0, 35, 180), scale=6)); print(key, len(fit.cubes))
        face.resize((256, 256), 0).save('/tmp/claude-0/-home-claude/b2fe60cb-fd40-5a94-8ee2-7d1a501fd998/scratchpad/outfit/' + key + '_face.png')
    tw = max(s.width for s in sheets); th = max(s.height for s in sheets)
    big = Image.new('RGB', (tw, th * len(sheets)), (30, 30, 36))
    for i, sh in enumerate(sheets): big.paste(sh, (0, i * th))
    big.save('/tmp/claude-0/-home-claude/b2fe60cb-fd40-5a94-8ee2-7d1a501fd998/scratchpad/outfit/foes.png')
