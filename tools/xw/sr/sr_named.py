"""The named Sky Rebellion bosses: Senju (the SR leader), Logan, Kill, Keita, Maddie, and the demons Persephone and Ava.
Each gets a skin, 3D details, an HD face; Persephone and Ava also get a demon form (horns, red eyes)."""
import sys, math
sys.path.insert(0, '/home/claude/xw/npcs'); sys.path.insert(0, '/home/claude/xw/outfits'); sys.path.insert(0, '/home/claude/xw/detail'); sys.path.insert(0, '/home/claude/xw/ultima')
import npcs, core
from npcs import Fit, pack, shell, pants, shoes, tee, clothes, paint_skin, face_img, export, hair, fringe_locks, crown_spikes, Adapter, TEX, MOD
from PIL import Image, ImageDraw
import ultima as U

BLK, BLK2, WHT, RED = '#141217', '#1f1c24', '#ececec', '#d42a2a'

def lock(f, root, d, ln, w, cols, thick=0.8, taper=(0.62, 0.3)):
    core.lock(Adapter(f), 'Head', root, d, ln, w, thick, cols, wide='x', taper=taper)

def horn(f, root, d, ln, w, col, tip=None):
    core.lock(Adapter(f), 'Head', root, d, ln, w, w, (col, col, tip or col), wide='x', taper=(0.7, 0.4), tip_len=(0.35, 0.3))

def hood(f, rim=None, inner='#2a1a1a', tail=True, col=BLK):
    """SR hood up: black, optional red rim framing the face (Kill/Keita/Logan's red-rimmed hood)."""
    f.add('head', [-4.75, 23.9, -3.4], [4.75, 33.4, 4.9], col, {'top': lambda x, y: BLK2 if col == BLK and abs(x - .5) < .08 else col})
    f.add('head', [-4.75, 31.4, -5.1], [4.75, 33.4, -3.4], col)              # brim over the brow
    for s in (1, -1):
        x0, x1 = (3.9, 4.75) if s > 0 else (-4.75, -3.9)
        f.add('head', [x0, 24.4, -5.1], [x1, 31.4, -3.4], col)
    if rim:
        f.add('head', [-3.95, 30.8, -5.25], [3.95, 31.5, -5.05], rim)             # red rim: brow (the V dips in the middle)
        f.add('head', [-0.7, 30.2, -5.3], [0.7, 30.9, -5.05], rim)
        for s in (1, -1):
            x0, x1 = (3.25, 3.95) if s > 0 else (-3.95, -3.25)
            f.add('head', [x0, 24.4, -5.25], [x1, 31.5, -5.05], rim)
    if tail:                                                                  # the hood's pointed tail flopping back
        f.add('head', [-1.6, 31.0, 4.9], [1.6, 33.0, 6.6], col)
        f.add('head', [-1.0, 30.0, 6.6], [1.0, 32.0, 8.0], col)

def hoodie_body(f, trim, eye=True, eye_col=WHT):
    shell(f, 'body', [-4, 11.5, -2], [4, 24, 2], 0.45, BLK2)
    f.add('body', [-4.5, 11.5, -2.6], [4.5, 12.6, 2.6], trim)
    if eye:
        f.add('body', [-2.2, 16.8, -2.75], [2.2, 19.6, -2.5], eye_col)
        f.add('body', [-3.0, 17.6, -2.7], [3.0, 18.8, -2.5], eye_col)
        f.add('body', [-0.8, 17.4, -2.85], [0.8, 19.0, -2.7], '#141218')
    f.both('right_arm', [3.5, 13.0, -2.45], [8.5, 24.4, 2.45], BLK2)
    f.both('right_arm', [3.45, 12.0, -2.55], [8.55, 13.4, 2.55], trim)

# ---------------------------------------------------------------- faces (32x32 HD face area; eyes at (3,14) & (20,14))
SHARP = ['kkkkkkkkkk', 'kWkWkWkWkk', '.kWkWkWk..']           # a grin of sharp teeth
def marks_kill(px, cm, dark):                                 # Kill's black slash marks under his left eye
    for i, (x, y, l) in enumerate([(22, 21, 6), (24, 21, 5), (26, 21, 4), (21, 22, 3)]):
        for j in range(l): px[x - j // 2 if i % 2 else x + j // 3, y + j] = (14, 10, 12, 255)
    for j in range(6): px[20 + j, 20] = (14, 10, 12, 255)
def crescents(px, cm, dark):                                  # Logan's black crescents under both eyes
    for cx in (4, 26):
        for j, (dx, dy) in enumerate([(0, 0), (1, 1), (2, 1), (3, 1), (4, 0)]): px[cx + dx - 2, 23 + dy] = (16, 12, 14, 255)
def freckles(px, cm, dark):
    for x, y in [(4, 23), (6, 22), (7, 24), (5, 25), (24, 22), (26, 23), (25, 25), (27, 25)]: px[x, y] = (90, 52, 40, 255)
def scar(px, cm, dark):                                       # Senju's dark slash down through his right eye
    for j in range(13): px[6 - j // 6, 11 + j] = (20, 14, 16, 255)
def blush(px, cm, dark):
    for x in list(range(3, 8)) + list(range(24, 29)):
        px[x, 23] = (226, 130, 120, 255)
def mole(px, cm, dark): px[7, 22] = (30, 18, 16, 255)
def star_eye(side):                                           # a little white star in the eye (Kill, Keita)
    def fn(px, cm, dark):
        x, y = (6, 16) if side == 0 else (23, 16)
        for dx, dy in [(0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)]: px[x + dx, y + dy] = (255, 255, 255, 255)
    return fn
def ava_marks(px, cm, dark):                                  # demon Ava: red slashes on the cheeks, black lines
    for cx, s in ((5, 1), (26, -1)):
        for j in range(4): px[cx + s * j // 2, 22 + j] = (214, 28, 32, 255); px[cx + s * (j // 2 + 2), 22 + j] = (214, 28, 32, 255)
    px[7, 22] = (30, 18, 16, 255)

EYE_ROUND = ['..kkkkkk.', 'kkkkkkkkk', '.wddpddw.', '.wWdppdw.', '.wWoopow.', '..wOOOw..', '..kkkkk..']
EYE_NARROW = ['.........', 'kkkkkkkkk', '.wddppdw.', '.wWoppow.', '..wOOOw..', '..kkkkk..', '']
EYE_SPIRAL = ['..kkkkkk.', 'kkkkkkkkk', '.wdpdpdw.', '.wpWppdw.', '.wdpopdw.', '..wpdpw..', '..kkkkk..']

# ---------------------------------------------------------------- the fits
def senju(f):
    """Senju, the SR leader (manga ref): black hood with a grey inner rim dipping to a V, black hair over his face,
    a grey wrap-front tunic with two rings on the chest and fishnet in the V, dark belt, fishnet arm guards,
    black fingerless gloves, baggy grey trousers, tall black boots, black cape."""
    TUN, TUN2, NET, CAPE = '#7a5434', '#5e3e26', '#3a2a1e', '#3e2818'     # he wears brown
    hood(f, rim='#8a6644', tail=True, col=CAPE)
    for x, ln in ((-3.1, 5.5), (-0.4, 2.6), (1.2, 2.2), (3.1, 5.0)):
        lock(f, [x, 31.2, -4.9], [0.06 * x, -1, -0.12], ln, 1.4 if abs(x) > 2 else 1.6, ('#0e0c10', '#0e0c10', '#1a1620'))
    def wrap(x, y):                                       # wrap-front: dark diagonal edge from his right shoulder to the left hip
        edge = 0.62 - (1 - y) * 0.45
        if y > .72 and abs(x - .5) < (y - .72) * 1.6: return NET if (int(x * 24) + int(y * 40)) % 2 else '#141216'
        if abs(x - edge) < .045: return '#141216'
        return TUN
    shell(f, 'body', [-4, 11.0, -2], [4, 24, 2], 0.4, TUN)
    f.add('body', [-1.7, 21.2, -2.55], [1.7, 24.2, -2.42], NET)                       # fishnet in the V
    f.add('body', [-0.9, 20.2, -2.55], [0.9, 21.2, -2.42], NET)
    f.add('body', [-0.25, 13.6, -2.6], [0.25, 22.6, -2.45], '#141216', rot=[0, 0, 22], origin=[0, 18, -2.5])   # wrap-front edge
    for x in (-2.6, 1.0):                                  # the two rings
        f.add('body', [x, 20.0, -2.6], [x + 1.6, 21.6, -2.45], '#d8d4dc', {'front': lambda u, v: (0, 0, 0, 0) if abs(u - .5) < .2 and abs(v - .5) < .2 else '#d8d4dc'})
    f.add('body', [-4.5, 12.4, -2.55], [4.5, 13.6, 2.55], '#1e1c22')                 # belt
    f.add('body', [-1.0, 12.5, -2.7], [0.6, 13.5, -2.55], '#6a6670')
    f.add('body', [-4.4, 4.0, 2.2], [4.4, 24.2, 3.0], CAPE,
          {'back': lambda x, y: '#4a3220' if int(x * 9) % 3 == 0 else CAPE})        # cape
    f.add('body', [-4.8, 22.4, -2.7], [4.8, 24.6, 3.0], CAPE)                   # cape over the shoulders
    f.both('right_arm', [3.45, 15.0, -2.45], [8.55, 24.4, 2.45], TUN)                # long sleeves
    f.both('right_arm', [3.4, 14.0, -2.5], [8.6, 18.0, 2.5], NET,
           {k: (lambda x, y: '#4a4650' if (int(x * 8) + int(y * 8)) % 2 else NET) for k in ('front', 'back', 'right', 'left')})   # fishnet guards
    f.both('right_arm', [3.35, 12.0, -2.55], [8.65, 14.0, 2.55], '#0e0c10')          # fingerless gloves
    pants(f, TUN2, 6.0, baggy=0.45)
    f.both('right_leg', [-0.55, 0, -2.75], [4.55, 6.6, 2.55], '#0e0c10')            # tall boots
    f.both('right_leg', [0.1, 0, -3.3], [3.9, 1.3, -2.75], '#0e0c10')

def kill(f):
    hood(f, rim=RED)
    for x in (-2.6, -0.8, 1.2, 2.8):
        lock(f, [x, 31.0, -4.9], [0.1 * x, -1, -0.2], 2.4, 1.4, ('#121014', '#121014', '#1e1a22'))
    sr_uniform(f)

def keita(f):
    hood(f, rim=RED)
    # dark blue hair over his right eye
    for x, ln in ((-2.8, 5.0), (-1.2, 6.2), (0.6, 3.0), (2.4, 2.6)):
        lock(f, [x, 31.0, -4.9], [0.15, -1, -0.2], ln, 1.6, ('#1a1e6a', '#1a1e6a', '#2a32a0'))
    for s in (1, -1): lock(f, [s * 3.6, 28.0, -4.6], [s * 0.4, -1, -0.3], 3.5, 1.2, ('#1a1e6a', '#2a32a0', '#2a32a0'))
    sr_uniform(f)

def sr_uniform(f):
    """The SR uniform from the manga: hooded black tee with rolled cuffs and the SR eye, drawstring, rolled-up black capris, bare shins."""
    tee(f, BLK2, 19.2, cuff=BLK)
    f.both('right_arm', [3.45, 18.8, -2.55], [8.55, 19.8, 2.55], '#2e2b34')       # rolled cuffs
    f.add('body', [-2.2, 17.0, -2.45], [2.2, 19.8, -2.3], WHT)                   # the SR eye
    f.add('body', [-3.0, 17.8, -2.4], [3.0, 19.0, -2.3], WHT)
    f.add('body', [-0.8, 17.6, -2.55], [0.8, 19.2, -2.42], '#141218')
    f.add('body', [-4.4, 11.8, -2.45], [4.4, 12.4, 2.45], BLK)
    f.add('body', [-0.9, 10.4, -2.6], [-0.5, 12.2, -2.45], '#f2f2f2')            # drawstrings
    f.add('body', [0.5, 10.6, -2.6], [0.9, 12.2, -2.45], '#f2f2f2')
    pants(f, '#121014', 5.0, '#26232c', baggy=0.3)
    shoes(f, '#141216', '#0a090c', 1.8)

def logan(f):
    """Logan (manga + colour refs): black hood up with the red rim, blond hair spilling out the front, black tee with
    rolled cuffs and the white SR eye, white drawstring, black rolled-up capris, bare shins, black shoes."""
    hood(f, rim=RED)
    for x, ln in ((-3.0, 4.4), (-1.4, 3.0), (0.4, 4.0), (2.0, 2.6), (3.2, 4.6)):
        lock(f, [x, 31.0, -4.95], [0.12 * x, -1, -0.25], ln, 1.7, ('#c8a034', '#d8b040', '#e0c060'))
    f.add('head', [-3.4, 30.2, -5.1], [-1.4, 30.7, -4.95], '#f2d040')            # hair clips
    f.add('head', [1.4, 30.2, -5.1], [3.4, 30.7, -4.95], '#f2d040')
    sr_uniform(f)

def maddie(f):
    # black on top fading to grey, long, wispy; cream horns; red-tied side braids; diamond clip
    hair(f, '#121014', '#6a6670', back_len=17, side_len=22, spikes=[([1.2, 33.0, -1.5], [0.8, 0.9, 0.4], 4.0, 1.4)],
         locks=fringe_locks(6, 31, 7.6, 4.2, 1.6, 0.3)
         + [([s * 4.6, 30, z], [s * 0.35, -1, 0.1], 9.0, 1.8) for s in (1, -1) for z in (-2.4, 0.4, 3.0)]
         + [([x, 30, 4.7], [0, -1, 0.15], 11.0, 2.2) for x in (-2.8, 0, 2.8)])
    for s in (1, -1):                                                              # grey ends + red ties on the braids
        f.add('head', [s * 4.6 - 0.9, 22.0, -2.4 - 0.9], [s * 4.6 + 0.9, 24.5, -2.4 + 0.9], '#a8a4b0')
        f.add('head', [s * 4.6 - 1.0, 23.6, -2.4 - 1.0], [s * 4.6 + 1.0, 24.4, -2.4 + 1.0], '#d42020')
        horn(f, [s * 3.6, 32.4, -1.8], [s * 1.0, 0.45, -0.1], 4.6, 2.0, '#f0dcc8', '#f8ece0')
    f.add('head', [-1.8, 31.4, -4.95], [-0.4, 32.6, -4.75], '#f6f6f6')            # diamond clip
    # black crop top with white puff sleeves and collar
    shell(f, 'body', [-4, 17.0, -2], [4, 24, 2], 0.3, '#141217')
    f.add('body', [-3.2, 22.6, -2.55], [3.2, 24.3, -2.3], '#f2f2f2')               # collar
    f.both('right_arm', [3.4, 20.6, -2.6], [8.6, 24.6, 2.6], '#f2f2f2')            # puff sleeves
    f.both('right_arm', [3.3, 20.2, -2.7], [8.7, 20.8, 2.7], '#141217')
    # white asymmetric skirt
    f.add('body', [-4.5, 8.6, -2.5], [4.5, 13.2, 2.5], '#f4f4f4',
          {k: (lambda x, y: '#c8c8cc' if y > .85 - x * .5 else '#f4f4f4') for k in ('front', 'back', 'right', 'left')})
    f.add('body', [-4.5, 13.2, -2.45], [4.5, 13.8, 2.45], '#141217')
    # white leggings, black boots with silver studs, red shoes
    f.both('right_leg', [-0.32, 1.4, -2.32], [4.32, 6.4, 2.32], '#141217', {'front': lambda x, y: '#c8ccd4' if abs(x - .25) < .1 and int(y * 4) % 2 == 0 else '#141217'})
    shoes(f, '#d42020', '#f4f4f4', 1.6)

def persephone(f, demon=False):
    P1, P2 = '#3a1258', '#1a0a26'
    hair(f, P2, P1, back_len=16, side_len=21,
         locks=fringe_locks(6, 31, 7.6, 4.4, 1.7, 0.35) + [([-1.0, 31.6, -4.7], [0.2, -1, -0.2], 5.5, 2.2)]
         + [([s * 4.4, 29.5, 3.0], [s * 0.45, -1, 0.35], 13.0, 2.2) for s in (1, -1)]          # long twin tails
         + [([s * 4.6, 30, -2.0], [s * 0.3, -1, 0], 6.0, 1.6) for s in (1, -1)])
    # the big black braided loops on top
    for s in (1, -1):
        for i in range(11):
            a = math.pi * (0.05 + i / 10 * 1.15)
            cx, cy = s * (4.2 + 2.6 * (1 - math.cos(a))), 32.0 + 3.6 * math.sin(a)
            f.add('head', [cx - 0.9, cy - 0.9, -0.9], [cx + 0.9, cy + 0.9, 0.9], '#0c080e' if i % 2 else '#1a1220')
        f.add('head', [s * 2.4 - 1.6, 32.6, -3.0], [s * 2.4 + 1.6, 33.4, -0.6], '#f2f2f2')   # white streaks
        # white-striped braid ties on the tails
        for k in range(3):
            y = 22.0 - k * 1.6
            f.add('head', [s * 5.6 - 0.9, y, 3.4 - 0.4 * k], [s * 5.6 + 0.9, y + 0.6, 5.2 - 0.4 * k], '#f2f2f2' if k % 2 == 0 else '#0c080e')
    if demon:
        for s in (1, -1): horn(f, [s * 1.6, 33.0, -2.2], [s * 0.35, 1, -0.2], 3.6, 1.5, '#b88ad8', '#e0c8f4')
    tee(f, '#7a2aa8', 21.0, '#d6b0f0')
    f.add('body', [-4.4, 12.0, -2.4], [4.4, 13.0, 2.4], '#d6b0f0')                 # lavender hem
    f.both('right_arm', [3.4, 12.0, -2.6], [8.6, 15.0, 2.6], '#2a1236')             # fingerless gloves
    f.both('right_arm', [3.3, 14.6, -2.7], [8.7, 15.2, 2.7], '#9a3ad0')
    pants(f, '#2a1236', 1.6)
    shoes(f, '#141216', '#d6b0f0', 2.2)

def ava(f, demon=False):
    hair(f, '#0c0a0e', '#16121a', back_len=24, side_len=24.5, spikes=crown_spikes(10, 2.4, 1.6, seed=57),
         locks=fringe_locks(6, 31, 7.6, 4.4, 1.6, 0.4) + [([1.4, 31.6, -4.7], [0.3, -1, -0.2], 5.4, 2.0)]
         + [([s * 4.6, 29, z], [s * 0.6, -1, 0.2], 4.0, 1.7) for s in (1, -1) for z in (-2.4, 0.6, 3.4)])
    for s in (1, -1):                                                               # white streaks
        f.add('head', [s * 3.0 - 1.2, 32.4, -3.2], [s * 3.0 + 1.2, 33.3, -1.0], '#f2f2f2')
        f.add('head', [s * 4.55, 30.4, -1.0], [s * 4.55 + s * 0.3, 31.4, 1.6], '#f2f2f2')
    if demon:
        for s in (1, -1): horn(f, [s * 1.4, 33.0, -2.0], [s * 0.25, 1, 0.1], 3.8, 1.5, '#f2f2f2', '#c8c8cc')
    top = '#c02028' if demon else '#3a3a3e'
    shell(f, 'body', [-4, 13.0, -2], [4, 24, 2], 0.26, top, {'front': lambda x, y: (top if .18 < x < .82 or y < .82 else (0, 0, 0, 0))})  # tank top
    f.add('body', [-4.4, 12.4, -2.4], [4.4, 13.2, 2.4], '#f06a78')                  # pink ruffle
    pants(f, '#121014', 6.0, '#f06a78', baggy=0.25)
    f.both('right_leg', [-0.5, 5.2, -2.5], [4.5, 5.6, 2.5], '#f2f2f2')
    f.both('right_leg', [-0.3, 1.6, -2.3], [4.3, 3.8, 2.3], '#f06a78')              # pink socks
    shoes(f, '#f06a78', '#f2f2f2', 1.8, lace='#f2f2f2')

def bare(skin, shirt, pantsc, shoesc, sleeve=12, pl=12):
    return clothes(shirt, sleeve, pantsc, pl, shoesc, 3)

SPECS = {
    'sr_senju': dict(name='Senju (SR Leader)', fit=senju, skin='#a87458', eyes='#d01818', hair=['#0e0c10', '#1a1620'],
                     paint=bare(0, '#7a5434', '#5e3e26', '#0e0c10'), eye=EYE_NARROW, brow=['.........', 'kkkkkk...'], mouth=['.kk.'], mouth_x=14, mouth_y=25, face_extra=[scar],
                     wing=('falcon', (0.16, 0.13, 0.15))),
    'sr_logan': dict(name='Logan', fit=logan, skin='#e2b096', eyes='#b81818', hair=['#c8a034', '#8a6420'],
                     paint=bare(0, BLK2, '#121014', '#141216', 5, 7), eye=EYE_SPIRAL, mouth=SHARP, mouth_x=11, mouth_y=24, face_extra=[crescents],
                     wing=('hawk', (1.0, 0.86, 0.3))),
    'sr_kill': dict(name='Kill', fit=kill, skin='#d6a088', eyes='#3a2218', hair=['#121014', '#1e1a22'],
                    paint=bare(0, BLK2, '#121014', '#141216', 5, 7), mouth=['kkkkkkkk', '.WWWWWW.', '..kkkk..'], mouth_x=12, mouth_y=24,
                    face_extra=[marks_kill, star_eye(0), star_eye(1)], wing=('tattered', (0.3, 0.26, 0.34))),
    'sr_keita': dict(name='Keita', fit=keita, skin='#dca890', eyes='#d01818', hair=['#1a1e6a', '#2a32a0'],
                     paint=bare(0, BLK2, '#121014', '#141216', 5, 7), mouth=SHARP, mouth_x=11, mouth_y=24, face_extra=[blush, star_eye(0)],
                     wing=('bat', (0.18, 0.2, 0.42))),
    'sr_maddie': dict(name='Maddie', fit=maddie, skin='#f4ddd2', eyes='#d01818', hair=['#121014', '#6a6670'],
                      paint=bare(0, '#141217', '#f4f4f4', '#d42020', 4, 12), eye=EYE_NARROW, mouth=['.kkkk.'], mouth_x=13, mouth_y=25,
                      wing=('angel_maddie', (1.0, 1.0, 1.0))),
    'sr_persephone': dict(name='Persephone', fit=persephone, skin='#e8b8a0', eyes='#6a2aa8', hair=['#1a0a26', '#3a1258'],
                          paint=bare(0, '#7a2aa8', '#2a1236', '#141216', 4, 12), mouth=['..kkk.', '.k....'], mouth_x=13, mouth_y=25, face_extra=[freckles]),
    'sr_ava': dict(name='Ava', fit=ava, skin='#9a6044', eyes='#2a1810', hair=['#0c0a0e', '#16121a'],
                   paint=bare(0, '#3a3a3e', '#121014', '#f06a78', 0, 6), eye=EYE_ROUND, mouth=['.kk.'], mouth_x=14, mouth_y=25, face_extra=[mole]),
}
# demon forms
SPECS['sr_persephone_demon'] = dict(SPECS['sr_persephone'], name='Persephone (demon)', fit=lambda f: persephone(f, True), eyes='#d01818',
                                    eye=EYE_SPIRAL, mouth=['.kkkkk.', '..WkW..'], mouth_x=12, mouth_y=25)
SPECS['sr_ava_demon'] = dict(SPECS['sr_ava'], name='Ava (demon)', fit=lambda f: ava(f, True), eyes='#d01818', eye=EYE_SPIRAL,
                             paint=bare(0, '#c02028', '#121014', '#f06a78', 0, 6), face_extra=[ava_marks, star_eye(0)])

def maddie_wings():
    """The angel wings, white with black tips (Maddie's)."""
    im = Image.open(TEX + 'wing_angel_details.png').convert('RGBA'); px = im.load()
    W, H = im.size
    # find each opaque pixel's brightness; darken the lower/outer feathers: a band in texture-y
    out = im.copy(); po = out.load()
    for y in range(H):
        for x in range(W):
            r, g, b, a = px[x, y]
            if a == 0: continue
            l = (r + g + b) / 3
            po[x, y] = (int(min(255, l * 1.08 + 20)),) * 3 + (a,)
    out.save(TEX + 'wing_angel_maddie_details.png')
    return out

if __name__ == '__main__':
    import json, shutil
    tiles = []
    for key, spec in SPECS.items():
        fit = Fit(); spec['fit'](fit)
        atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); pack(fit, atlas, region=(0, 0, 256, 256))
        skin = paint_skin(spec); face = face_img(spec)
        skin.save(TEX + key + '.png'); atlas.save(TEX + key + '_details.png'); face.save(TEX + key + '_face.png')
        mj = export(fit, MOD + key + '.json')
        im = U.preview(mj, skin, atlas, face, yaws=(20, 200), scale=6)
        ImageDraw.Draw(im).text((6, 6), spec['name'], fill=(255, 255, 255)); tiles.append(im)
    tw, th = tiles[0].size
    cols = 3
    sheet = Image.new('RGB', (tw * cols, th * ((len(tiles) + cols - 1) // cols)), (30, 30, 36))
    for i, t in enumerate(tiles): sheet.paste(t, ((i % cols) * tw, (i // cols) * th))
    sheet.save('/tmp/sr_named.png'); print(sheet.size)
