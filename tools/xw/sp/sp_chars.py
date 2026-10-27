"""The Sky Patrol playables, season 1 looks: Kyrie, Lexy, Max (Sintrie) and Sam. Each gets a skin, 3D details (hair,
clothes, gear), an HD face and Skylander wings (a wing style from tools/xw/wings, recoloured), all on one detail atlas."""
import sys, math, json, copy
sys.path.insert(0, '/home/claude/xw/ultima'); sys.path.insert(0, '/home/claude/xw/npcs'); sys.path.insert(0, '/home/claude/xw/outfits'); sys.path.insert(0, '/home/claude/xw/detail')
import ultima as U
import npcs, core
from npcs import hair, shell, Fit, pack, paint_skin, face_img, fringe_locks, Adapter, C
from PIL import Image

RES = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'

def lock(f, root, d, ln, w, cols, thick=0.8, taper=(0.8, 0.55), bone='Head', tip=(0.3, 0.2)):
    core.lock(Adapter(f), bone, root, d, ln, w, thick, cols, wide='x', taper=taper, tip_len=tip)

STAR = ['...k...', '..kkk..', 'kkkkkkk', '.kkkkk.', '..kkk..', '.kk.kk.', 'kk...kk']

def plate(f, bone, rows, cx, ytop, z, cell, col):
    """A pixel pattern built from thin plates (so it stays sharp), merged into horizontal runs."""
    x0 = cx - len(rows[0]) * cell / 2
    for r, row in enumerate(rows):
        c = 0
        while c < len(row):
            if row[c] != 'k': c += 1; continue
            e = c
            while e < len(row) and row[e] == 'k': e += 1
            f.add(bone, [x0 + c * cell, ytop - (r + 1) * cell, z], [x0 + e * cell, ytop - r * cell, z + 0.12], col, shade=False)
            c = e

def stripes(a, b, n=2):
    """Horizontal stripes: rows alternate every n pixels (for the skin painter)."""
    return lambda y: a if (y // n) % 2 == 0 else b

# ====================================================================== KYRIE
K_SKIN, K_EYE = '#7a4c34', '#7a4ad8'
KH1, KH2, KH3 = '#2e1a3a', '#4a2a5c', '#6a4480'
K_BLK, K_WHT, K_LAV = '#18161c', '#ecebf0', '#d6d2e6'

def kyrie_paint(part, face, x, y, W, H, outer):
    if part == 'body':
        if y >= 9: return K_BLK if not (y == 9) else '#2a2a30'                # belt line
        if face == 'top': return K_WHT
        return K_BLK if (y // 2) % 2 == 0 else K_WHT                          # black & white stripes
    if part in ('rarm', 'larm'):
        if y < 8: return K_LAV                                                # pale sleeves pushed up
        if part == 'larm' and y >= 9: return K_BLK                           # one fingerless glove (her left)
        return None
    if part in ('rleg', 'lleg'):
        if y >= 10: return K_BLK if y < 11 or face != 'front' else K_WHT     # boots with white toes
        if face == 'front' and 4 <= y <= 5 and 1 <= x <= 2: return K_WHT     # ripped knees
        return K_BLK
    return None

def kyrie(f):
    # long side locks framing her face, a white streak at the part, two shell space-buns and a heart ahoge
    hair(f, KH1, KH2, back_len=25, side_len=26,
         locks=[([s * 3.7, 31.6, -4.55], [s * 0.1, -1, -0.1], 11.0, 2.0) for s in (1, -1)]
         + [([s * 1.6, 32.0, -4.7], [s * 0.35, -1, -0.2], 3.2, 2.2) for s in (1, -1)]
         + [([s * 4.6, 30, z], [s * 0.2, -1, 0.1], 4.5, 1.8) for s in (1, -1) for z in (-0.5, 2.5)])
    f.add('head', [-0.6, 31.2, -4.85], [0.3, 32.4, -4.55], '#f2eef6')                      # white streak at the part
    for s_ in (1, -1):                                                                  # the shell buns: round, ridged
        cx, cz = s_ * 2.7, 0.6
        for (w, y0, y1) in ((3.6, 32.8, 33.8), (4.2, 33.8, 35.0), (4.0, 35.0, 36.0), (3.2, 36.0, 36.8), (2.0, 36.8, 37.4)):
            f.add('head', [cx - w / 2, y0, cz - w / 2], [cx + w / 2, y1, cz + w / 2], KH2,
                  {'front': lambda x, y: KH3 if int(x * 5) % 2 == 0 else KH2, 'top': lambda x, y: KH3 if int(x * 4) % 2 == 0 else KH2,
                   'right': lambda x, y: KH3 if int(x * 5) % 2 == 0 else KH2, 'left': lambda x, y: KH3 if int(x * 5) % 2 == 0 else KH2})
        f.add('head', [cx - 1.4, 32.5, cz - 1.4], [cx + 1.4, 33.0, cz + 1.4], '#a8a0b8')    # little hair ring
    for dx, dy in ((-0.9, 0.6), (0.9, 0.6), (-0.5, 0.0), (0.5, 0.0), (0, -0.5), (-1.3, 1.1), (1.3, 1.1), (-0.5, 1.4), (0.5, 1.4)):
        f.add('head', [dx - 0.25, 33.4 + dy, -2.6], [dx + 0.25, 33.9 + dy, -2.2], KH3)     # heart ahoge
    # striped long-sleeve with the sleeves pushed up, belt, ripped black jeans, boots
    shell(f, 'body', [-4, 12.4, -2], [4, 24, 2], 0.3, K_WHT, {'front': lambda x, y: K_BLK if int(y * 11.6) % 2 == 0 else K_WHT,
                                                            'back': lambda x, y: K_BLK if int(y * 11.6) % 2 == 0 else K_WHT,
                                                            'right': lambda x, y: K_BLK if int(y * 11.6) % 2 == 0 else K_WHT,
                                                            'left': lambda x, y: K_BLK if int(y * 11.6) % 2 == 0 else K_WHT})
    f.add('body', [-4.4, 11.8, -2.45], [4.4, 12.8, 2.45], '#2a2a30')                   # belt
    f.add('body', [-1.0, 11.9, -2.6], [1.0, 12.7, -2.4], '#c8c8d0')                    # buckle
    f.add('body', [-4.5, 11.6, -2.5], [-2.0, 12.2, 2.5], '#ececf0')                    # white sash end
    f.both('right_arm', [3.62, 17.4, -2.38], [8.38, 24.38, 2.38], K_LAV)
    f.both('right_arm', [3.5, 16.8, -2.5], [8.5, 17.8, 2.5], '#c4bfd8')                 # pushed-up roll
    f.add('left_arm', [-8.5, 12.0, -2.5], [-3.5, 15.2, 2.5], K_BLK, {'bottom': K_SKIN})  # fingerless glove (her left)
    f.add('left_arm', [-8.55, 14.8, -2.55], [-3.45, 15.2, 2.55], '#f0f0f0')
    f.both('right_leg', [-0.35, 2.4, -2.35], [4.35, 12.3, 2.35], K_BLK,
           {'front': lambda x, y: '#e8e8ee' if .5 < y < .62 and .2 < x < .8 and int(x * 9) % 2 == 0 else K_BLK})
    f.both('right_leg', [-0.4, 0, -2.6], [4.4, 3.4, 2.4], '#121016', {'front': lambda x, y: '#ececf0' if y > .55 else '#121016'})
    f.both('right_leg', [0.1, 0, -3.15], [3.9, 1.4, -2.6], '#ececf0')                  # white toe cap
    f.both('right_leg', [-0.45, 0, -3.25], [4.45, 0.35, 2.45], '#ececf0')

def kyrie_marks(px, cm, dark):
    for x, y in [(4, 21), (5, 22), (5, 23), (25, 21), (26, 22), (26, 23)]: px[x, y] = (24, 14, 20, 255)   # dark liner drips
KYRIE = dict(skin=K_SKIN, eyes=K_EYE, hair=[KH1, KH2], paint=kyrie_paint,
             brow=['..kkkkkk.', 'kk.......'],
             eye=['.kkkkkkk.', 'kkkkkkkkk', '.wdOOodw.', '.wWOpoOw.', '.wOoopOw.', '..wOOOw..', '.kkkkkk..'],
             mouth=['.rrrr.', '.rRRr.'], mouth_x=13, mouth_y=24, face_extra=[kyrie_marks],
             wing=('kyrie', '#5e3a80'), fit=kyrie)

# ====================================================================== LEXY
L_SKIN, L_EYE = '#a2704e', '#c89048'
LH1, LH2, LH3 = '#3a3aa0', '#5050c0', '#7070d8'
L_BLUE, L_BLK, L_PANT, L_STRIPE = '#2a3ac8', '#141418', '#16242a', '#6050c8'

def lexy_paint(part, face, x, y, W, H, outer):
    if part == 'body':
        if y >= 10: return L_PANT
        return L_BLUE
    if part in ('rarm', 'larm'):
        if y < 4: return L_BLUE
        if 6 <= y <= 7: return L_BLK                                         # wristbands
        return None
    if part in ('rleg', 'lleg'):
        if y >= 11: return L_BLK                                             # flats
        if face in ('right', 'left') and x == W // 2: return L_STRIPE
        return L_PANT
    return None

def lexy(f):
    # long blue-violet hair up in a high ponytail with a cream bow, white diamond clips
    hair(f, LH1, LH2, back_len=22, side_len=24,
         locks=fringe_locks(5, 31, 7.0, 4.0, 1.8, 0.3) + [([s * 3.9, 31.0, -4.4], [s * 0.15, -1, -0.1], 7.5, 2.0) for s in (1, -1)])
    f.add('head', [-1.6, 31.2, 4.2], [1.6, 33.2, 6.0], LH2)                              # ponytail root
    lock(f, [0.0, 32.2, 5.4], [0, -0.35, 1.0], 6.0, 3.4, (LH1, LH2, LH3), thick=2.4)
    lock(f, [0.0, 29.5, 8.6], [0, -1, 0.35], 16.0, 3.2, (LH2, LH1, LH3), thick=2.2, taper=(0.75, 0.35))
    for s in (1, -1):                                                                   # cream bow
        f.add('head', [s * 0.7 if s > 0 else -2.9, 32.2, 5.4], [2.9 if s > 0 else -0.7, 34.0, 6.4], '#f2ead2')
    f.add('head', [-0.7, 32.6, 5.3], [0.7, 33.6, 6.6], '#e2d8b8')
    for s in (1, -1):                                                                   # diamond clips
        f.add('head', [s * 3.4 - 0.5, 29.6, -5.4], [s * 3.4 + 0.5, 31.0, -5.1], '#f4f4ff')
        f.add('head', [s * 2.5 - 0.4, 30.6, -5.4], [s * 2.5 + 0.4, 31.8, -5.1], '#f4f4ff')
    # the black mask with the white zigzag, tied over the lower face
    # her mask, fully 3D: a black scarf over the lower face, wrapping round, tied at the back, the end draped down the front
    f.add('head', [-4.45, 23.8, -4.8], [4.45, 26.8, -4.35], L_BLK)                        # front
    for s_ in (1, -1):
        x0, x1 = (4.1, 4.5) if s_ > 0 else (-4.5, -4.1)
        f.add('head', [x0, 24.2, -4.8], [x1, 26.6, 4.5], L_BLK)                            # wraps round the sides
    f.add('head', [-4.5, 24.2, 4.1], [4.5, 26.6, 4.5], L_BLK)                               # and the back
    f.add('head', [-1.0, 24.6, 4.5], [1.0, 26.2, 5.4], '#222228')                            # the knot
    lock(f, [0.4, 25.0, 5.2], [0.15, -1, 0.35], 3.6, 1.2, (L_BLK, '#222228', L_BLK), thick=0.3)
    ZIG = ['k.....k.....k.....k...', '.k...k.k...k.k...k.k..', '..k.k...k.k...k.k...k.', '...k.....k.....k.....k',
           '......................', '.k.k...k.k...k.k...k..', '..k.....k.....k.....k.']
    C_ = 0.4; X0 = -4.4
    for r, row in enumerate(ZIG):                                                          # the white zigzag, as thin plates
        for c, ch in enumerate(row):
            if ch == 'k':
                f.add('head', [X0 + c * C_, 26.5 - (r + 1) * C_, -4.9], [X0 + (c + 1) * C_, 26.5 - r * C_, -4.8], '#eceef8', shade=False)
    f.add('head', [-3.4, 22.2, -4.75], [-0.4, 24.0, -4.4], L_BLK)                          # the end draped under her chin
    f.add('head', [-3.0, 23.0, -4.85], [-2.2, 23.6, -4.74], '#d8d8e0')                      # with a little buckle
    # blue tee, black capelet with a strap, wristbands, baggy pants with stripes and little bows, flats
    shell(f, 'body', [-4, 12.0, -2], [4, 24, 2], 0.3, L_BLUE)
    f.both('right_arm', [3.62, 20.0, -2.38], [8.38, 24.38, 2.38], L_BLUE)
    shell(f, 'body', [-4.6, 19.0, -2.6], [4.6, 24.4, 2.6], 0.25, L_BLK, {'bottom': L_BLK})          # capelet
    f.both('right_arm', [3.3, 20.0, -2.8], [8.8, 24.8, 2.8], L_BLK)
    f.add('body', [-3.2, 20.6, -3.15], [0.6, 21.4, -2.9], '#d8d8e0', rot=[0, 0, -25], origin=[-1.3, 21.0, -3.0])   # strap
    f.add('body', [1.6, 19.8, -3.1], [2.4, 20.6, -2.9], '#f4f4ff')                     # little white x
    f.both('right_arm', [3.5, 14.4, -2.5], [8.5, 16.0, 2.5], L_BLK)                    # wristbands
    f.both('right_leg', [-0.55, 1.2, -2.55], [4.55, 12.3, 2.55], L_PANT,
           {'right': lambda x, y: L_STRIPE if .4 < x < .6 else L_PANT, 'left': lambda x, y: L_STRIPE if .4 < x < .6 else L_PANT})
    for y in (8.0, 4.0):
        f.both('right_leg', [4.4, y, -0.9], [4.9, y + 1.0, 0.9], '#c8c8f0')            # little bows on the side
    f.both('right_leg', [-0.6, 1.0, -2.6], [4.6, 2.0, 2.6], L_STRIPE)                  # cuffs
    f.both('right_leg', [-0.3, 0, -2.6], [4.3, 1.1, 2.3], L_BLK)
    f.both('right_leg', [0.2, 0, -3.1], [3.8, 0.8, -2.6], L_BLK)

def lexy_mask(px, cm, dark):
    W = (236, 238, 248, 255)
    for y in range(21, 30):
        for x in range(2, 30): px[x, y] = (20, 20, 24, 255)
    for x in range(2, 30):                                                    # a white zigzag across it, like her mask
        px[x, 23 + abs((x + 2) % 8 - 4) * 3 // 4] = W
    for x in range(3, 30, 8):                                                 # little diamonds under the points
        px[x + 1, 27] = W; px[x, 28] = W; px[x + 2, 28] = W; px[x + 1, 29] = W
    for x in (2, 29):
        for y in range(22, 30): px[x, y] = (12, 12, 14, 255)
LEXY = dict(skin=L_SKIN, eyes=L_EYE, hair=[LH1, LH2], paint=lexy_paint,
            brow=['.........', '..kkkkk..', 'kk.......'],
            eye=['..kkkkkk.', 'kkkkkkkkk', '.wOOooOw.', '.wWOpoOw.', '.wOoopOw.', '..wOOOw..', '..kkkkk..'],
            mouth=['......'], mouth_x=13, mouth_y=26, face_extra=[lexy_mask],
            wing=('angel_small', '#f4f2f8'), fit=lexy)

# ====================================================================== MAX (Sintrie)
M_SKIN, M_EYE = '#e8c0a8', '#8a3a2a'
MH1, MH2, MH3 = '#94a6a2', '#c2d8d2', '#e2f4ee'
M_TEE, M_STAR, M_SHORT = '#f8ecd4', '#a8a0e8', '#9a9aa2'

def max_paint(part, face, x, y, W, H, outer):
    if part == 'body':
        if y >= 9: return M_SHORT if y > 9 else '#1a1a1e'
        if face == 'front' and 2 <= y <= 5 and 3 <= x <= 5 and not (y == 2 and x != 4) and not (y == 5 and x == 4): return M_STAR
        return M_TEE
    if part in ('rarm', 'larm'):
        if y < 4: return M_TEE
        if y >= 9: return '#1a1a1e'                                          # black gloves
        return None
    if part in ('rleg', 'lleg'):
        if y < 4: return M_SHORT
        if y >= 8: return '#2a2a30'                                          # socks + shoes
        return None
    return None

def max_fit(f):
    # messy silver-white hair hanging over his eyes
    hair(f, MH1, MH2, back_len=25, side_len=26,
         locks=[([x, 32.3, -4.7], [x * 0.08, -1, -0.45], 3.0 if abs(x) < 2 else 4.4, 2.0) for x in (-3.2, -1.6, 0.0, 1.6, 3.2)]
         + [([s * 4.6, 30.5, z], [s * 0.4, -1, 0.1], 4.6, 1.8) for s in (1, -1) for z in (-2.2, 0.8, 3.4)]
         + [([x, 31.0, 4.7], [x * 0.1, -1, 0.3], 5.2, 2.2) for x in (-2.6, 0.0, 2.6)])
    for sx in (1, -1):
        lock(f, [sx * 0.4, 32.9, -3.6], [sx * 0.9, -0.5, -0.4], 3.6, 2.0, (MH2, MH1, MH3), thick=0.8)        # swept from his centre part
        lock(f, [sx * 0.5, 31.6, -4.8], [sx * 0.05, -1, -0.2], 4.4, 1.0, (MH1, MH2, MH3), thick=0.5)        # thin strands between his eyes
    for x, z in ((-2.2, -1.6), (1.0, -0.4), (2.6, 1.8), (-0.8, 2.2)):
        lock(f, [x, 32.6, z], [x * 0.3, 0.35, -0.8], 3.0, 2.2, (MH2, MH2, MH3), thick=0.9)   # messy, flat tufts
    # cream tee with the lavender star, dark shoulder strap, black belt with pale-blue clips, grey cargo shorts
    shell(f, 'body', [-4, 12.2, -2], [4, 24, 2], 0.3, M_TEE)
    plate(f, 'body', STAR, 0.6, 21.4, -2.42, 0.48, M_STAR)                            # the lavender star
    f.both('right_arm', [3.62, 20.0, -2.38], [8.38, 24.38, 2.38], M_TEE)
    f.both('right_arm', [3.5, 19.6, -2.5], [8.5, 20.4, 2.5], '#1a1a1e')                  # black sleeve trim
    f.add('body', [-1.6, 21.6, -2.45], [1.6, 24.3, -2.3], '#1a1a1e')                       # black collar
    f.add('body', [-4.3, 23.6, -2.45], [4.3, 24.4, 2.45], '#1a1a1e')
    f.add('body', [-4.4, 11.6, -2.45], [4.4, 12.6, 2.45], '#18181c')                    # belt
    for x in (-2.6, 2.6): f.add('body', [x - 0.6, 11.0, -2.7], [x + 0.6, 12.8, -2.45], '#bfe4ec')   # pale-blue clips
    f.both('right_arm', [3.5, 12.0, -2.5], [8.5, 15.0, 2.5], '#1a1a1e', {'bottom': '#1a1a1e'})      # gloves
    f.both('right_leg', [-0.55, 7.2, -2.55], [4.55, 12.3, 2.55], M_SHORT)
    f.both('right_leg', [4.4, 8.0, -1.6], [4.9, 10.6, 1.6], '#8a8a92')                 # cargo pockets
    f.both('right_leg', [-0.32, 1.6, -2.32], [4.32, 4.4, 2.32], '#2a2a30')             # socks
    f.both('right_leg', [-0.36, 0, -2.6], [4.36, 1.8, 2.36], '#3a3a42')
    f.both('right_leg', [0.1, 0, -3.1], [3.9, 1.1, -2.6], '#3a3a42')
    f.both('right_leg', [-0.44, 0, -3.25], [4.44, 0.3, 2.44], '#222226')

def max_blush(px, cm, dark):
    for x in (5, 6, 25, 26): px[x, 21] = (232, 150, 150, 255)
MAX = dict(skin=M_SKIN, eyes=M_EYE, hair=[MH1, MH2], paint=max_paint,
           brow=['.........', 'kkkkkkk..'],
           eye=['mmmmmmmm.', 'kkkkkkkkk', '.wdpppdw.', '.wdpWpdw.', '..wdddw..', '..mkkkm..', '.........'],
           mouth=['..kkk.'], mouth_x=13, mouth_y=25, face_extra=[max_blush],
           wing=('falcon_small', '#c6f0e6'), fit=max_fit)

# ====================================================================== SAM
S_SKIN, S_EYE = '#f0c8a8', '#1a2238'
SH1, SH2, SH3 = '#1a6a64', '#2a8a80', '#40a89a'
S_WHT, S_GRY, S_PANT, S_PUR = '#f2f2f6', '#5a5a64', '#18181c', '#6a3ac8'

def sam_paint(part, face, x, y, W, H, outer):
    if part == 'body':
        if y >= 10: return S_PANT
        if face == 'front' and 1 <= x <= 6: return S_GRY
        return S_WHT
    if part in ('rarm', 'larm'):
        if y < 5: return S_WHT
        if part == 'rarm' and y >= 9: return '#1a1a1e'                      # glove on his right hand
        return None
    if part in ('rleg', 'lleg'):
        if y >= 11: return '#141418'
        if 9 <= y <= 10: return S_WHT if y == 9 else S_PANT                  # white cuff stripes
        return S_PANT
    return None

def sam(f):
    # messy teal-green hair
    hair(f, SH1, SH2, back_len=26, side_len=27,
         locks=fringe_locks(6, 31.8, 7.6, 4.4, 1.8, 0.45)
         + [([x, 32.0, -4.7], [x * 0.12, -1, -0.3], 3.2, 2.0) for x in (-2.8, 1.4, 3.4)]
         + [([s * 4.6, 30.5, z], [s * 0.55, -1, 0.15], 3.8, 1.8) for s in (1, -1) for z in (-2.4, 0.6, 3.2)])
    for x, z in ((-2.2, -1.8), (0.4, 0.6), (2.6, -0.6), (-1.0, 2.6), (2.0, 2.8)):
        lock(f, [x, 32.8, z], [x * 0.25, 0.8, z * 0.15 - 0.3], 2.8, 1.8, (SH2, SH2, SH3))   # spiky top
    # open white hoodie (short sleeves) over a grey tee with a white star, purple drawstrings
    shell(f, 'body', [-4, 12.2, -2], [4, 24, 2], 0.28, S_GRY)
    plate(f, 'body', STAR, 0.0, 19.6, -2.4, 0.42, S_WHT)                              # the white star
    for s in (1, -1):                                                                  # the open hoodie front panels
        x0, x1 = (1.9, 4.5) if s > 0 else (-4.5, -1.9)
        f.add('body', [x0, 11.6, -2.6], [x1, 24.4, -2.3], S_WHT,
              {'front': (lambda s_: lambda x, y: '#9a86d8' if (x < .22 if s_ > 0 else x > .78) else S_WHT)(s)})
        f.add('body', [s * 1.7 - 0.22, 19.8, -2.75], [s * 1.7 + 0.22, 23.8, -2.55], S_PUR)          # drawstrings
    shell(f, 'body', [-4, 11.8, -2], [4, 24.2, 2], 0.42, S_WHT, {'front': lambda x, y: None})
    f.add('body', [-4.2, 21.0, 2.3], [4.2, 24.8, 4.2], S_WHT, {'back': lambda x, y: '#dcdce4' if y > .7 else S_WHT})   # hood down
    f.both('right_arm', [3.5, 19.6, -2.5], [8.5, 24.5, 2.5], S_WHT)
    f.add('right_arm', [3.5, 12.0, -2.5], [8.5, 15.0, 2.5], '#1a1a1e', {'bottom': '#1a1a1e'})   # glove
    f.both('right_leg', [-0.5, 1.6, -2.5], [4.5, 12.3, 2.5], S_PANT,
           {'front': lambda x, y: S_WHT if .78 < y < .84 or .9 < y < .95 else S_PANT, 'right': lambda x, y: S_WHT if .78 < y < .84 or .9 < y < .95 else S_PANT,
            'left': lambda x, y: S_WHT if .78 < y < .84 or .9 < y < .95 else S_PANT, 'back': lambda x, y: S_WHT if .78 < y < .84 or .9 < y < .95 else S_PANT})
    f.both('right_leg', [-0.36, 0, -2.6], [4.36, 1.8, 2.36], '#141418', {'front': lambda x, y: S_WHT if y > .7 else '#141418'})
    f.both('right_leg', [0.1, 0, -3.15], [3.9, 1.1, -2.6], '#141418')
    f.both('right_leg', [-0.44, 0, -3.25], [4.44, 0.4, 2.44], S_WHT)

def sam_marks(px, cm, dark):
    for y in (20, 22):
        for x in range(3, 8): px[x, y] = (110, 60, 200, 255)
        for x in range(24, 29): px[x, y] = (110, 60, 200, 255)
SAM = dict(skin=S_SKIN, eyes=S_EYE, hair=[SH1, SH2], paint=sam_paint,
           brow=['..kkkkk..', 'kk.....k.'],
           eye=['.........', '.........', '..kkkkk..', '.k.....k.', 'k.......k', '.........', '.........'],
           mouth=['kkkkkkkk', 'kwwwwwwk', '.krrrrk.', '..kkkk..'], mouth_x=12, mouth_y=24, face_extra=[sam_marks],
           wing=('hawk_small', '#3a6a4a'), fit=sam)

# ====================================================================== AI-ONI (demon)
A_SKIN, A_EYE = '#f2d6c2', '#d0182a'
AH1, AH2, A_RED = '#121014', '#1e1a22', '#c41e30'
A_GREY, A_GREYD, A_BLK = '#8a8a90', '#6e6e74', '#1a181c'

def aioni_paint(part, face, x, y, W, H, outer):
    if part == 'body':
        if y >= 10: return A_GREY                                            # her long grey top
        return A_GREY
    if part in ('rarm', 'larm'):
        if face == 'bottom': return A_GREYD
        return A_GREY if y < 10 else (A_GREYD if y == 10 else None)          # long sleeves to the wrist
    if part in ('rleg', 'lleg'):
        if y < 2: return A_BLK                                               # black shorts
        if y < 4: return None                                                # bare thigh
        if y == 4: return A_GREY                                             # grey band on the thigh-highs
        if y >= 8:
            return A_BLK
        return A_BLK
    return None

def aioni(f):
    # black bob with red streaks, a little side bun with a red ribbon, cream horns, a gold star clip
    hair(f, AH1, AH2, back_len=25.5, side_len=26,
         locks=fringe_locks(6, 31.8, 7.6, 4.6, 1.8, 0.3) + [([s * 3.9, 31.4, -4.5], [s * 0.12, -1, -0.05], 6.4, 2.2) for s in (1, -1)]
         + [([s * 4.6, 30, z], [s * 0.2, -1, 0.15], 5.2, 1.9) for s in (1, -1) for z in (-1.2, 1.6, 3.8)])
    for x in (-3.9, 3.4):
        f.add('head', [x, 28.6, -4.95], [x + 0.5, 31.8, -4.7], A_RED)                         # red streaks in the side locks
    f.add('head', [4.4, 27.0, 1.0], [4.75, 31.5, 2.2], A_RED)
    f.add('head', [-6.0, 30.0, 1.2], [-3.8, 32.6, 3.6], AH2)                                  # side bun (her left)
    f.add('head', [-6.6, 30.6, 1.6], [-5.6, 32.2, 3.2], AH1)
    f.add('head', [-5.0, 29.4, 1.0], [-4.4, 30.4, 3.8], A_RED)                                # red ribbon
    lock(f, [-5.2, 29.6, 3.6], [-0.2, -1, 0.4], 3.0, 0.9, (A_RED, A_RED, '#e04050'), thick=0.3)
    for sx in (1, -1):                                                                     # cream horns curving back
        core.lock(Adapter(f), 'Head', [sx * 2.4, 32.6, -1.6], [sx * 0.3, 1, 0.35], 3.6, 1.4, 1.4, ('#efe6cc', '#e2d6b2', '#fff6e2'),
                  wide='x', taper=(0.7, 0.35), tip_len=(0.4, 0.35))
    for (dx, dy) in ((0, 0.5), (-0.5, 0), (0.5, 0), (-0.3, -0.5), (0.3, -0.5), (0, 0)):
        f.add('head', [3.3 + dx - 0.25, 30.6 + dy - 0.25, -4.95], [3.3 + dx + 0.25, 30.6 + dy + 0.25, -4.75], '#e8c030')   # star clip
    # long grey top, black open hoodie vest with red trim and a grey hood down
    shell(f, 'body', [-4, 9.4, -2], [4, 24, 2], 0.28, A_GREY)
    for sx in (1, -1):
        x0, x1 = (2.3, 4.55) if sx > 0 else (-4.55, -2.3)
        f.add('body', [x0, 11.0, -2.6], [x1, 24.4, -2.35], A_BLK)
        t0, t1 = (2.3, 2.75) if sx > 0 else (-2.75, -2.3)
        f.add('body', [t0, 11.0, -2.7], [t1, 24.4, -2.55], A_RED, shade=False)                 # red trim
    shell(f, 'body', [-4, 11.0, -2], [4, 24.2, 2], 0.45, A_BLK, {'front': lambda x, y: None})
    f.add('body', [-3.9, 20.8, 2.3], [3.9, 24.8, 4.2], A_GREY, {'back': lambda x, y: A_GREYD if y > .7 else A_GREY})       # hood
    f.both('right_arm', [3.45, 12.0, -2.55], [8.55, 24.4, 2.55], A_GREY, {'bottom': A_GREYD})                               # baggy sleeves
    f.both('right_arm', [3.4, 18.6, -2.6], [8.6, 19.0, 2.6], A_GREYD)
    # black shorts, thigh-highs with a grey band, laced boots with red laces and chunky soles
    f.both('right_leg', [-0.4, 10.0, -2.4], [4.4, 12.3, 2.4], A_BLK)
    f.both('right_leg', [-0.32, 3.4, -2.32], [4.32, 8.0, 2.32], A_BLK)
    f.both('right_leg', [-0.36, 7.4, -2.36], [4.36, 8.2, 2.36], A_GREY)
    f.both('right_leg', [-0.42, 0.0, -2.62], [4.42, 4.0, 2.42], '#121014')
    for yy in (1.0, 1.7, 2.4, 3.1):
        f.both('right_leg', [1.1, yy, -2.74], [2.9, yy + 0.3, -2.6], A_RED)                # red laces, criss-crossed
    f.both('right_leg', [1.85, 0.8, -2.72], [2.15, 3.6, -2.6], '#e04050')
    f.both('right_leg', [0.1, 0.0, -3.2], [3.9, 1.6, -2.6], '#121014')
    f.both('right_leg', [-0.5, 0.0, -3.35], [4.5, 0.6, 2.5], '#2a2a2e')                    # chunky soles

def aioni_marks(px, cm, dark):
    for y in range(21, 25): px[6, y] = (200, 20, 36, 255); px[25, y] = (200, 20, 36, 255)   # red tear streaks
AIONI = dict(skin=A_SKIN, eyes=A_EYE, hair=[AH1, AH2], paint=aioni_paint,
             brow=['.........', '..kkkkkk.', 'kk.......'],
             eye=['..kkkkkk.', 'kkkkkkkkk', '.wOOooOw.', '.wWOpoOw.', '.wOoopOw.', '..wOOOw..', '..kkkkk..'],
             mouth=['kkkkkkkk', 'kWrrrrWk', '.krrrrk.', '..kkkk..'], mouth_x=12, mouth_y=24, face_extra=[aioni_marks],
             wing=('demon', '#c42a38'), fit=aioni)

# ====================================================================== TECHNICA ("Tech")
T_SKIN, T_EYE = '#f4dcd0', '#e0408a'
TH1, TH2, TH3 = '#8a1a5a', '#c42a7e', '#e858a4'
T_COAT, T_COATD, T_PINK, T_BLK = '#f2f0f4', '#d6d2dc', '#ec5aa0', '#1a181e'

def tech_paint(part, face, x, y, W, H, outer):
    if part == 'body':
        if y >= 10: return T_BLK
        return T_BLK
    if part in ('rarm', 'larm'):
        if y >= 10: return '#f4f4f6'                                         # white gloves
        return T_COAT
    if part in ('rleg', 'lleg'):
        if y >= 8: return '#9a9aa2' if y != 8 else '#f2a8cc'                 # grey boots, pink band
        return T_BLK
    return None

def technica(f):
    # magenta hair fading darker at the ends, a messy bun with a flower crown, a long lock on one side, a headphone
    hair(f, TH2, TH1, back_len=25, side_len=25.5,
         locks=fringe_locks(6, 31.8, 7.6, 4.4, 1.8, 0.35) + [([3.9, 31.2, -4.4], [0.15, -1, -0.1], 6.0, 2.2)]
         + [([-4.2, 30.8, -3.4], [-0.2, -1, -0.15], 11.0, 2.4)]
         + [([s * 4.6, 30, z], [s * 0.3, -1, 0.15], 5.0, 1.9) for s in (1, -1) for z in (0.0, 2.8)])
    f.add('head', [2.2, 30.6, 0.4], [6.0, 34.6, 4.4], TH2, {'top': TH3, 'front': lambda x, y: TH3 if int(x * 6 + y * 4) % 3 == 0 else TH2})   # messy bun
    f.add('head', [2.8, 34.0, 1.0], [5.4, 35.4, 3.8], TH3)
    for (x, y, z) in ((2.2, 34.2, 0.2), (3.6, 35.0, 0.2), (5.0, 34.6, 0.2), (6.1, 33.4, 1.2), (6.1, 31.8, 2.6)):   # flower crown
        f.add('head', [x - 0.5, y - 0.5, z - 0.4], [x + 0.5, y + 0.5, z + 0.4], '#d86ad8')
        f.add('head', [x - 0.2, y - 0.2, z - 0.5], [x + 0.2, y + 0.2, z - 0.35], '#f6c8f0')
    f.add('head', [4.2, 26.4, -1.6], [5.4, 29.6, 1.6], '#5a1238', {'right': lambda x, y: '#c42a7e' if (x - .5) ** 2 + (y - .5) ** 2 < .06 else '#5a1238'})   # headphone
    f.add('head', [3.6, 29.4, -0.6], [4.6, 33.0, 0.6], '#3a0c26')
    # pink-tinted rectangular glasses
    for sx in (1, -1):
        x0, x1 = (0.5, 3.7) if sx > 0 else (-3.7, -0.5)
        F = '#2a1a24'
        f.add('head', [x0, 28.6, -4.8], [x1, 28.95, -4.6], F, shade=False)          # rims as thin bars
        f.add('head', [x0, 26.9, -4.8], [x1, 27.2, -4.6], F, shade=False)
        f.add('head', [x0, 26.9, -4.8], [x0 + 0.3, 28.95, -4.6], F, shade=False)
        f.add('head', [x1 - 0.3, 26.9, -4.8], [x1, 28.95, -4.6], F, shade=False)
    f.add('head', [-0.5, 28.2, -4.75], [0.5, 28.6, -4.6], '#2a1a24')
    for sx in (1, -1):
        x0, x1 = (3.7, 4.1) if sx > 0 else (-4.1, -3.7)
        f.add('head', [x0, 28.3, -4.7], [x1, 28.7, -1.0], '#2a1a24')
    # black top with a pink collar and the C3 logo, open white lab coat with pink bands
    shell(f, 'body', [-4, 11.6, -2], [4, 24, 2], 0.26, T_BLK)
    f.add('body', [-1.4, 22.6, -2.42], [1.4, 24.4, -2.25], T_PINK)                          # pink collar
    f.add('body', [-0.25, 20.0, -2.44], [0.25, 23.0, -2.28], '#8a8a92')                     # zip
    C3 = ['.kkkkk.', 'k.....k', 'k.kkk..', 'k...k..', 'k.kkk..', 'k...k.k', 'k.kkk.k', '.kkkkk.']
    plate(f, 'body', C3, 0.0, 19.8, -2.42, 0.36, T_PINK)
    for sx in (1, -1):
        x0, x1 = (2.0, 4.55) if sx > 0 else (-4.55, -2.0)
        f.add('body', [x0, 9.0, -2.6], [x1, 24.4, -2.35], T_COAT)
    shell(f, 'body', [-4, 9.0, -2], [4, 24.2, 2], 0.45, T_COAT, {'front': lambda x, y: None})
    f.add('body', [-3.6, 23.4, 1.9], [3.6, 25.2, 2.9], T_COATD)                             # collar
    f.both('right_arm', [3.45, 13.0, -2.55], [8.55, 24.4, 2.55], T_COAT, {'bottom': T_COATD})
    f.both('right_arm', [3.4, 19.4, -2.6], [8.6, 20.8, 2.6], T_PINK)                        # pink sleeve bands
    f.both('right_arm', [3.4, 13.0, -2.6], [8.6, 14.0, 2.6], T_PINK)                        # pink cuffs
    f.both('right_arm', [3.5, 12.0, -2.5], [8.5, 13.2, 2.5], '#f6f6f8', {'bottom': '#e8e8ec'})   # white gloves
    # black pants, chunky grey boots with a pink top
    f.both('right_leg', [-0.35, 3.6, -2.35], [4.35, 12.3, 2.35], T_BLK)
    f.both('right_leg', [-0.55, 0.0, -2.75], [4.55, 4.4, 2.55], '#9a9aa2', {'top': '#f2a8cc'})
    f.both('right_leg', [-0.6, 3.6, -2.8], [4.6, 4.6, 2.6], '#f2a8cc')
    f.both('right_leg', [0.0, 0.0, -3.3], [4.0, 1.6, -2.7], '#8a8a92')
    f.both('right_leg', [-0.6, 0.0, -3.4], [4.6, 0.5, 2.6], '#6a6a72')

TECHNICA = dict(skin=T_SKIN, eyes=T_EYE, hair=[TH1, TH2], paint=tech_paint,
                brow=['.........', '..kkkkk..', 'kk.......'],
                eye=['..kkkkkk.', 'kkkkkkkkk', '.wOOooOw.', '.wWOpoOw.', '.wOoopOw.', '..wOOOw..', '..kkkkk..'],
                mouth=['..kkkk..'], mouth_x=12, mouth_y=25, wing=('tech', '#e8a0c4'), fit=technica)

CHARS = {'kyrie': KYRIE, 'lexy': LEXY, 'max': MAX, 'sam': SAM, 'aioni': AIONI, 'technica': TECHNICA}

# ---------------------------------------------------------------------- building
def face(spec, **kw):
    s = dict(spec); s.update(kw)
    red = 'r' in ''.join(s.get('mouth', [])) or 'R' in ''.join(s.get('mouth', []))
    mouth = s.get('mouth', [])
    s['mouth'] = [row.replace('r', 'k').replace('R', 'k') for row in mouth]
    im = face_img(s)
    if red:
        px = im.load()
        for j, row in enumerate(mouth):
            for i, ch in enumerate(row):
                if ch == 'r': px[s.get('mouth_x', 12) + i, s.get('mouth_y', 24) + j] = (196, 30, 44, 255)
                if ch == 'R': px[s.get('mouth_x', 12) + i, s.get('mouth_y', 24) + j] = (236, 80, 90, 255)
    return im

def wings(style, colour, atlas):
    """Merge a wing style's cubes in (UVs at the atlas top), recoloured from its light greys to `colour`."""
    wj = json.load(open(RES + f'models/detail/npc/wing_{style}.json'))
    wt = Image.open(RES + f'textures/entity/npc/wing_{style}_details.png').convert('RGBA')
    tgt = C(colour); src = wt.load(); dst = atlas.load()
    for y in range(wt.height):
        for x in range(wt.width):
            p = src[x, y]
            if p[3] == 0: continue
            l = (p[0] + p[1] + p[2]) / 3 / 235.0
            dst[x, y] = tuple(max(0, min(255, int(c * (0.45 + 0.75 * l)))) for c in tgt) + (255,)
    return [c for c in wj['bones']['body']['children'] if c.get('name') in ('right_wing', 'left_wing')]

def wing_rows(style):
    wj = json.load(open(RES + f'models/detail/npc/wing_{style}.json')); m = [0]
    def w(n):
        for c in n.get('cubes', []): W, H, D = c['size']; m[0] = max(m[0], c['uv'][1] + math.ceil(D + H) + 1)
        for ch in n.get('children', []): w(ch)
    for c in wj['bones']['body']['children']: w(c)
    return m[0] + 1

def make(key):
    spec = CHARS[key]
    fit = Fit(); spec['fit'](fit)
    atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    style, col = spec['wing']
    pack(fit, atlas, region=(0, wing_rows(style), 256, 256))
    w = wings(style, col, atlas)
    skin = paint_skin(spec)
    return fit, atlas, skin, w

def export(key, path):
    fit, atlas, skin, w = make(key)
    mj = npcs.export(fit, path)
    mj['bones']['body']['children'] = [c for c in mj['bones']['body']['children'] if c.get('name') not in ('right_wing', 'left_wing')] + w
    json.dump(mj, open(path, 'w'), separators=(',', ':'))
    return mj, fit, atlas, skin

if __name__ == '__main__':
    tiles = []
    for key in (sys.argv[1:] or CHARS):
        mj, fit, atlas, skin = export(key, f'/tmp/sp_{key}.json')
        fc = face(CHARS[key])
        tiles.append(U.preview(mj, skin, atlas, fc, yaws=(25, 0, 200), scale=8, wide=44, tall=46, top=12))
        print(key, len(fit.cubes))
    w = max(t.width for t in tiles); h = sum(t.height for t in tiles)
    S = Image.new('RGB', (w, h), (30, 32, 40)); y = 0
    for t in tiles: S.paste(t, (0, y)); y += t.height
    S.save('/tmp/sp_preview.png'); print(S.size)
