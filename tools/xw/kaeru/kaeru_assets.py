"""Puts Kaeru into the mod as a playable character: skin, 3D details, model, faces for every expression, and the
shared pieces (armor, mouths, damage/torn looks) the character system expects."""
import sys, os, json, shutil, glob
sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/kaeru'); sys.path.insert(0, '/home/claude/xw/ultima')
import kaeru as K, ultima as U, npcs
from npcs import face_img
from PIL import Image
RES = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
CH = RES + 'textures/entity/character/'
fit, atlas, skin = K.make()
skin.save(CH + 'kaeru.png'); atlas.save(CH + 'kaeru_details.png')
npcs.export(fit, RES + 'models/detail/kaeru.json')

def face(tongue=False, **kw):
    s = dict(K.SPEC); s.update(kw); im = face_img(s)
    if tongue:
        px = im.load()
        for x in (15, 16): px[x, 26] = (220, 96, 104, 255)
    return im
B_FLAT, B_MAD, B_UP = ['.........', '.kkkkkk..', 'k........'], ['......kkk', '...kkk...', 'kkk......'], ['..kkkkk..', 'kk.....k.', '.........']
E_OPEN = K.SPEC['eye']
E_HAPPY = ['.........', '.........', '..kkkkk..', '.k.....k.', 'k.......k', '.........', '.........']
E_SHUT = ['.........', '.........', '.........', '.........', 'kkkkkkkkk', '.........', '.........']
E_WIDE = ['..kkkkkk.', 'kkkkkkkkk', '.wwOOOww.', '.wwOpOww.', '.wwOOOww.', '..wwwww..', '..kkkkk..']
E_NARROW = ['.........', 'kkk......', '.kkkkkkkk', '.wOOpoOwk', '..wOOOw..', '..kkkkk..', '.........']
E_PAIN = ['.........', 'kk.......', '..kkk....', '....kkkk.', '..kkk....', 'kk.......', '.........']
SMILE = ['k......k', '.kkkkkk.']; GRIN = ['kwwwwwwk', '.kwwwwk.', '..kkkk..']; OPEN = ['.kkkk.', 'k....k', 'k....k', '.kkkk.']
FROWN = ['..kkkk..', '.k....k.']; FLAT = ['..kkkk..']
V = {
    'base': face(True, mouth=SMILE, mouth_x=12),
    'blink': face(eye=E_SHUT, mouth=SMILE, mouth_x=12),
    'happy': face(eye=E_HAPPY, mouth=GRIN, mouth_x=12),
    'grin': face(brow=B_MAD, eye=E_NARROW, mouth=GRIN, mouth_x=12),
    'fierce': face(brow=B_MAD, eye=E_OPEN, mouth=['kkkkkkkk', 'kwkwwkwk', '.kkkkkk.'], mouth_x=12),
    'shout': face(brow=B_MAD, eye=E_OPEN, mouth=OPEN, mouth_x=13, mouth_y=23),
    'surprised': face(brow=B_UP, eye=E_WIDE, mouth=['.kk.', 'k..k', '.kk.'], mouth_x=14),
    'hurt': face(brow=B_UP, eye=E_PAIN, mouth=['kkkkkk', 'kwwwwk', 'kkkkkk'], mouth_x=13),
    'sad': face(brow=B_UP, eye=E_OPEN, mouth=FROWN, mouth_x=12),
    'tired': face(brow=B_UP, eye=E_SHUT, mouth=['.kkkk.', 'k....k', '.kkkk.'], mouth_x=13),
    'annoyed': face(brow=B_MAD, eye=E_NARROW, mouth=FLAT, mouth_x=12),
    'calm': face(mouth=SMILE, mouth_x=12),
}
V['base'].save(CH + 'kaeru_face.png')
MAP = {'blink': 'blink', 'sleep': 'blink', 'sleepy': 'tired', 'snore': 'tired', 'yawn': 'tired', 'grin': 'grin', 'happy': 'happy', 'excited': 'happy',
       'smug': 'grin', 'cool': 'grin', 'wink': 'grin', 'fierce': 'fierce', 'rage': 'fierce', 'focused': 'annoyed', 'concentrate': 'annoyed', 'sense': 'annoyed',
       'hurt': 'hurt', 'damage1': 'hurt', 'damage2': 'hurt', 'dizzy': 'tired', 'exhausted': 'tired', 'exhausted2': 'tired', 'strained': 'hurt',
       'panic': 'surprised', 'scared': 'surprised', 'surprised': 'surprised', 'shout': 'shout', 'sing': 'happy', 'headbang': 'shout', 'tongue': 'base',
       'annoyed': 'annoyed', 'sad': 'sad', 'crying': 'sad', 'melancholy': 'sad', 'sick': 'tired', 'hungry': 'sad', 'hungry2': 'sad',
       'freezing': 'sad', 'glitch': 'surprised', 'sparkle': 'happy', 'thinking': 'calm', 'bored': 'calm', 'calm': 'calm'}
for f in glob.glob(CH + 'rhalix_face_*.png'):
    expr = os.path.basename(f)[len('rhalix_face_'):-4]
    V[MAP.get(expr, 'base')].save(CH + f'kaeru_face_{expr}.png')
for suf in ('damage1', 'damage2', 'torn1', 'torn2', 'torn3'): skin.save(CH + f'kaeru_{suf}.png')
for suf in ('details_damage1', 'details_damage2', 'details_torn1', 'details_torn2', 'details_torn3'): atlas.save(CH + f'kaeru_{suf}.png')
for suf in ('armor', 'armor_torn', 'armor_worn', 'mouth_1', 'mouth_2', 'mouth_3', 'mouth_4', 'mouth_5'):
    shutil.copy(CH + f'rhalix_{suf}.png', CH + f'kaeru_{suf}.png')
for f in glob.glob(RES + 'models/armor/rhalix_*.json'):
    shutil.copy(f, f.replace('rhalix_', 'kaeru_'))
# the silhouette she becomes mid-Change (black with glowing white eyes)
sil = Image.new('RGBA', skin.size); sp = sil.load(); src = skin.load()
for y in range(skin.height):
    for x in range(skin.width):
        if src[x, y][3]: sp[x, y] = (14, 12, 16, 255)
sil.save(CH + 'kaeru_silhouette.png')
sf = Image.new('RGBA', (64, 64)); fp = sf.load()
for y in range(6, 30):
    for x in range(2, 30): fp[x, y] = (14, 12, 16, 255)
for cx in (7, 24):
    for y in range(15, 19):
        for x in range(cx - 3, cx + 4): fp[x, y] = (255, 255, 255, 255)
sf.save(CH + 'kaeru_silhouette_face.png')
print('ok', len(glob.glob(CH + 'kaeru*')))

# the girls' skins in the slim layout (their body in the mod is slim - see tools/xw/female/slim.py)
sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/female'); import slim; slim.convert_all()
