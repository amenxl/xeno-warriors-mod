import sys
sys.path.insert(0, '/home/claude/xw/outfits')
from kit import *
from PIL import Image

face = Image.open(CHAR + 'mikiko_face.png').convert('RGBA')
SK = face.getpixel((15, 26))[:3]
SKD = mul(SK, .85)
OUT = CHAR

def noise_col(c):
    return C(c)

# ======================================================================= ORIGINS 1 (kid: black blazer, white shirt)
def o1_skin(part, face_, x, y, W, H, outer):
    BL, BLD, WH, PK, GR, GRD, SH, WR = '#2b2b30', '#1e1e22', '#eceaee', '#c9b6cc', '#55555c', '#46464c', '#7e2e24', '#6c6c74'
    if part == 'body':
        if face_ == 'front':
            if x in (0, 1, 6, 7): return BL if x in (0, 7) else BLD if y < 7 else BL
            if y <= 1 and x in (3, 4): return SK                          # V-neck
            if y == 2 and x in (3, 4): return PK
            return WH if y < 11 else PK
        if face_ == 'top': return SK if 2 <= x <= 5 and y >= 1 else BL
        if face_ == 'bottom': return BL
        return BLD if face_ == 'back' and x in (3, 4) else BL
    if part in ('rarm', 'larm'):
        if face_ == 'bottom': return SK
        if face_ == 'top': return BL
        wrapped = part == 'larm'
        if y == 11: return SK
        if wrapped and y >= 6: return WR if (x + y) % 3 else mul(C(WR), .78)
        if y == 10: return WH
        return BL
    if part in ('rleg', 'lleg'):
        if face_ == 'top': return GR
        if face_ == 'bottom': return '#3a1c18'
        if y >= 10: return SH if not (y == 11 and face_ != 'front') else mul(C(SH), .8)
        if y == 9: return GRD
        return GR if face_ != 'front' or x not in (0, 3) else GRD
    return None

def o1_fit():
    f = Fit()
    BL, BLD, WH, GR, GRD, SH, SOLE, WR = '#2b2b30', '#1c1c20', '#eceaee', '#55555c', '#44444a', '#7e2e24', '#3a1c18', '#6c6c74'
    # open blazer: back, sides, front panels, collar, lapels
    f.add('body', [-4.35, 11.4, 2.0], [4.35, 24.3, 2.4], BL)
    f.add('body', [4.0, 11.4, -2.4], [4.35, 24.3, 2.4], BL)
    f.add('body', [-4.35, 11.4, -2.4], [-4.0, 24.3, 2.4], BL)
    f.add('body', [2.0, 11.4, -2.4], [4.35, 24.3, -2.0], BL)
    f.add('body', [-4.35, 11.4, -2.4], [-2.0, 24.3, -2.0], BL)
    f.add('body', [-4.35, 24.0, -2.4], [4.35, 24.3, 2.4], BL)
    f.add('body', [-3.2, 23.6, 1.7], [3.2, 25.2, 2.7], BLD)                          # collar
    f.add('body', [1.6, 17.8, -2.6], [2.4, 24.3, -2.35], BLD)                        # lapels
    f.add('body', [-2.4, 17.8, -2.6], [-1.6, 24.3, -2.35], BLD)
    f.add('body', [2.6, 14.5, -2.55], [3.6, 15.4, -2.38], BLD)                       # pocket flaps
    f.add('body', [-3.6, 14.5, -2.55], [-2.6, 15.4, -2.38], BLD)
    # sleeves (right: full with white cuff; left: pushed up, forearm wrapped)
    f.add('right_arm', [3.7, 13.8, -2.35], [8.35, 24.35, 2.35], BL)
    f.add('right_arm', [3.8, 13.0, -2.25], [8.25, 13.8, 2.25], WH)
    f.add('left_arm', [-8.35, 17.6, -2.4], [-3.7, 24.35, 2.4], BL)
    f.add('left_arm', [-8.42, 17.2, -2.45], [-3.6, 18.1, 2.45], BLD)                 # rolled cuff
    for i, y in enumerate((13.0, 14.1, 15.2, 16.3)):
        f.add('left_arm', [-8.22 + (i % 2) * .05, y, -2.22], [-3.78, y + 1.15, 2.22], WR if i % 2 == 0 else mul(C(WR), .85))
    f.add('left_arm', [-6.6, 12.2, 2.1], [-5.6, 14.6, 2.6], mul(C(WR), .8))         # loose wrap end
    # trousers + shoes
    f.both('right_leg', [-0.3, 2.3, -2.3], [4.3, 12.2, 2.3], GR)
    f.both('right_leg', [-0.45, 1.7, -2.45], [4.45, 2.6, 2.45], GRD)
    f.both('right_leg', [-0.25, 0.0, -2.75], [4.25, 1.9, 2.3], SH)
    f.both('right_leg', [0.2, 0.0, -3.25], [3.8, 1.2, -2.75], SH)
    f.both('right_leg', [-0.32, -0.32, -3.3], [4.32, 0.38, 2.38], SOLE)
    return f

# ======================================================================= ORIGINS 2 (black/orange hoodie, shorts, yellow shoes)
O2 = dict(BL='#232526', BLD='#17191a', OR='#f06a0c', ORD='#c4520a', RD='#c8282a', WH='#e2e8e8', GR='#9a9e9e', GRD='#7a7e7e',
          BU='#1f4a72', YE='#f8be2c', YED='#d89a18', ST='#2a7cc8', SO='#5e5252')

def o2_skin(part, face_, x, y, W, H, outer):
    P = O2
    if part == 'body':
        if face_ == 'front':
            if y >= 10: return P['GR'] if x % 2 == 0 else P['GRD']
            if 2 <= x <= 5 and y >= 2: return P['RD'] if x == 4 and y < 10 else P['OR'] if x != 2 else P['ORD']
            if y >= 7 and x in (0, 1, 6, 7): return P['WH']
            if y == 0 and x in (3, 4): return SK
            return P['BL']
        if face_ in ('back', 'right', 'left'):
            if y >= 10: return P['GR'] if x % 2 == 0 else P['GRD']
            if y <= 1 and face_ != 'back': return P['OR']
            return P['BL'] if not (face_ == 'back' and y <= 1) else P['BLD']
        if face_ == 'top': return SK if 2 <= x <= 5 and y >= 1 else P['OR'] if x in (0, 7) else P['BL']
        return P['GR']
    if part in ('rarm', 'larm'):
        if face_ == 'bottom': return SK
        if face_ == 'top': return P['OR']
        if y <= 1 and face_ in (outer, 'front', 'back') and (face_ == outer or x in ((W - 1,) if outer == 'right' else (0,))): return P['OR']
        if y <= 4: return P['BL']
        if y == 5: return P['GR']
        return SK if y < 11 or face_ != 'front' else SKD
    if part in ('rleg', 'lleg'):
        if face_ == 'top': return P['BL']
        if face_ == 'bottom': return P['SO']
        if y <= 6:
            if face_ == 'front' and 1 <= y <= 4 and x + (4 - y) >= 3 and x <= 3: return P['BU'] if (x + y) % 4 else P['RD']
            return P['BL']
        if y == 7: return P['GR']
        if y == 8: return SK
        if y == 9: return P['WH'] if face_ in ('front', outer) else P['YE']
        if face_ == 'front' and x in (1, 2) and y == 10: return P['ST']
        return P['YE'] if y == 10 else P['YED']
    return None

def stitched_patch(fx, fy):
    edge = fx < .14 or fy > .84 or (fx + (1 - fy)) < .55
    if (fx + (1 - fy)) < .4: return None
    return O2['RD'] if edge and int((fx + fy) * 12) % 2 == 0 else O2['BU']

def o2_fit():
    f = Fit(); P = O2
    # hood bunched behind the neck + collar, drawstrings
    f.add('body', [-3.6, 21.8, 2.0], [3.6, 24.9, 3.7], P['BL'], {'top': P['BLD']}, group=('hood_down', [0, 24, 2.5]))
    f.add('body', [-2.8, 24.2, 1.4], [2.8, 25.4, 3.2], P['BLD'], group=('hood_down', [0, 24, 2.5]))
    hood_up(f, P['BL'], P['OR'])
    f.add('body', [-2.7, 23.4, -2.6], [2.7, 24.6, -2.0], P['BL'])
    for x in (-1.5, 1.2):
        f.add('body', [x, 19.0, -2.62], [x + 0.32, 23.6, -2.42], P['RD'])
        f.add('body', [x - 0.12, 18.4, -2.7], [x + 0.44, 19.0, -2.34], P['RD'])
    f.add('body', [-4.42, 11.7, -2.42], [4.42, 13.4, 2.42], P['GR'], {'front': lambda x, y: P['GR'] if int(x * 18) % 2 else P['GRD']})
    f.add('body', [3.6, 22.6, -2.35], [4.35, 24.3, 2.35], P['OR'])                     # orange shoulder yokes
    f.add('body', [-4.35, 22.6, -2.35], [-3.6, 24.3, 2.35], P['OR'])
    # short sleeves with grey bands
    f.both('right_arm', [3.55, 19.0, -2.45], [8.45, 24.45, 2.45], P['BL'], {'top': P['OR'], 'right': lambda x, y: P['OR'] if y < .35 else P['BL']})
    f.both('right_arm', [3.45, 18.3, -2.55], [8.55, 19.2, 2.55], P['GR'])
    # baggy shorts, grey hem, patches
    f.both('right_leg', [-0.55, 4.9, -2.55], [4.55, 12.3, 2.55], P['BL'])
    f.both('right_leg', [-0.7, 4.2, -2.7], [4.7, 5.2, 2.7], P['GR'])
    f.both('right_leg', [0.3, 7.6, -2.62], [3.4, 11.2, -2.55], P['BU'], {'front': stitched_patch, 'back': stitched_patch})
    # shoes
    f.both('right_leg', [-0.35, 0.0, -2.9], [4.35, 3.1, 2.35], P['YE'], {'front': lambda x, y: P['ST'] if .42 < x < .58 else P['YE']})
    f.both('right_leg', [0.1, 0.0, -3.5], [3.9, 1.7, -2.9], P['YE'], {'front': lambda x, y: P['ST'] if .42 < x < .58 else P['YE']})
    f.both('right_leg', [-0.45, -0.32, -3.6], [4.45, 0.45, 2.45], P['SO'])
    f.both('right_leg', [-0.42, 2.3, -2.98], [4.42, 2.85, 2.42], P['BLD'], {'front': lambda x, y: P['WH'] if .4 < x < .6 else P['BLD']})
    return f

# ======================================================================= RECARNET SEASON 3 (orange overalls)
S3 = dict(BL='#1f1f22', BLD='#141416', BLL='#2c2c30', OR='#f28c14', ORD='#c86a0c', ORL='#ffa83a', RD='#d22a26', RDD='#9c1a18',
          WH='#f0f0f0', SI='#b8bcc2', SID='#8a8e96', GL='#1a1a1c', SOCK='#26262a', PA='#8a94a4', PAL='#aab4c4', TAN='#c8b48a')

def flames(base, fl, dark=False):
    def fn(x, y):
        # tongues of flame rising from the bottom edge
        k = (x * 5) % 1.0
        h = 0.35 + 0.35 * abs(k - .5) * 2
        return fl if (1 - y) < h * (0.8 + 0.2 * ((x * 13) % 1.0)) else base
    return fn



def hood_up(f, col, inner):
    """The hood pulled up over his head (hidden until it rains): shell around head and hair, brim round the face."""
    g = ('hood_up', [0, 24, 0])
    f.add('head', [-5.4, 33.3, -4.7], [5.4, 34.4, 5.6], col, {'bottom': inner}, group=g)
    f.add('head', [4.95, 24.6, -4.7], [5.45, 33.4, 5.6], col, {'right': inner}, group=g)
    f.add('head', [-5.45, 24.6, -4.7], [-4.95, 33.4, 5.6], col, {'left': inner}, group=g)
    f.add('head', [-5.4, 24.6, 5.1], [5.4, 33.4, 5.65], col, {'front': inner}, group=g)
    f.add('head', [-5.45, 32.0, -5.1], [5.45, 34.45, -4.6], col, {'back': inner, 'bottom': inner}, group=g)     # brim over the brow
    f.add('head', [4.0, 24.4, -5.1], [5.45, 32.1, -4.6], col, {'back': inner}, group=g)
    f.add('head', [-5.45, 24.4, -5.1], [-4.0, 32.1, -4.6], col, {'back': inner}, group=g)
    f.add('head', [-3.0, 34.3, -3.5], [3.0, 34.8, 4.8], col, group=g)                                           # rounded top

def wrap_tex(seed, tilt=0.35):
    """Overlapping diagonal strips of bandage in three reds, dark seams and little frayed tick marks."""
    P = S3
    shades = [P['RD'], '#a81c1e', P['RDD'], '#e0332c']
    def fn(x, y):
        v = y * 4.2 + x * tilt + seed * 0.37
        band = int(v) % 4; fr = v - int(v)
        if fr < 0.06: return '#701616'                                   # seam between turns
        if fr < 0.12 and int(x * 9 + seed) % 4 == 0: return '#861a1a'    # frayed ticks along the seam
        return mul(shades[band], 1.0 - 0.12 * fr)
    return fn

def hand_wraps(f, y_top, glove=False):
    """Bandages on his left forearm and over the knuckles, wound on the slant, a loose end hanging.
    glove=True (like the art): a black glove on the hand instead, the wraps above it."""
    P = S3
    faces = lambda s: {k: wrap_tex(s + i) for i, k in enumerate(('front', 'back', 'right', 'left'))}
    f.add('left_arm', [-8.28, 13.2, -2.28], [-3.72, y_top, 2.28], P['RD'], faces(0), shade=False)
    if glove:
        f.add('left_arm', [-8.15, 12.0, -2.15], [-3.85, 13.25, 2.15], P['GL'], {'front': lambda x, y: '#2c2c30' if y < .25 else P['GL']})
    else:
        f.add('left_arm', [-8.2, 12.0, -2.2], [-3.8, 13.3, 2.2], P['RD'], faces(5) | {'bottom': None}, shade=False)   # over the knuckles
    # a couple of turns standing proud, tipped on the slant like the art
    for i, (y0, ang) in enumerate(((14.1, 10), (15.9, -8), (y_top - 1.0, 12))):
        f.add('left_arm', [-8.42, y0, -2.42], [-3.58, y0 + 0.75, 2.42], '#c8201e' if i % 2 == 0 else '#9a1818',
              {k: (lambda yy: lambda x, y: '#701616' if y > .86 else '#861a1a' if int(x * 11) % 6 == 0 and y < .25 else ('#d42a24' if y < .5 else '#a81c1e'))(y0)
               for k in ('front', 'back', 'right', 'left')}, rot=[0, 0, ang], origin=[-6, y0 + .37, 0])
    f.add('left_arm', [-4.0, 11.2, -1.0], [-3.55, 13.4, -0.2], '#a81c1e', rot=[0, 0, -12], origin=[-3.8, 13.3, -.6])   # loose end

def s3_skin(tied):
    P = S3
    def fn(part, face_, x, y, W, H, outer):
        if part == 'body':
            if face_ == 'front':
                if y >= 9 and x in (0, 7) and y < 11: return P['BL']        # shirt shows beside the bib, under the arms
                if y >= 9: return P['ORD'] if y == 9 else P['OR']
                if x in (1, 6) and y <= 3: return P['RD'] if y < 3 else P['SI']
                if 1 <= x <= 6 and y >= 4:
                    if y == 6 and 2 <= x <= 5: return P['RD']
                    return P['OR'] if x not in (1,) else P['ORD']
                if not tied and x in (2, 5) and 1 <= y <= 4: return P['WH']
                if y == 0 and 3 <= x <= 4: return SK if tied else P['BLL']
                return P['BL']
            if face_ == 'back':
                if y >= 4: return P['OR'] if 1 <= x <= 6 or y >= 11 else P['BL']
                if x in (1, 6): return P['RD'] if y < 3 else P['SI']
                return P['BL']
            if face_ in ('right', 'left'):
                return P['OR'] if y >= 11 else P['BL']
            if face_ == 'top':
                if x in (1, 6): return P['RD']
                return SK if (2 <= x <= 5 and y >= 1 and tied) else P['BL']
            return P['OR']
        if part in ('rarm', 'larm'):
            right = part == 'rarm'
            if face_ == 'top': return P['BL']
            if face_ == 'bottom': return P['GL'] if (right or not tied) else SK
            if tied:                                  # t-shirt: short black sleeve with orange cuff
                if y <= 3: return P['BL']
                if y == 4: return P['OR']
                if not right and 6 <= y <= 10: return P['RD'] if (x + y) % 3 else P['RDD']    # red wraps
                if right and y == 11: return P['GL']
                return SK
            if y == 11: return P['GL']
            if right and y >= 6: return flames(P['BL'], P['RD'])(x / W, (y - 6) / 5)
            if not right and 7 <= y <= 10: return P['RD'] if (x + y) % 3 else P['RDD']
            return P['BL'] if y != 10 or right else P['RD']
        if part in ('rleg', 'lleg'):
            if face_ == 'top': return P['OR']
            if face_ == 'bottom': return P['BLD']
            if y <= 5:
                if face_ == outer and 2 <= y <= 4 and 1 <= x <= 2: return P['RD'] if y == 2 else P['ORD']
                return P['OR'] if not (face_ == 'front' and x == (3 if outer == 'right' else 0)) else P['ORD']
            if y == 6: return P['RD']
            if y == 7: return P['WH']
            if y == 8: return SK
            if y == 9: return P['SOCK']
            if face_ == 'front' and y == 10 and x in (1, 2): return P['SI']
            return P['RD'] if y == 10 else P['RDD']
        return None
    return fn

def s3_fit(tied):
    f = Fit(); P = S3
    # overalls: bib with pocket, straps, buckles, waistband
    pocket = lambda x, y: P['RD'] if .18 < y < .3 and .15 < x < .85 else (P['ORD'] if x < .06 or x > .94 else P['OR'])
    f.add('body', [-3.25, 13.2, -2.48], [3.25, 20.6, -2.05], P['OR'], {'front': pocket, 'top': P['ORD']})
    for sx in (1, -1):
        x0, x1 = (2.45, 3.35) if sx > 0 else (-3.35, -2.45)
        f.add('body', [x0, 20.4, -2.56], [x1, 24.32, -2.1], P['RD'])                  # straps up the front
        f.add('body', [x0, 24.0, -2.56], [x1, 24.4, 2.56], P['RD'])                   # over the shoulders
        f.add('body', [x0 - .2, 19.7, -2.72], [x1 + .2, 20.9, -2.4], P['SI'], {'front': lambda x, y: P['SID'] if .3 < x < .7 and .3 < y < .7 else P['SI']})
        f.add('body', [(x0 + x1) / 2 - .3, 19.0, -2.62], [(x0 + x1) / 2 + .3, 19.6, -2.44], P['RD'])   # button
    # back bib (filled, like the front) with the straps hooked into its top corners
    f.add('body', [-3.25, 13.2, 2.05], [3.25, 20.6, 2.48], P['OR'], {'back': lambda x, y: P['ORD'] if x < .06 or x > .94 or y < .05 else P['OR'], 'top': P['ORD']})
    for sx in (1, -1):
        x0, x1 = (2.45, 3.35) if sx > 0 else (-3.35, -2.45)
        f.add('body', [x0, 20.4, 2.1], [x1, 24.32, 2.56], P['RD'])
        f.add('body', [x0 - .2, 19.7, 2.4], [x1 + .2, 20.9, 2.72], P['SI'], {'back': lambda x, y: P['SID'] if .3 < x < .7 and .3 < y < .7 else P['SI']})
    f.add('body', [-4.4, 11.5, -2.45], [4.4, 13.3, 2.45], P['OR'], {'front': lambda x, y: P['RD'] if .48 < x < .55 and .4 < y < .6 else P['OR']})
    # hood behind the neck + drawstrings (hoodie variant), collar tee (tied variant)
    if not tied:
        f.add('body', [-3.7, 21.6, 2.0], [3.7, 24.9, 3.8], P['BL'], {'top': P['BLD']}, group=('hood_down', [0, 24, 2.5]))
        f.add('body', [-2.9, 24.2, 1.4], [2.9, 25.5, 3.3], P['BLD'], group=('hood_down', [0, 24, 2.5]))
        hood_up(f, P['BL'], P['BLL'])
        f.add('body', [-2.8, 23.4, -2.62], [2.8, 24.6, -2.0], P['BL'])
        for x in (-1.6, 1.25):
            f.add('body', [x, 19.2, -2.66], [x + .32, 23.6, -2.5], P['WH'])
            f.add('body', [x - .1, 18.6, -2.72], [x + .42, 19.2, -2.45], P['SI'])
    # arms
    if tied:
        f.both('right_arm', [3.6, 19.6, -2.42], [8.4, 24.42, 2.42], P['BL'], {'top': P['BL']})
        f.both('right_arm', [3.5, 19.0, -2.5], [8.5, 19.8, 2.5], P['OR'])
        f.add('right_arm', [3.85, 12.0, -2.15], [8.15, 13.1, 2.15], P['GL'])           # glove (fingerless)
        hand_wraps(f, 18.1)
    else:
        f.add('right_arm', [3.62, 13.1, -2.4], [8.38, 17.6, 2.4], P['RD'],             # flame guard on his right forearm
              {k: flames(P['RD'], P['BL']) for k in ('front', 'back', 'right', 'left')})
        f.add('right_arm', [3.85, 12.0, -2.15], [8.15, 13.1, 2.15], P['GL'])
        f.add('right_arm', [3.75, 13.0, -2.25], [8.25, 13.8, 2.25], P['RD'], {'front': lambda x, y: '#9a1818' if y > .7 else P['RD']})   # red wrist strap (art)
        hand_wraps(f, 16.8, glove=True)
        f.both('right_arm', [3.6, 16.8, -2.42], [8.4, 17.7, 2.42], P['BLL'])            # bunched hoodie cuffs
    # shoulder pauldron on his left shoulder
    f.add('left_arm', [-8.75, 20.4, -2.75], [-3.35, 24.9, 2.75], P['PA'], {'top': P['PAL'], 'right': P['PAL']})
    f.add('left_arm', [-8.35, 24.6, -2.35], [-3.85, 25.5, 2.35], P['PAL'])
    f.add('left_arm', [-8.95, 20.0, -2.95], [-3.15, 20.6, 2.95], P['SID'])
    # baggy capris: red and white cuffs, cargo pockets
    f.both('right_leg', [-0.6, 5.6, -2.6], [4.6, 12.3, 2.6], P['OR'], {'right': lambda x, y: P['ORD'] if y > .85 else P['OR']})
    f.both('right_leg', [-0.72, 4.9, -2.72], [4.72, 5.8, 2.72], P['RD'])
    f.both('right_leg', [-0.78, 4.2, -2.78], [4.78, 5.0, 2.78], P['WH'])
    f.both('right_leg', [4.55, 7.2, -1.3], [5.05, 10.0, 1.3], P['OR'], {'top': P['RD'], 'right': lambda x, y: P['RD'] if y < .3 else P['ORD']})
    # boots: red with black toe and sole, silver gem, sock
    f.both('right_leg', [-0.38, 0.0, -2.8], [4.38, 2.75, 2.4], P['RD'], {'top': P['SOCK']})
    f.both('right_leg', [0.15, 0.0, -3.4], [3.85, 1.5, -2.8], P['BLD'])
    f.both('right_leg', [-0.48, -0.32, -3.5], [4.48, 0.5, 2.5], P['BLD'])
    f.both('right_leg', [-0.46, 1.75, -2.88], [4.46, 2.2, 2.48], P['SI'])
    f.both('right_leg', [1.35, 1.4, -3.1], [2.65, 2.8, -2.8], P['SI'], {'front': lambda x, y: P['WH'] if x < .5 and y < .5 else P['SI'] if x < .5 or y < .5 else P['SID']})
    if not tied:                                                   # white chevron on the outer side of the sneakers (art)
        f.add('right_leg', [4.36, 0.5, -1.6], [4.6, 2.4, 1.2], P['WH'], {'right': lambda x, y: P['WH'] if abs(y - .5) < .5 - abs(x - .5) * .8 else P['RD']})
        f.add('left_leg', [-0.6, 0.5, -1.6], [-0.36, 2.4, 1.2], P['WH'], {'left': lambda x, y: P['WH'] if abs(y - .5) < .5 - abs(x - .5) * .8 else P['RD']})
    if tied:
        # jacket tied around his hips: back panel to mid-thigh with white hem, sides, knotted sleeves in front
        hem = lambda x, y: P['WH'] if y > .82 else P['BL']
        allf = lambda fn: {'back': fn, 'front': fn, 'right': fn, 'left': fn}
        # (each flap is its own part the mod swings with the leg beside it - like Rhalix's coat - so legs never cut through)
        f.add('body', [-4.75, 6.6, 2.3], [4.75, 12.6, 2.85], P['BL'], allf(hem), group=('coat_back', [0, 12.6, 2.6]))
        f.add('body', [4.35, 6.9, -1.9], [4.85, 12.6, 2.8], P['BL'], allf(hem), group=('coat_side_r', [4.6, 12.6, 0.4]))
        f.add('body', [-4.85, 6.9, -1.9], [-4.35, 12.6, 2.8], P['BL'], allf(hem), group=('coat_side_l', [-4.6, 12.6, 0.4]))
        f.add('body', [-4.6, 11.4, -2.75], [4.6, 12.7, 2.75], P['BL'])                  # the tied band
        f.add('body', [-1.2, 10.4, -3.25], [1.2, 12.4, -2.6], P['BLL'])                 # knot
        cuff = lambda x, y: (P['RD'] if (y > .55 and ((x * 4) % 1 < .5 or y > .78)) else P['WH']) if y > .5 else P['BL']
        f.add('body', [0.15, 6.4, -3.15], [1.6, 10.6, -2.65], P['BL'], allf(cuff), group=('coat_front_r', [0.9, 10.6, -2.9]))
        f.add('body', [-1.6, 6.8, -3.15], [-0.15, 10.6, -2.65], P['BL'], allf(cuff), group=('coat_front_l', [-0.9, 10.6, -2.9]))
    return f

OUTFITS = {
    'origins1': (o1_skin, o1_fit, {'frame': '#d2902c', 'lens': '#3a0e12', 'glint': '#a05050', 'bridge': '#d2902c'}),
    'origins2': (o2_skin, o2_fit, {'frame': '#c8282a', 'lens': '#4a7c9a', 'glint': '#9cc4dc', 'bridge': '#c8282a'}),
    's3': (s3_skin(False), lambda: s3_fit(False), {}),
    's3b': (s3_skin(True), lambda: s3_fit(True), {}),
}

if __name__ == '__main__':
    import os
    which = sys.argv[1:] or list(OUTFITS)
    prev = []
    for key in which:
        skin_fn, fit_fn, gog = OUTFITS[key]
        skin = paint_skin(skin_fn)
        atlas = Image.open(CHAR + 'mikiko_details.png').convert('RGBA')
        for part, col in gog.items():
            src = json.load(open(RES + 'models/detail/mikiko.json'))
            sizes = {tuple(c['uv']): c['size'] for c in src['bones']['head']['children'][0]['cubes']}
            for uv in GOGGLE[part]:
                w, h, d = sizes[uv]
                recolor_rect(atlas, uv[0], uv[1], math.ceil(2 * d + 2 * w), math.ceil(d + h), col)
        fit = fit_fn()
        pack(fit, atlas)
        name = 'mikiko_' + key
        skin.save(OUT + name + '.png'); atlas.save(OUT + name + '_details.png')
        mj = export(fit, RES + 'models/detail/' + name + '.json')
        # Fox King version: the same outfit with Fox King's hair (his atlas, his head), goggles recoloured the same way
        katlas = Image.open(CHAR + 'mikiko_foxking_details.png').convert('RGBA')
        ksrc = json.load(open(RES + 'models/detail/mikiko_foxking.json'))
        msrc = json.load(open(RES + 'models/detail/mikiko.json'))
        msizes = {tuple(c['uv']): tuple(round(v, 2) for v in c['size']) for c in msrc['bones']['head']['children'][0]['cubes']}
        for part, col in gog.items():
            want = {msizes[uv] for uv in GOGGLE[part]}
            for c in ksrc['bones']['head']['children'][0]['cubes']:
                if tuple(round(v, 2) for v in c['size']) in want:
                    w, h, d = c['size']
                    recolor_rect(katlas, c['uv'][0], c['uv'][1], math.ceil(2 * d + 2 * w), math.ceil(d + h), col)
        kfit = fit_fn()
        pack(kfit, katlas)
        katlas.save(OUT + name + '_foxking_details.png')
        export(kfit, RES + 'models/detail/' + name + '_foxking.json', char='mikiko_foxking')
        sheet = preview(mj, skin, atlas, face, scale=9)
        sheet.save(f'/tmp/claude-0/-home-claude/b2fe60cb-fd40-5a94-8ee2-7d1a501fd998/scratchpad/outfit/prev_{key}.png')
        prev.append(sheet)
        print(key, len(fit.cubes), 'cubes')
    W = max(p.width for p in prev); H = sum(p.height for p in prev)
    s = Image.new('RGB', (W, H)); y = 0
    for p in prev: s.paste(p, (0, y)); y += p.height
    s.save('/tmp/claude-0/-home-claude/b2fe60cb-fd40-5a94-8ee2-7d1a501fd998/scratchpad/outfit/prev_all.png')
