"""Every expression the game uses, drawn for Ultima (his own, not copies), plus talking mouths. Sheet of all of them."""
import sys, os
sys.path.insert(0, '/home/claude/xw/ultima')
import ultima as U
from PIL import Image, ImageDraw, ImageFont
CH = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/textures/entity/character/'
title, build, gloves, sleek = U.OPTIONS['A']
spec0 = dict(U.SPEC); spec0['paint'] = U.body_paint(gloves, sleek)

# ---- brows
BF = ['.........', 'kkkkkkk..', '.........']          # flat (bored)
BE = ['......kkk', '...kkk...', 'kkk......']          # evil / angry slant
BU = ['..kkkkk..', 'kk.....k.', '.........']          # raised
BW = ['kkk......', '...kkk...', '......kkk']          # worried (inner up)
BH = ['.........', '.........', 'kkkkkkk..']          # low, relaxed
# ---- eyes (9 wide)
EO = ['..kkkkkk.', 'kkkkkkkkk', '.wOOooOw.', '.wOOpoOw.', '.wOoopOw.', '..wOOOw..', '..kkkkk..']
EH = ['.........', '.........', 'kkkkkkkkk', 'kkkkkkkkk', '.wOOpoOw.', '..kkkkk..', '.........']
EHS = ['.........', '.........', 'kkkkkkkkk', 'kkkkkkkkk', '.wwwOpOw.', '..kkkkk..', '.........']
EHL = ['.........', '.........', 'kkkkkkkkk', 'kkkkkkkkk', '.wOpOwww.', '..kkkkk..', '.........']
EN = ['.........', 'kkk......', '.kkkkkkkk', '.wOOpoOwk', '..wOOOw..', '..kkkkk..', '.........']
EW = ['..kkkkkk.', 'kkkkkkkkk', '.wwOOOww.', '.wwOpOww.', '.wwOOOww.', '..wwwww..', '..kkkkk..']
ES = ['.........', '.........', '.........', '.........', 'kkkkkkkkk', '.........', '.........']      # shut line
EC = ['.........', '.........', '.........', '..kkkkk..', '.k.....k.', 'k.......k', '.........']      # ^ happy closed
EX = ['.........', 'kk.....kk', '..kk.kk..', '....k....', '..kk.kk..', 'kk.....kk', '.........']      # x dizzy
EP = ['.........', 'kk.......', '..kkk....', '....kkkk.', '..kkk....', 'kk.......', '.........']      # > pain
EUP = ['..kkkkkk.', 'kkkkkkkkk', '.wOpOOww.', '.wOOOOww.', '.wwwwwww.', '..wwwww..', '..kkkkk..']   # looking up
ESP = ['....O....', '...OOO...', '.OOOOOOO.', '...OOO...', '....O....', '.........', '.........']      # sparkle stars
EG = ['..kkkkkk.', 'kkkkkkkkk', '.wOOooOw.', '.ppppppp.', '.wOoopOw.', '..wOOOw..', '..kkkkk..']      # glitch bar
ER = ['.........', 'kkkkkkkkk', 'kOOOOOOOk', 'kO.....Ok', 'kOOOOOOOk', 'kkkkkkkkk', '.........']      # robot visor
ERL = ['.........', 'kkkkkkkkk', 'kOOOOOOOk', 'kOO....Ok', 'kOOOOOOOk', 'kkkkkkkkk', '.........']
ERR = ['.........', 'kkkkkkkkk', 'kOOOOOOOk', 'kO....OOk', 'kOOOOOOOk', 'kkkkkkkkk', '.........']
ET = ['.........', '.........', 'kkkkkkkkk', 'kkkkkkkkk', '.wOOpoOw.', '..kkkkk..', '...o.....']      # tear
EWINK = EO
# ---- mouths (x, rows)
M_FLAT = (12, ['..kkkkk.'])
M_SMALL = (13, ['.kkkk.'])
M_SMIRK = (12, ['.......k', '......k.', 'kkkkkk..'])
M_SLY = (11, ['.........k', 'k.......k.', '.kkkkkkk..'])
M_GRIN = (11, ['kwwwwwwwwk', '.kwkwkwkk.', '..kkkkkk..'])
M_FANG = (11, ['kkkkkkkkkk', 'kw.wwww.wk', 'k.wwwwww.k', '.kkkkkkkk.'])
M_OPEN = (13, ['.kkkk.', 'k....k', 'k....k', '.kkkk.'])
M_BIG = (12, ['.kkkkkk.', 'k......k', 'k......k', 'k......k', '.kkkkkk.'])
M_O = (14, ['.kk.', 'k..k', '.kk.'])
M_FROWN = (12, ['..kkkk..', '.k....k.'])
M_WAVY = (12, ['.k.k.k.k', 'k.k.k.k.'])
M_TEETH = (12, ['kkkkkkkk', 'kwwwwwwk', 'kkkkkkkk'])
M_TONGUE = (12, ['kwwwwwwk', '.kkoo.k.', '...oo...'])
M_EAT = (13, ['.kkkk.', 'kmmmmk', '.kkkk.'])
M_EAT2 = (13, ['kkkkkk', '.kkkk.'])
M_WHISTLE = (14, ['.kk.', 'k..k', '.kk.'])
M_HUM = (13, ['.kkk.', '..k..'])
M_YAWN = (12, ['.kkkkkk.', 'k......k', 'k......k', 'k......k', 'k......k', '.kkkkkk.'])
M_SNORE = (13, ['.kkk.', 'k...k', '.kkk.'])
F = {
    'base':        (BF, EHS, M_FLAT),
    'blink':       (BF, ES, M_FLAT),
    'bored':       (BF, EH, M_FLAT),
    'calm':        (BH, EH, M_SMALL),
    'annoyed':     (BE, EH, M_FROWN),
    'concentrate': (BE, EN, M_FLAT),
    'focused':     (BE, EN, M_SMALL),
    'sense':       (BE, EN, M_SMALL),
    'cool':        (BF, EN, M_SMIRK),
    'smug':        (BE, EN, M_SMIRK),
    'wink':        (BE, EWINK, M_SLY),          # (right eye shut, drawn below)
    'grin':        (BE, EO, M_GRIN),
    'happy':       (BH, EC, M_GRIN),
    'excited':     (BU, EW, M_BIG),
    'sparkle':     (BU, ESP, M_GRIN),
    'fierce':      (BE, EO, M_FANG),
    'rage':        (BE, EW, M_FANG),
    'shout':       (BE, EO, M_OPEN),
    'sing':        (BH, EC, M_OPEN),
    'headbang':    (BE, ES, M_OPEN),
    'hum':         (BH, ES, M_HUM),
    'whistle':     (BF, EH, M_WHISTLE),
    'hurt':        (BW, EP, M_TEETH),
    'damage1':     (BW, EP, M_TEETH),
    'damage2':     (BW, EX, M_OPEN),
    'strained':    (BE, EP, M_TEETH),
    'dizzy':       (BW, EX, M_WAVY),
    'exhausted':   (BW, EH, M_OPEN),
    'exhausted2':  (BW, ES, M_OPEN),
    'sick':        (BW, EH, M_WAVY),
    'panic':       (BW, EW, M_BIG),
    'scared':      (BW, EW, M_WAVY),
    'surprised':   (BU, EW, M_O),
    'sad':         (BW, EH, M_FROWN),
    'melancholy':  (BW, EHL, M_FROWN),
    'crying':      (BW, ET, M_FROWN),
    'rain':        (BW, EH, M_FLAT),
    'rain2':       (BW, EHL, M_FROWN),
    'rain3':       (BW, ES, M_FLAT),
    'freezing':    (BW, ES, M_TEETH),
    'hungry':      (BW, EH, M_O),
    'hungry2':     (BW, EUP, M_TONGUE),
    'eat':         (BH, EC, M_EAT),
    'eat2':        (BH, EC, M_EAT2),
    'tongue':      (BU, EO, M_TONGUE),
    'thinking':    (BU, EUP, M_SMALL),
    'look_up':     (BU, EUP, M_FLAT),
    'glance_l':    (BF, EHL, M_FLAT),
    'glance_r':    (BF, EHS, M_FLAT),
    'sleep':       (BH, ES, M_SMALL),
    'sleepy':      (BH, EH, M_SMALL),
    'snore':       (BH, ES, M_SNORE),
    'yawn':        (BH, ES, M_YAWN),
    'glitch':      (BE, EG, M_GRIN),
    'robot':       (BF, ER, M_FLAT),
    'robot_l':     (BF, ERL, M_FLAT),
    'robot_r':     (BF, ERR, M_FLAT),
}
def make(key):
    b, e, (mx, m) = F[key]
    s = dict(spec0); s.update(brow=b, eye=e, mouth=m, mouth_x=mx, mouth_y=23)
    im = U.face_img(s)
    if key == 'wink':                                # his right eye (viewer's left) shut
        px = im.load(); sk = U.C(U.SKIN)
        for y in range(14, 21):
            for x in range(3, 12): px[x, y] = sk + (255,)
        for x in range(3, 12): px[x, 18] = (25, 18, 18, 255)
    return im
faces = {k: make(k) for k in F}
faces['base'].save(CH + 'ultima_face.png')
for k, im in faces.items():
    if k != 'base': im.save(CH + f'ultima_face_{k}.png')
U.overload_face(faces['rage']).save(CH + 'ultima_face_overload.png')
# talking mouths: mouth shapes on his own skin (same 64x64 layout as the others: a 32x32 face, mouth area only)
shapes = [['..kkkk..'], ['.kkkkkk.', '.kwwwwk.', '..kkkk..'], ['.kkkkkk.', 'k......k', '.kkkkkk.'], ['.kkkkkk.', 'k......k', 'k......k', '.kkkkkk.'], ['..kkkk..', '.k....k.', '..kkkk..']]
sk = U.C(U.SKIN)
for i, sh in enumerate(shapes):
    im = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); px = im.load()
    for y in range(22, 29):
        for x in range(10, 23): px[x, y] = sk + (255,)
    for j, row in enumerate(sh):
        for k_, ch in enumerate(row):
            if ch == 'k': px[12 + k_, 23 + j] = (25, 18, 18, 255)
            elif ch == 'w': px[12 + k_, 23 + j] = (244, 240, 238, 255)
    im.save(CH + f'ultima_mouth_{i + 1}.png')
# picture sheet: every face on his head
fit = U.Fit(); build(fit); atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); U.pack(fit, atlas, region=(0, 0, 256, 256))
mj = U.npcs.export(fit, '/tmp/ult_tmp.json'); skin = U.paint_skin(spec0)
FN = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 12)
keys = list(F) + ['overload']
tiles = []
for k in keys:
    face = U.overload_face(faces['rage']) if k == 'overload' else faces[k]
    im = U.preview(mj, skin, atlas, face, yaws=(0,), scale=9)
    W = im.width; im = im.crop((int(W * .22), int(im.height * .08), int(W * .78), int(im.height * .40)))
    d = ImageDraw.Draw(im); d.rectangle((0, 0, im.width, 15), fill=(20, 20, 28)); d.text((4, 1), k, font=FN, fill=(255, 255, 255))
    tiles.append(im)
tw, th = tiles[0].size; cols = 10
sheet = Image.new('RGB', (tw * cols, th * ((len(tiles) + cols - 1) // cols)), (30, 30, 36))
for i, t in enumerate(tiles): sheet.paste(t, ((i % cols) * tw, (i // cols) * th))
sheet.save('/mnt/user-data/outputs/ultima_all_faces.png'); print(len(tiles), sheet.size)
