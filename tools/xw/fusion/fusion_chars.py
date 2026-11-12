"""Fusion characters (original designs): fully new looks - new hair, new outfits - for the Dragon Ball fusions.
Rhalix + Mikiko (two drafts) and Mikuri = Mikiko + Yuri (two drafts). Built with the Sky Patrol toolkit."""
import sys, math, json
sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/sp')
import sp_chars as S
from sp_chars import lock, plate, hair, shell, core, Adapter, U
from PIL import Image

def rm_b_hair(f):
    # violet hair in a high spiky ponytail with orange-tipped ends, a red band at the forehead with long tails
    hair(f, RB1, RB2, back_len=25.0, side_len=27.5,
         locks=[([x, 32.0, -4.7], [x * 0.18, -0.7, -0.5], 2.8, 1.8) for x in (-3.0, -1.0, 1.0, 3.0)]
         + [([s * 4.5, 30.4, z], [s * 0.2, -1, 0.0], 3.6, 1.8) for s in (1, -1) for z in (-2.8, 0.6)])
    f.add('head', [-1.4, 32.8, 1.6], [1.4, 34.4, 4.4], RB2)                                                   # the tie
    for i, (dx, dy, dz) in enumerate(((0, 1, 0.5), (0.4, 0.8, 0.8), (-0.4, 0.8, 0.8), (0, 0.4, 1))):
        lock(f, [dx * 1.5, 34.2, 3.2], [dx, dy, dz], 6.5 - i * 0.6, 2.2, (RB1, RB2, RB3), thick=1.4)         # spiky ponytail
    f.add('head', [-4.75, 29.6, -4.8], [4.75, 30.6, 4.75], '#c41e30')                                         # red band
    for s in (1, -1):
        lock(f, [s * 1.0, 30.0, 4.7], [s * 0.3, -1, 0.5], 6.0, 1.0, ('#c41e30', '#a01828', '#e04050'), thick=0.3)

# ====================================================================== RHALIX + MIKIKO, draft A: "storm coat"
RA_SKIN, RA_EYE = '#7a4e36', '#c8306e'
RB1, RB2, RB3 = '#5a2a8a', '#2a1440', '#f07a30'
RH1, RH2, RH3 = '#9a1830', '#4a1e6a', '#e8603a'            # crimson top, violet base, ember tips
VIO, VIOD, ORA, BLK, GOLD = '#3e2066', '#2a1446', '#f08a2a', '#16141c', '#e8c040'

def rm_a_paint(part, face, x, y, W, H, outer):
    if part == 'body': return BLK
    if part in ('rarm', 'larm'): return VIO if y < 9 else BLK
    if part in ('rleg', 'lleg'): return '#24202c' if y < 9 else VIOD
    return None

def rm_a(f):
    rm_b_hair(f)                                                                                             # (Sky Ronin's hair, as picked)
    # black bodysuit with a gold diamond
    shell(f, 'body', [-4, 12.0, -2], [4, 24, 2], 0.26, BLK)
    plate(f, 'body', ['...k...', '..kkk..', '.kk.kk.', 'kk...kk', '.kk.kk.', '..kkk..', '...k...'], 0.0, 22.6, -2.36, 0.36, GOLD)
    # the storm coat: long, violet, high collar lined orange, open front, flame hem
    for sx in (1, -1):
        x0, x1 = (1.6, 4.6) if sx > 0 else (-4.6, -1.6)
        f.add('body', [x0, 3.0, -2.62], [x1, 24.4, -2.36], VIO, {'front': lambda x, y: ORA if y < 0.06 else VIO})
        xi = 1.6 if sx > 0 else -2.0
        f.add('body', [xi, 3.0, -2.75], [xi + 0.4, 24.4, -2.6], ORA, shade=False)                          # orange edge
        for k in range(4):                                                                                  # flame licks up from the hem
            fx = sx * (2.0 + k * 0.7)
            f.add('body', [fx - 0.18, 3.0, -2.74], [fx + 0.18, 4.6 + (k % 2) * 1.4, -2.62], ORA, shade=False)
    shell(f, 'body', [-4, 3.0, -2], [4, 24.2, 2], 0.5, VIO, {'front': lambda x, y: None, 'back': lambda x, y: ORA if y > 0.93 else VIO})
    f.add('body', [-4.4, 23.6, -2.9], [4.4, 27.2, 2.9], VIOD, {'front': lambda x, y: None, 'top': lambda x, y: None,
                                                                'right': lambda x, y: ORA if x > .5 else VIOD, 'left': lambda x, y: ORA if x < .5 else VIOD})   # high collar
    f.add('body', [-4.6, 11.4, -2.95], [4.6, 12.6, 2.95], '#c41e30')                                         # red sash
    f.add('body', [-4.7, 6.4, -2.4], [-3.6, 12.0, -1.2], '#c41e30')                                          # sash end
    # an orange armoured shoulder (his right), gold-cuffed black gloves
    f.add('right_arm', [3.2, 21.6, -2.9], [8.9, 24.9, 2.9], ORA, {'top': lambda x, y: '#ffb050' if y < .5 else ORA})
    f.add('right_arm', [3.2, 21.4, -2.95], [8.9, 21.9, 2.95], GOLD)
    f.both('right_arm', [3.45, 12.0, -2.55], [8.55, 24.4, 2.55], VIO, {'bottom': BLK})
    f.both('right_arm', [3.4, 11.6, -2.6], [8.6, 15.0, 2.6], BLK)
    f.both('right_arm', [3.35, 14.6, -2.65], [8.65, 15.4, 2.65], GOLD)
    # dark trousers, orange knee guards, violet boots with gold toes
    f.both('right_leg', [-0.35, 4.0, -2.35], [4.35, 12.3, 2.35], '#24202c')
    f.both('right_leg', [0.4, 6.4, -2.75], [3.6, 8.4, -2.35], ORA)
    f.both('right_leg', [-0.45, 0.0, -2.6], [4.45, 4.4, 2.45], VIOD)
    f.both('right_leg', [0.1, 0.0, -3.2], [3.9, 1.5, -2.6], VIOD)
    f.both('right_leg', [0.6, 1.5, -3.25], [3.4, 1.9, -2.9], GOLD)

def rm_marks(px, cm, dark):
    for y in range(20, 23): px[7, y] = (240, 120, 40, 255); px[24, y] = (150, 60, 220, 255)   # an ember mark under one eye, a violet one under the other

RM_A = dict(skin=RA_SKIN, eyes=RA_EYE, hair=[RB1, RB2], paint=rm_a_paint,
            brow=['.........', '..kkkkkk.', 'kk.......'],
            eye=['..kkkkkk.', 'kkkkkkkkk', '.wOOooOw.', '.wWOpoOw.', '.wOoopOw.', '..wOOOw..', '..kkkkk..'],
            mouth=['.kkkk.'], mouth_x=13, mouth_y=25, face_extra=[rm_marks], wing=('falcon', '#3a1a5a'), fit=rm_a)

# ====================================================================== RHALIX + MIKIKO, draft B: "sky ronin"
RB1, RB2, RB3 = '#5a2a8a', '#2a1440', '#f07a30'
def rm_b_paint(part, face, x, y, W, H, outer):
    if part == 'body': return '#e8e2d6'
    if part in ('rarm', 'larm'): return None if y >= 4 else ORA
    if part in ('rleg', 'lleg'): return BLK if y < 10 else '#3a2a1e'
    return None

def rm_b(f):
    rm_b_hair(f)
    # a wrapped cross-over top (cream with an orange panel and violet edges), wide black trousers, a violet obi
    shell(f, 'body', [-4, 12.0, -2], [4, 24, 2], 0.28, '#e8e2d6')
    for k in range(10):
        t = k / 9
        x = 3.6 - 6.8 * t; y = 24.0 - 9.0 * t
        f.add('body', [x - 0.6, y - 0.6, -2.42], [x + 0.6, y + 0.6, -2.3], ORA)                               # orange cross-over panel
        f.add('body', [x - 0.25 + 0.7, y - 0.6, -2.5], [x + 0.25 + 0.7, y + 0.6, -2.36], VIO, shade=False)   # violet edge
    f.add('body', [-4.6, 11.6, -2.7], [4.6, 14.4, 2.7], VIO)                                                   # obi
    f.add('body', [1.2, 11.0, -2.95], [3.6, 13.6, -2.65], ORA)                                                 # knot
    f.add('body', [-4.0, 21.0, 2.3], [4.0, 24.4, 2.6], '#e8e2d6', {'back': lambda x, y: plate_back(x, y)})
    # one long sleeve (his right, orange), the other arm bare with a black bracer
    f.add('right_arm', [3.45, 15.0, -2.55], [8.55, 24.4, 2.55], ORA, {'bottom': '#c06a1a'})
    f.add('right_arm', [3.3, 14.6, -2.7], [8.7, 15.6, 2.7], VIO)
    f.add('left_arm', [-8.55, 12.0, -2.55], [-3.45, 16.4, 2.55], BLK)
    f.add('left_arm', [-8.6, 15.0, -2.6], [-3.4, 15.6, 2.6], GOLD)
    f.both('right_leg', [-0.7, 3.6, -2.7], [4.7, 12.3, 2.7], BLK)                                              # wide trousers
    f.both('right_leg', [-0.4, 0.0, -2.5], [4.4, 3.6, 2.4], '#3a2a1e')
    f.both('right_leg', [0.1, 0.0, -3.1], [3.9, 1.2, -2.5], '#3a2a1e')
    f.both('right_leg', [-0.75, 3.4, -2.75], [4.75, 4.0, 2.75], ORA)

def plate_back(x, y): return VIO if 0.35 < x < 0.65 else '#e8e2d6'

RM_B = dict(skin=RA_SKIN, eyes='#e0702a', hair=[RB1, RB2], paint=rm_b_paint,
            brow=['.........', '.kkkkkk..', 'k........'],
            eye=['..kkkkkk.', 'kkkkkkkkk', '.wOOooOw.', '.wWOpoOw.', '.wOoopOw.', '..wOOOw..', '..kkkkk..'],
            mouth=['k....k', '.kkkk.'], mouth_x=13, mouth_y=25, face_extra=[], wing=('tattered', '#8a1a2a'), fit=rm_b)

# ====================================================================== MIKURI (Mikiko + Yuri), draft A: "lab fox"
MK_SKIN, MK_EYE = '#c8946c', '#3ab84a'
MH1, MH2, MH3 = '#121212', '#1c1c1e', '#c42a2a'
GRN, GRN2, WHT, OR2 = '#3ad04a', '#1e8a2a', '#ecebf0', '#f08a2a'

def mk_a_paint(part, face, x, y, W, H, outer):
    if part == 'body': return '#1a1a1e'
    if part in ('rarm', 'larm'): return OR2 if y < 9 else '#1a1a1e'
    if part in ('rleg', 'lleg'):
        if y >= 10: return OR2
        if face in ('right', 'left') and x == 1: return GRN
        return '#2a2a30'
    return None

def mk_a(f):
    # shoulder-length messy black hair, a red streak, green-dyed ends, a small side tail with a green ribbon
    hair(f, MH1, MH2, back_len=24.0, side_len=25.0,
         spikes=[([x, 32.6, z], [x * 0.2, 0.7, 0.6], 2.6, 2.0) for (x, z) in ((-2.4, -1.0), (0.4, -1.6), (2.8, -0.6), (0, 1.6))],
         locks=[([x, 31.9, -4.75], [x * 0.12, -1, -0.2], 3.8, 1.9) for x in (-3.2, -1.2, 1.0, 3.0)]
         + [([s * 4.55, 30.6, z], [s * 0.15, -1, 0.1], 6.6, 2.0) for s in (1, -1) for z in (-2.6, 0.2, 3.0)]
         + [([x, 30.4, 4.65], [0, -1, 0.25], 7.0, 2.2) for x in (-2.6, 0.0, 2.6)])
    for (x, z) in ((-4.85, -2.6), (4.65, -2.6), (-4.85, 0.2), (4.65, 0.2)):
        f.add('head', [x, 24.2, z - 0.8], [x + 0.2, 25.4, z + 0.8], GRN2)                                     # green ends
    f.add('head', [-1.8, 31.0, -4.95], [-1.0, 32.6, -4.75], MH3)                                              # red streak in the fringe
    f.add('head', [-6.0, 29.2, 1.2], [-4.4, 31.6, 3.2], MH2)                                                  # side tail (left)
    lock(f, [-5.6, 29.8, 2.2], [-0.5, -1, 0.3], 3.4, 1.6, (MH2, MH1, GRN2), thick=1.3)
    f.add('head', [-5.4, 30.8, 1.0], [-4.8, 31.8, 3.4], GRN)                                                   # green ribbon
    # a tech visor: one green lens over the right eye on a red band (goggles and glasses in one)
    f.add('head', [-4.8, 29.6, -4.85], [4.8, 30.2, 4.8], '#c41e30')
    f.add('head', [0.6, 27.4, -5.05], [3.8, 29.4, -4.75], GRN, shade=False)
    f.add('head', [0.4, 27.2, -4.95], [4.0, 29.6, -4.85], '#2a2a30')
    # a black turtleneck with a fox-and-gear badge, a cropped white lab jacket with orange sleeves and green trim
    shell(f, 'body', [-4, 12.0, -2], [4, 24, 2], 0.26, '#1a1a1e')
    f.add('body', [-2.6, 23.6, -2.5], [2.6, 25.0, 2.5], '#1a1a1e')                                             # turtleneck
    plate(f, 'body', ['k.....k', 'kk...kk', 'kkkkkkk', 'k.kkk.k', '.kkkkk.', '..kkk..'], 0.0, 21.6, -2.36, 0.34, OR2)   # little fox face
    for sx in (1, -1):
        x0, x1 = (2.0, 4.6) if sx > 0 else (-4.6, -2.0)
        f.add('body', [x0, 16.0, -2.62], [x1, 24.4, -2.36], WHT)
        xi = 2.0 if sx > 0 else -2.35
        f.add('body', [xi, 16.0, -2.74], [xi + 0.35, 24.4, -2.6], GRN, shade=False)
    shell(f, 'body', [-4, 16.0, -2], [4, 24.2, 2], 0.45, WHT, {'front': lambda x, y: None, 'back': lambda x, y: GRN if y > .9 else WHT})
    f.both('right_arm', [3.45, 13.0, -2.55], [8.55, 24.4, 2.55], OR2, {'bottom': '#c06a1a'})
    f.both('right_arm', [3.4, 22.2, -2.6], [8.6, 24.6, 2.6], WHT)
    f.both('right_arm', [3.35, 12.6, -2.65], [8.65, 13.6, 2.65], GRN)
    f.both('right_arm', [3.5, 12.0, -2.5], [8.5, 12.8, 2.5], '#1a1a1e')
    # cargo trousers with a green side stripe, orange high-tops
    f.both('right_leg', [-0.35, 3.6, -2.35], [4.35, 12.3, 2.35], '#2a2a30')
    f.both('right_leg', [4.3, 4.0, -0.25], [4.5, 12.0, 0.25], GRN, shade=False)
    f.both('right_leg', [4.3, 6.0, -1.5], [4.85, 9.0, 1.5], '#36363e')
    f.both('right_leg', [-0.45, 0.0, -2.6], [4.45, 3.8, 2.45], OR2)
    f.both('right_leg', [0.1, 0.0, -3.2], [3.9, 1.4, -2.6], OR2)
    f.both('right_leg', [-0.5, 0.0, -3.3], [4.5, 0.5, 2.5], WHT)

def mk_marks(px, cm, dark):
    G = (58, 200, 74, 255)
    for (x, y) in ((8, 22), (7, 23), (9, 23), (8, 24)): px[x, y] = G                                          # a small green mark under the left eye

MK_A = dict(skin=MK_SKIN, eyes=MK_EYE, hair=[MH1, MH2], paint=mk_a_paint,
            brow=['.........', '..kkkkkk.', 'kk.......'],
            eye=['..kkkkkk.', 'kkkkkkkkk', '.wOOooOw.', '.wWOpoOw.', '.wOoopOw.', '..wOOOw..', '..kkkkk..'],
            mouth=['k....k', '.kkkk.'], mouth_x=13, mouth_y=25, face_extra=[mk_marks], wing=None, fit=mk_a)

# ====================================================================== MIKURI, draft B: "neon medic"
def mk_b_paint(part, face, x, y, W, H, outer):
    if part == 'body': return OR2
    if part in ('rarm', 'larm'): return OR2 if y < 10 else '#141418'
    if part in ('rleg', 'lleg'): return '#141418' if y < 9 else '#8a8a92'
    return None

def mk_b(f):
    # a long fringe swept over one eye, short at the back and sides, black with green highlights, red headband
    hair(f, MH1, MH2, back_len=27.0, side_len=28.5,
         locks=[([x, 32.0, -4.75], [-0.55, -0.9, -0.25], ln, 2.2) for (x, ln) in ((3.6, 5.6), (2.0, 6.2), (0.4, 6.0), (-1.2, 5.0))]
         + [([s * 4.55, 31.0, z], [s * 0.1, -1, 0.0], 2.6, 1.8) for s in (1, -1) for z in (-1.0, 2.0)])
    for x in (2.0, 0.4):
        f.add('head', [x - 0.3, 28.0, -5.2], [x + 0.3, 29.6, -4.95], GRN)                                    # green highlights in the fringe
    f.add('head', [-4.75, 31.0, -4.6], [4.75, 31.8, 4.75], '#c41e30')                                         # red headband
    # a long orange hooded coat with white side panels, green gem buttons, black shorts over leggings, grey boots
    shell(f, 'body', [-4, 12.0, -2], [4, 24, 2], 0.26, '#141418')
    for sx in (1, -1):
        x0, x1 = (0.6, 4.6) if sx > 0 else (-4.6, -0.6)
        f.add('body', [x0, 5.0, -2.62], [x1, 24.4, -2.36], OR2, {'front': lambda x, y: WHT if (x > .75 if sx > 0 else x < .25) else OR2})
        for yy in (21.4, 18.8, 16.2):
            f.add('body', [sx * 1.2 - 0.4, yy, -2.74], [sx * 1.2 + 0.4, yy + 0.8, -2.6], '#5ae86a', shade=False)
    shell(f, 'body', [-4, 5.0, -2], [4, 24.2, 2], 0.45, OR2, {'front': lambda x, y: None})
    f.add('body', [-3.8, 20.4, 2.4], [3.8, 24.8, 4.4], OR2, {'back': lambda x, y: WHT if y > .75 else OR2})   # hood down
    f.add('body', [-3.8, 24.3, 2.3], [3.8, 24.9, 4.5], GRN)
    f.both('right_arm', [3.45, 12.4, -2.55], [8.55, 24.4, 2.55], OR2, {'bottom': '#c06a1a'})
    f.both('right_arm', [3.4, 12.0, -2.6], [8.6, 13.0, 2.6], WHT)
    f.both('right_leg', [-0.4, 9.4, -2.4], [4.4, 12.3, 2.4], '#141418')
    f.both('right_leg', [-0.3, 3.8, -2.3], [4.3, 9.4, 2.3], '#22222a')
    f.both('right_leg', [-0.45, 0.0, -2.6], [4.45, 4.2, 2.45], '#8a8a92')
    f.both('right_leg', [0.1, 0.0, -3.2], [3.9, 1.4, -2.6], '#8a8a92')
    f.both('right_leg', [-0.5, 0.0, -3.3], [4.5, 0.5, 2.5], GRN2)

MK_B = dict(MK_A, paint=mk_b_paint, fit=mk_b, eyes='#d8402a', mouth=['.kkkk.'])

CHARS = {'fusion_rm_a': RM_A, 'fusion_rm_b': RM_B, 'fusion_mikuri_a': MK_A, 'fusion_mikuri_b': MK_B}
S.CHARS.update(CHARS)

if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else '/tmp/fusion_preview'
    for key in CHARS:
        mj, fit, atlas, skin = S.export(key, f'/tmp/{key}.json')
        fc = S.face(CHARS[key])
        t = U.preview(mj, skin, atlas, fc, yaws=(25, 0, 200), scale=8, wide=44, tall=50, top=16)
        t.save(f'{out}_{key}.png'); print(key, len(fit.cubes), t.size)
