"""Puts Ultima (Option A, bored face) into the mod as a playable character's assets."""
import sys, os, json, shutil, glob
sys.path.insert(0, '/home/claude/xw/ultima')
import ultima as U
from PIL import Image
RES = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
CH = RES + 'textures/entity/character/'
title, build, gloves, sleek = U.OPTIONS['A']
fit = U.Fit(); build(fit)
atlas = Image.new('RGBA', (256, 256), (0, 0, 0, 0)); U.pack(fit, atlas, region=(0, 0, 256, 256))
spec = dict(U.SPEC); spec['paint'] = U.body_paint(gloves, sleek)
skin = U.paint_skin(spec)
skin.save(CH + 'ultima.png'); atlas.save(CH + 'ultima_details.png')
U.npcs.export(fit, RES + 'models/detail/ultima.json')

def face(**kw):
    s = dict(spec); s.update(kw); return U.face_img(s)
B_FLAT, B_EVIL, B_UP = ['.........', 'kkkkkkk..', '.........'], ['......kkk', '...kkk...', 'kkk......'], ['..kkkkk..', 'kk.....k.', '.........']
E_HALF = ['.........', '.........', 'kkkkkkkkk', 'kkkkkkkkk', '.wOOpoOw.', '..kkkkk..', '.........']
E_OPEN = ['..kkkkkk.', 'kkkkkkkkk', '.wOOooOw.', '.wOOpoOw.', '.wOoopOw.', '..wOOOw..', '..kkkkk..']
E_NARROW = ['.........', 'kkk......', '.kkkkkkkk', '.wOOpoOwk', '..wOOOw..', '..kkkkk..', '.........']
E_WIDE = ['..kkkkkk.', 'kkkkkkkkk', '.wwOOOww.', '.wwOpOww.', '.wwOOOww.', '..wwwww..', '..kkkkk..']
E_SHUT = ['.........', '.........', '.........', '.........', 'kkkkkkkkk', '.........', '.........']
E_PAIN = ['.........', 'kk.......', '..kkk....', '....kkkk.', '..kkk....', 'kk.......', '.........']
GRIN = ['kwwwwwwwwk', '.kwkwkwkk.', '..kkkkkk..']; FANG = ['kkkkkkkkkk', 'kw.wwww.wk', 'k.wwwwww.k', '.kkkkkkkk.']
OPEN = ['.kkkk.', 'k....k', 'k....k', '.kkkk.']; FLAT = ['..kkkkk.']; FROWN = ['..kkkk..', '.k....k.']
V = {
    'base': face(),
    'blink': face(eye=E_SHUT),
    'grin': face(brow=B_EVIL, eye=E_OPEN, mouth=GRIN, mouth_x=11),
    'sly': face(brow=B_EVIL, eye=E_NARROW, mouth=['.........k', 'k.......k.', '.kkkkkkk..'], mouth_x=11),
    'smirk': face(brow=B_EVIL, eye=E_NARROW, mouth=['.......k', '......k.', 'kkkkkk..'], mouth_x=12),
    'fang': face(brow=B_EVIL, eye=E_OPEN, mouth=FANG, mouth_x=11),
    'crazed': face(brow=B_UP, eye=E_WIDE, mouth=['k........k', 'kwwwwwwwwk', '.kwwwwwwk.', '..kkkkkk..'], mouth_x=11),
    'hurt': face(brow=B_UP, eye=E_PAIN, mouth=['kkkkkk', 'kwwwwk', 'kkkkkk'], mouth_x=13),
    'shout': face(brow=B_EVIL, eye=E_OPEN, mouth=OPEN, mouth_x=13, mouth_y=22),
    'tired': face(brow=B_UP, eye=E_HALF, mouth=['.kkkk.', 'k....k', '.kkkk.'], mouth_x=13),
    'frown': face(brow=B_FLAT, eye=E_HALF, mouth=FROWN),
    'surprised': face(brow=B_UP, eye=E_WIDE, mouth=['.kk.', 'k..k', '.kk.'], mouth_x=14),
}
V['base'].save(CH + 'ultima_face.png')
V['crazed'].save(CH + 'ultima_face_overload_eyes.png')
U.overload_face(V['crazed']).save(CH + 'ultima_face_overload.png')
MAP = {'blink': 'blink', 'sleep': 'blink', 'sleepy': 'blink', 'snore': 'blink', 'yawn': 'tired', 'grin': 'grin', 'happy': 'grin', 'excited': 'crazed',
       'smug': 'smirk', 'cool': 'sly', 'wink': 'smirk', 'fierce': 'fang', 'rage': 'fang', 'focused': 'sly', 'concentrate': 'sly', 'sense': 'sly',
       'hurt': 'hurt', 'damage1': 'hurt', 'damage2': 'hurt', 'dizzy': 'tired', 'exhausted': 'tired', 'exhausted2': 'tired', 'strained': 'hurt',
       'panic': 'surprised', 'scared': 'surprised', 'surprised': 'surprised', 'shout': 'shout', 'sing': 'shout', 'headbang': 'shout', 'tongue': 'grin',
       'annoyed': 'frown', 'sad': 'frown', 'crying': 'frown', 'melancholy': 'frown', 'sick': 'tired', 'hungry': 'frown', 'hungry2': 'frown',
       'freezing': 'frown', 'glitch': 'crazed', 'sparkle': 'grin', 'thinking': 'base', 'bored': 'base', 'calm': 'base'}
for f in glob.glob(CH + 'rhalix_face_*.png'):
    expr = os.path.basename(f)[len('rhalix_face_'):-4]
    V[MAP.get(expr, 'base')].save(CH + f'ultima_face_{expr}.png')
# the rest copies his base look, or Rhalix's for shared pieces (same body size)
for suf in ('damage1', 'damage2', 'torn1', 'torn2', 'torn3'): skin.save(CH + f'ultima_{suf}.png')
for suf in ('details_damage1', 'details_damage2', 'details_torn1', 'details_torn2', 'details_torn3'): atlas.save(CH + f'ultima_{suf}.png')
for suf in ('armor', 'armor_torn', 'armor_worn', 'mouth_1', 'mouth_2', 'mouth_3', 'mouth_4', 'mouth_5'):
    shutil.copy(CH + f'rhalix_{suf}.png', CH + f'ultima_{suf}.png')
for f in glob.glob(RES + 'models/armor/rhalix_*.json'):
    shutil.copy(f, f.replace('rhalix_', 'ultima_'))
print('ok', len(glob.glob(CH + 'ultima*')))
# Rhalix's talking mouths carry a patch of his skin: recolour it to Ultima's
SK = U.C(U.SKIN)
for i in range(1, 6):
    im = Image.open(CH + f'ultima_mouth_{i}.png').convert('RGBA'); px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if not a: continue
            if (r, g, b) == (90, 54, 33): px[x, y] = SK + (255,)
            elif (r, g, b) == (58, 26, 16): px[x, y] = U.mul(SK, .55) + (255,)
    im.save(CH + f'ultima_mouth_{i}.png')
print('mouths')
