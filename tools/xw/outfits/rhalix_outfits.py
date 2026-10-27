import sys
sys.path.insert(0, '/home/claude/xw/outfits')
from kit import *
from PIL import Image

face = Image.open(CHAR + 'rhalix_face.png').convert('RGBA')
SK = face.getpixel((15, 26))[:3]
SKD = mul(SK, .85)

def blazer(f, BL, BLD, sleeve_to):
    """Open jacket over the shirt: back, sides, front panels, collar, lapels, sleeves down to `sleeve_to`."""
    f.add('body', [-4.35, 11.4, 2.0], [4.35, 24.3, 2.4], BL)
    f.add('body', [4.0, 11.4, -2.4], [4.35, 24.3, 2.4], BL)
    f.add('body', [-4.35, 11.4, -2.4], [-4.0, 24.3, 2.4], BL)
    f.add('body', [2.0, 11.4, -2.4], [4.35, 24.3, -2.0], BL)
    f.add('body', [-4.35, 11.4, -2.4], [-2.0, 24.3, -2.0], BL)
    f.add('body', [-4.35, 24.0, -2.4], [4.35, 24.3, 2.4], BL)
    f.add('body', [-3.2, 23.6, 1.7], [3.2, 25.3, 2.7], BLD)
    f.add('body', [1.5, 18.6, -2.62], [2.4, 24.3, -2.35], BLD)
    f.add('body', [-2.4, 18.6, -2.62], [-1.5, 24.3, -2.35], BLD)
    f.both('right_arm', [3.65, sleeve_to, -2.38], [8.38, 24.4, 2.38], BL)

# ======================================================================= ORIGINS 1 (kid: purple suit, glasses)
R1 = dict(P='#5a30c8', PD='#4422a0', PL='#7a52e6', LAV='#c4b0f4', RD='#c42028', GR='#6a6a72', BK='#1a1a20')

def r1_skin(part, face_, x, y, W, H, outer):
    P = R1
    if part == 'body':
        if face_ == 'front':
            if y >= 9 and 2 <= x <= 5 and (abs(x - 3.5) < (12 - y) * 0.9): return P['RD'] if abs(x - 3.5) < (12 - y) * 0.9 - 0.8 else P['GR']
            if y == 8: return P['LAV']
            if y == 0 and x in (3, 4): return P['LAV']
            return P['P'] if x not in (0, 7) else P['PD']
        if face_ == 'top': return SK if 3 <= x <= 4 and y >= 1 else P['P']
        if y == 8: return P['LAV']
        return P['P'] if face_ != 'back' else P['PD'] if x in (3, 4) and y < 8 else P['P']
    if part in ('rarm', 'larm'):
        if face_ == 'bottom': return SK
        if face_ == 'top': return P['P']
        if y == 11: return SK
        if y == 10: return P['LAV']
        if part == 'larm' and face_ == outer and 1 <= y <= 4:
            ring = (x in (0, 3) and y in (2, 3)) or (y in (1, 4) and x in (1, 2))
            return P['LAV'] if ring else P['P']
        return P['P']
    if part in ('rleg', 'lleg'):
        if face_ == 'top': return P['P']
        if face_ == 'bottom': return P['BK']
        if y >= 8: return P['PL'] if y < 11 else P['PD']
        if y == 7: return P['PD']
        return P['P'] if not (face_ == 'front' and x == (0 if outer == 'right' else 3)) else P['PD']
    return None

def glasses(f, col):
    t = 0.22
    for sx in (1, -1):
        x0, x1 = (0.5, 3.4) if sx > 0 else (-3.4, -0.5)
        f.add('head', [x0, 28.85, -4.32], [x1, 28.85 + t, -4.1], col)          # top rim
        f.add('head', [x0, 27.05, -4.32], [x1, 27.05 + t, -4.1], col)          # bottom rim
        f.add('head', [x0, 27.05, -4.32], [x0 + t, 29.07, -4.1], col)
        f.add('head', [x1 - t, 27.05, -4.32], [x1, 29.07, -4.1], col)
        f.add('head', [sx * 4.0 - (0.18 if sx > 0 else 0), 28.6, -4.3], [sx * 4.0 + (0.18 if sx < 0 else 0) + (0 if sx > 0 else 0), 28.85, -1.0], col)  # arms
    f.add('head', [-0.5, 28.3, -4.3], [0.5, 28.55, -4.12], col)               # bridge

def r1_fit():
    f = Fit(); P = R1
    f.add('body', [-3.4, 23.6, -2.5], [3.4, 25.3, 2.5], P['P'], {'front': lambda x, y: P['LAV'] if .42 < x < .58 else P['P'], 'top': P['PD']})   # high collar
    f.add('body', [-4.42, 11.9, -2.42], [4.42, 12.9, 2.42], P['LAV'])                            # belt stripe
    vee = lambda x, y: (P['RD'] if abs(x - .5) < (1 - y) * .5 - .08 else P['GR']) if abs(x - .5) < (1 - y) * .5 else None
    f.add('body', [-1.5, 9.0, -2.56], [1.5, 12.0, -2.3], P['RD'], {'front': vee, 'back': vee, 'right': None, 'left': None, 'top': P['RD'], 'bottom': None})
    f.both('right_arm', [3.78, 13.0, -2.22], [8.22, 13.9, 2.22], P['LAV'])                      # cuffs
    ring = lambda x, y: P['LAV'] if .12 < ((x - .5) ** 2 + (y - .5) ** 2) ** .5 * 2 < .95 else None
    f.add('left_arm', [-8.14, 19.0, -1.6], [-8.02, 22.2, 1.6], P['LAV'], {'right': ring, 'left': ring, 'front': None, 'back': None, 'top': None, 'bottom': None})
    f.both('right_leg', [-0.3, 0.0, -2.45], [4.3, 4.3, 2.3], P['PL'], {'top': P['PD']})        # boots
    f.both('right_leg', [-0.42, 3.9, -2.55], [4.42, 4.6, 2.42], P['PL'])
    f.both('right_leg', [0.1, 0.0, -3.0], [3.9, 1.3, -2.45], P['PL'])
    f.both('right_leg', [-0.4, 0.0, -3.08], [4.4, 0.35, 2.4], P['BK'])
    f.both('right_leg', [-0.32, 4.6, -2.32], [4.32, 5.6, 2.32], P['PD'])                        # pant cuffs
    glasses(f, '#202024')
    return f

# ======================================================================= ORIGINS 2 (long purple hooded coat, grey tee, black pants, grey boots)
R2 = dict(P='#8a1eb6', PD='#5e1080', LAV='#b498cc', TEE='#625a58', TEED='#3e3a3a', BK='#1e1e1e', BKL='#3a3a3a', BO='#4a4a4c', WH='#d8d8dc', SI='#b8b8bc')

def r2_skin(part, face_, x, y, W, H, outer):
    P = R2
    if part == 'body':
        if face_ == 'front':
            if y >= 11: return P['BK']
            if y in (9, 10): return P['SI'] if x in (3, 4) else P['BK']
            if x in (0, 1, 6, 7): return P['P'] if x in (0, 7) else P['PD']
            if y == 0 and x in (3, 4): return SK
            if y in (5, 6) and x == 7 - y + 0: return P['TEED']
            if (x - y) == -3 or (x - y) == -4: return P['TEED'] if y >= 4 else P['TEE']
            return P['TEE']
        if face_ == 'top': return SK if 2 <= x <= 5 and y >= 1 else P['P']
        if y >= 11: return P['BK']
        return P['P']
    if part in ('rarm', 'larm'):
        if face_ == 'bottom': return SK
        if face_ == 'top': return P['P']
        if y == 11: return SK
        return P['PD'] if y == 10 else P['P']
    if part in ('rleg', 'lleg'):
        if face_ == 'top': return P['BK']
        if face_ == 'bottom': return P['SI']
        if y >= 8:
            if face_ == outer and x in (1, 2) and y < 11: return P['WH']
            return P['BO']
        return P['BK'] if (x + y) % 5 else P['BKL']
    return None

def r2_fit():
    f = Fit(); P = R2
    # hood bunched behind the neck, drawstrings
    f.add('body', [-3.7, 21.6, 2.0], [3.7, 25.0, 3.9], P['P'], {'top': P['PD']})
    f.add('body', [-3.0, 24.3, 1.4], [3.0, 25.6, 3.4], P['PD'])
    for x in (-1.7, 1.35):
        f.add('body', [x, 17.0, -2.66], [x + .35, 22.6, -2.42], P['LAV'])
    f.both('right_arm', [3.62, 13.2, -2.4], [8.4, 24.42, 2.4], P['P'])                         # coat sleeves
    f.both('right_arm', [3.5, 12.9, -2.52], [8.5, 13.8, 2.52], P['PD'])
    f.add('body', [-4.4, 11.4, -2.42], [4.4, 12.6, 2.42], P['BK'], {'front': lambda x, y: P['SI'] if .38 < x < .62 else P['BK']})   # belt
    f.add('body', [-0.7, 11.6, -2.62], [0.7, 12.4, -2.42], P['TEED'])
    f.both('right_leg', [-0.45, 3.6, -2.5], [4.45, 6.6, 2.5], P['BK'])                         # pants gathered over the boots
    f.both('right_leg', [-0.3, 0.0, -2.5], [4.3, 4.0, 2.3], P['BO'],
           {'right': lambda x, y: P['WH'] if .3 < x < .55 and y < .8 else P['BO']})             # boots
    f.both('right_leg', [0.1, 0.0, -3.2], [3.9, 1.4, -2.5], P['BO'])
    f.both('right_leg', [-0.42, 0.0, -3.3], [4.42, 0.4, 2.42], P['SI'])
    return f

# ======================================================================= RECARNET SEASON 3 (purple jacket, cross, crown, visor, purple boots)
R3 = dict(P='#7a1ea6', PD='#521470', PL='#9a3ed0', BK='#1c1c1e', BKL='#2c2c30', SI='#d4d4d8', BR='#6a4630', WH='#e8e8ec', RD='#e01818', CR='#1a181c')

def r3_skin(part, face_, x, y, W, H, outer):
    P = R3
    if part == 'body':
        if face_ == 'front':
            if y >= 11: return P['BK']
            if y in (9, 10): return P['WH'] if x in (3, 4) else P['BR']
            if x in (0, 1, 6, 7): return P['P'] if x in (0, 7) else P['PD']
            if (x, y) in ((2, 0), (5, 0), (2, 1), (5, 1), (3, 2), (4, 2)): return P['SI']        # chain
            if (x == 4 and 3 <= y <= 6) or (y == 4 and x in (3, 5)): return P['SI']            # cross
            return P['BK'] if y else SK if x in (3, 4) else P['BK']
        if face_ == 'top': return SK if 2 <= x <= 5 and y >= 1 else P['P']
        if y >= 11: return P['BK']
        if y in (9, 10) and face_ != 'back': return P['BR']
        return P['P']
    if part in ('rarm', 'larm'):
        if face_ == 'bottom': return SK
        if face_ == 'top': return P['P'] if part == 'larm' else P['BK']
        if y <= 4: return P['P']
        if y == 5: return P['PD']
        if part == 'rarm' and face_ == 'front' and 9 <= y <= 11:                     # the pink sigil on the back of his hand
            ring = {(1, 9), (2, 9), (0, 10), (3, 10), (1, 11), (2, 11)}
            if (x, y) in ring: return '#e86ab8'
            if (x, y) in ((1, 10), (2, 10)): return '#f4a8d8'
        return SK if y < 11 else SKD
    if part in ('rleg', 'lleg'):
        if face_ == 'top': return P['BK']
        if face_ == 'bottom': return P['CR']
        if y >= 6:
            if face_ in ('front', outer) and y in (7, 9) and x in (1, 2): return P['BK']
            if face_ == 'front' and x == 1: return '#2a1a36'                           # zip line
            return P['P'] if y < 11 else P['PD']
        if face_ in ('front', 'back') and (x + (y // 2)) % 3 == 0: return P['BKL']      # baggy folds
        return P['BK']
    return None

def boot_pattern(fx, fy):
    P = R3
    gx, gy = int(fx * 4), int(fy * 8)
    if gy in (2, 5) and 0 < gx < 3: return P['BK']
    if gx == 1 and gy in (3, 4, 6): return P['BK']
    return P['P']

def r3_fit():
    f = Fit(); P = R3
    blazer(f, P['P'], P['PD'], 18.4)
    f.both('right_arm', [3.5, 17.8, -2.5], [8.5, 18.8, 2.5], P['PD'])                          # rolled cuffs
    # shoulder pad on his right shoulder, purple stripe
    f.add('right_arm', [3.3, 21.2, -2.7], [8.7, 24.9, 2.7], P['BK'], {'top': P['BKL'], 'right': lambda x, y: P['PL'] if .55 < y < .75 else P['BK']})
    f.add('right_arm', [3.8, 24.6, -2.3], [8.2, 25.4, 2.3], P['BKL'])
    # belt, buckle, white pocket flap, torn white sash with purple drips on his right hip
    f.add('body', [-4.42, 11.3, -2.42], [4.42, 12.6, 2.42], P['BR'], {'front': lambda x, y: P['WH'] if .38 < x < .62 else P['BR']})
    f.add('body', [-0.75, 11.45, -2.62], [0.75, 12.45, -2.42], P['WH'])
    f.add('body', [-3.9, 9.4, -2.62], [-2.2, 12.4, -2.4], P['WH'], {'front': lambda x, y: P['SI'] if y > .8 else P['WH']})
    torn = lambda x, y: (None if y > .55 + .4 * abs(((x * 3) % 1) - .5) else P['PL'] if y > .5 and int(x * 7) % 3 == 0 else P['WH'])
    f.add('body', [4.3, 7.2, -1.4], [4.9, 12.4, 1.6], P['WH'], {'right': torn, 'left': torn, 'front': torn, 'back': torn})
    f.add('body', [0.5, 17.6, -2.2], [1.2, 19.8, -2.08], P['SI'])                               # 3D cross
    f.add('body', [0.15, 18.7, -2.2], [1.55, 19.2, -2.08], P['SI'])
    # baggy black pants, tall purple boots with the black pattern
    f.both('right_leg', [-0.4, 6.0, -2.4], [4.4, 12.3, 2.4], P['BK'])
    f.both('right_leg', [-0.32, 0.0, -2.55], [4.32, 6.6, 2.35], P['P'], {'front': boot_pattern, 'right': boot_pattern, 'left': boot_pattern, 'top': P['PD']})
    f.both('right_leg', [0.15, 0.0, -3.1], [3.85, 1.4, -2.55], P['P'])
    f.both('right_leg', [-0.42, 0.0, -3.2], [4.42, 0.4, 2.45], P['CR'])
    # wide lapels in a lighter purple, a stand-up collar at the back
    for sx in (1, -1):
        x0, x1 = (1.3, 2.6) if sx > 0 else (-2.6, -1.3)
        f.add('body', [x0, 19.4, -2.72], [x1, 24.2, -2.5], P['PL'], {'front': lambda x, y: P['PD'] if y > .85 else P['PL']})
    f.add('body', [-3.6, 23.4, 1.9], [3.6, 25.8, 2.9], P['PD'])
    # the black pants bunch up over the boots
    f.both('right_leg', [-0.55, 5.4, -2.55], [4.55, 7.0, 2.55], P['BK'], {'front': lambda x, y: P['BKL'] if int(x * 5) % 2 == 0 else P['BK']})
    # S3 hair: dark grey locs hanging round his head, purple locs tied up on top
    sys.path.insert(0, '/home/claude/xw/detail'); import core as _core
    class _A:
        def __init__(s_, f_): s_.f = f_
        def add(s_, bone, name, frm, to, color, rot=None, origin=None, shade=None):
            s_.f.add('head', frm, to, color, rot=rot if rot and any(rot) else None, origin=origin)
    A_ = _A(f)
    G1, G2, G3 = '#141216', '#22182c', '#7a2ab0'
    f.add('head', [-4.55, 31.4, -4.55], [4.55, 32.6, 4.55], G1)                                  # cap of hair under the crown
    f.add('head', [-4.55, 26.0, 4.0], [4.55, 32.0, 4.7], G1)
    for side in (1, -1):
        x0, x1 = (4.0, 4.7) if side > 0 else (-4.7, -4.0)
        f.add('head', [x0, 27.0, -3.0], [x1, 32.0, 4.6], G1)
        for k_, z in enumerate((-4.2, -2.4, -0.4, 1.6, 3.4)):
            LC = (G2, G1, G3) if k_ % 2 == 0 else ('#6a1e9e', '#8a2ec0', '#a64ade')
            _core.lock(A_, 'Head', [side * 4.6, 31.0, z], [side * 0.18, -1, 0.05], 6.0 + (z > 0) * 1.0, 1.5, 1.3, LC, wide='x', taper=(0.85, 0.7), tip_len=(0.2, 0.2))
    for k_, x in enumerate((-3.2, -1.6, 0.0, 1.6, 3.2)):
        LC = (G2, G1, G3) if k_ % 2 == 0 else ('#6a1e9e', '#8a2ec0', '#a64ade')
        _core.lock(A_, 'Head', [x, 31.0, 4.7], [0, -1, 0.15], 5.6, 1.5, 1.3, LC, wide='x', taper=(0.85, 0.7), tip_len=(0.2, 0.2))
    PU1, PU2, PU3 = '#8a2ec0', '#a64ade', '#c46cf0'
    f.add('head', [-1.6, 32.4, -1.4], [1.6, 34.0, 1.6], PU1)                                    # where the top locs are tied
    for (dx, dz, tx, tz, ln) in ((-1.0, -0.6, -0.25, -0.35, 5.2), (0.0, -0.8, 0.05, -0.45, 5.8), (1.0, -0.6, 0.3, -0.35, 5.0),
                                (-0.6, 0.6, -0.2, 0.25, 4.6), (0.7, 0.7, 0.25, 0.3, 4.4)):
        _core.lock(A_, 'Head', [dx, 33.6, dz], [tx, 1, tz], ln, 1.7, 1.6, (PU1, PU2, PU3), wide='x', taper=(0.9, 0.75), tip_len=(0.25, 0.25))
    # the purple mask over his brow, red eyes slanting in
    eyes = lambda x, y: (P['RD'] if (.12 < x < .42 and abs(y - (.3 + (x - .12) * 0.9)) < .2) or (.58 < x < .88 and abs(y - (.3 + (.88 - x) * 0.9)) < .2)
                         else '#3a0a5a' if y > .82 else P['P'])
    f.add('head', [-4.45, 29.2, -4.85], [4.45, 32.2, -4.3], P['P'], {'front': lambda x, y: '#3a0a5a' if y > .85 else P['P']})
    EYE = ['kkkk......kkkk', '.kkkk....kkkk.', '...kkk..kkk...']
    for r, row in enumerate(EYE):                                   # red mask eyes, slanting in (thin plates so they stay sharp)
        c = 0
        while c < len(row):
            if row[c] != 'k': c += 1; continue
            e = c
            while e < len(row) and row[e] == 'k': e += 1
            f.add('head', [-4.2 + c * 0.6, 31.6 - (r + 1) * 0.55, -4.97], [-4.2 + e * 0.6, 31.6 - r * 0.55, -4.85], P['RD'], shade=False)
            c = e
    for sx in (1, -1):
        x0, x1 = (4.25, 4.75) if sx > 0 else (-4.75, -4.25)
        f.add('head', [x0, 29.6, -4.6], [x1, 31.8, 1.0], P['PD'])
    # the black crown on top, five points with little balls
    for cube in (([-4.0, 32.4, -4.2], [4.0, 33.6, -3.8]), ([-4.0, 32.4, 3.8], [4.0, 33.6, 4.2]),
                 ([-4.2, 32.4, -4.2], [-3.8, 33.6, 4.2]), ([3.8, 32.4, -4.2], [4.2, 33.6, 4.2])):
        f.add('head', cube[0], cube[1], P['CR'], {'front': lambda x, y: P['PL'] if abs((x * 6) % 2 - 1) < 0.35 and y > .35 else P['CR']})
    for x, z in ((-3.8, -4.0), (0, -4.0), (3.8, -4.0), (-3.8, 3.8), (3.8, 3.8)):
        f.add('head', [x - .35, 33.6, z - .25], [x + .35, 35.4, z + .25], P['CR'])
        f.add('head', [x - .4, 35.4, z - .4], [x + .4, 36.1, z + .4], '#101012')
    return f

OUTFITS = {'origins1': (r1_skin, r1_fit, ()), 'origins2': (r2_skin, r2_fit, ('coat_back', 'coat_front_r', 'coat_front_l', 'coat_side_r', 'coat_side_l')),
           's3': (r3_skin, r3_fit, ())}

if __name__ == '__main__':
    which = sys.argv[1:] or list(OUTFITS)
    prev = []
    src = json.load(open(RES + 'models/detail/rhalix.json'))
    for key in which:
        skin_fn, fit_fn, coat = OUTFITS[key]
        skin = paint_skin(skin_fn, char='rhalix')
        atlas = Image.open(CHAR + 'rhalix_details.png').convert('RGBA')
        for ch in src['bones']['body']['children']:          # his coat in Origins 2's plain purple
            if ch['name'] in coat:
                for c in ch['cubes']:
                    w, h, d = c['size']
                    recolor_rect(atlas, c['uv'][0], c['uv'][1], math.ceil(2 * d + 2 * w), math.ceil(d + h), R2['P'])
        fit = fit_fn()
        pack(fit, atlas, region=(0, 148, 256, 256))
        name = 'rhalix_' + key
        skin.save(CHAR + name + '.png'); atlas.save(CHAR + name + '_details.png')
        if key == 's3':
            px = skin.load()
            for (x0, y0, w, h) in ((8, 0, 8, 8), (24, 8, 8, 8), (0, 8, 8, 3), (16, 8, 8, 3)):   # top, back, upper sides
                for yy in range(y0, y0 + h):
                    for xx in range(x0, x0 + w): px[xx, yy] = (22, 18, 26, 255)
            skin.save(CHAR + name + '.png')
        mj = export(fit, RES + 'models/detail/' + name + '.json', char='rhalix', keep=('right_wing', 'left_wing') + coat)
        if key == 's3':
            mj['bones']['head']['children'] = [c for c in mj['bones']['head'].get('children', []) if c.get('name') not in ('hair', 'bangs')]
            json.dump(mj, open(RES + 'models/detail/' + name + '.json', 'w'))
        mjp = json.loads(json.dumps(mj))
        for w in ('right_wing', 'left_wing'):
            mjp['bones']['body']['children'] = [c for c in mjp['bones']['body']['children'] if c.get('name') != w]
        sheet = preview(mjp, skin, atlas, face, scale=9)
        sheet.save(f'/tmp/claude-0/-home-claude/b2fe60cb-fd40-5a94-8ee2-7d1a501fd998/scratchpad/outfit/prev_r_{key}.png')
        prev.append(sheet); print(key, len(fit.cubes))
    W = max(p.width for p in prev); H = sum(p.height for p in prev)
    s = Image.new('RGB', (W, H)); y = 0
    for p in prev: s.paste(p, (0, y)); y += p.height
    s.save('/tmp/claude-0/-home-claude/b2fe60cb-fd40-5a94-8ee2-7d1a501fd998/scratchpad/outfit/prev_rhalix.png')
