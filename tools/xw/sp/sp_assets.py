"""Puts the Sky Patrol playables into the mod: skin, 3D details + wings, model, a face for every expression, and the
shared pieces (armor, mouths, damage/torn looks) the character system expects."""
import sys, os, glob, shutil
sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/sp')
import sp_chars as S
RES = S.RES
CH = RES + 'textures/entity/character/'

B_FLAT, B_MAD, B_UP = ['.........', '.kkkkkk..', 'k........'], ['......kkk', '...kkk...', 'kkk......'], ['..kkkkk..', 'kk.....k.', '.........']
E_HAPPY = ['.........', '.........', '..kkkkk..', '.k.....k.', 'k.......k', '.........', '.........']
E_SHUT = ['.........', '.........', '.........', '.........', 'kkkkkkkkk', '.........', '.........']
E_WIDE = ['..kkkkkk.', 'kkkkkkkkk', '.wwOOOww.', '.wwOpOww.', '.wwOOOww.', '..wwwww..', '..kkkkk..']
E_NARROW = ['.........', 'kkk......', '.kkkkkkkk', '.wOOpoOwk', '..wOOOw..', '..kkkkk..', '.........']
E_PAIN = ['.........', 'kk.......', '..kkk....', '....kkkk.', '..kkk....', 'kk.......', '.........']
E_OPEN = ['..kkkkkk.', 'kkkkkkkkk', '.wOOooOw.', '.wWOpoOw.', '.wOoopOw.', '..wOOOw..', '..kkkkk..']
SMILE = ['k......k', '.kkkkkk.']; GRIN = ['kwwwwwwk', '.kwwwwk.', '..kkkk..']; OPEN = ['.kkkk.', 'k....k', 'k....k', '.kkkk.']
FROWN = ['..kkkk..', '.k....k.']; FLAT = ['..kkkk..']
MAP = {'blink': 'blink', 'sleep': 'blink', 'sleepy': 'tired', 'snore': 'tired', 'yawn': 'tired', 'grin': 'grin', 'happy': 'happy', 'excited': 'happy',
       'smug': 'grin', 'cool': 'grin', 'wink': 'grin', 'fierce': 'fierce', 'rage': 'fierce', 'focused': 'annoyed', 'concentrate': 'annoyed', 'sense': 'annoyed',
       'hurt': 'hurt', 'damage1': 'hurt', 'damage2': 'hurt', 'dizzy': 'tired', 'exhausted': 'tired', 'exhausted2': 'tired', 'strained': 'hurt',
       'panic': 'surprised', 'scared': 'surprised', 'surprised': 'surprised', 'shout': 'shout', 'sing': 'happy', 'headbang': 'shout', 'tongue': 'base',
       'annoyed': 'annoyed', 'sad': 'sad', 'crying': 'sad', 'melancholy': 'sad', 'sick': 'tired', 'hungry': 'sad', 'hungry2': 'sad',
       'freezing': 'sad', 'glitch': 'surprised', 'sparkle': 'happy', 'thinking': 'calm', 'bored': 'calm', 'calm': 'calm'}

def faces(key):
    spec = S.CHARS[key]
    eye = spec['eye']; open_eye = eye if key != 'sam' else E_OPEN
    masked = key == 'lexy'                      # her mask hides the mouth: only the eyes and brows change
    mx = spec.get('mouth_x', 12)
    def f(**kw):
        if masked: kw.pop('mouth', None); kw.pop('mouth_x', None); kw.pop('mouth_y', None)
        return S.face(spec, **kw)
    return {
        'base': S.face(spec),
        'blink': f(eye=E_SHUT),
        'happy': f(eye=E_HAPPY, mouth=GRIN, mouth_x=12),
        'grin': f(brow=B_MAD, eye=E_NARROW if key != 'max' else eye, mouth=GRIN, mouth_x=12),
        'fierce': f(brow=B_MAD, eye=open_eye if key != 'max' else eye, mouth=['kkkkkkkk', 'kwkwwkwk', '.kkkkkk.'], mouth_x=12),
        'shout': f(brow=B_MAD, eye=open_eye if key != 'max' else eye, mouth=OPEN, mouth_x=13, mouth_y=23),
        'surprised': f(brow=B_UP, eye=E_WIDE, mouth=['.kk.', 'k..k', '.kk.'], mouth_x=14),
        'hurt': f(brow=B_UP, eye=E_PAIN, mouth=['kkkkkk', 'kwwwwk', 'kkkkkk'], mouth_x=13),
        'sad': f(brow=B_UP, eye=open_eye, mouth=FROWN, mouth_x=12),
        'tired': f(brow=B_UP, eye=E_SHUT, mouth=['.kkkk.', 'k....k', '.kkkk.'], mouth_x=13),
        'annoyed': f(brow=B_MAD, eye=E_NARROW if key != 'max' else eye, mouth=FLAT, mouth_x=12),
        'calm': S.face(spec),
    }

def build(key):
    mj, fit, atlas, skin = S.export(key, RES + f'models/detail/{key}.json')
    skin.save(CH + f'{key}.png'); atlas.save(CH + f'{key}_details.png')
    V = faces(key)
    V['base'].save(CH + f'{key}_face.png')
    for fpath in glob.glob(CH + 'rhalix_face_*.png'):
        expr = os.path.basename(fpath)[len('rhalix_face_'):-4]
        if expr in ('damage1', 'damage2'): continue                  # (those are battle-damage overlays, from damage_all)
        V[MAP.get(expr, 'base')].save(CH + f'{key}_face_{expr}.png')
    # the girls' skin goes slim first, then battle damage is drawn from it (tools/xw/damage/damage_all.py)
    sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/female'); import slim; slim.convert(CH + f'{key}.png')
    sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/damage'); import damage_all; damage_all.build(key)
    # every expression the game uses, plus the newer ones (tools/xw/faces/expr_more.py)
    sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/faces'); import expr_more
    if key in expr_more.TEMPLATE:
        names = sorted({os.path.basename(p)[len('rhalix_face_'):-4] for p in glob.glob(CH + 'rhalix_face_*.png')} - {'damage1', 'damage2'})
        FV = expr_more.template_faces(key, S.CHARS[key], lambda sp, **kw: S.face(sp, **kw), masked=(key == 'lexy'))
        for n in names + ['laugh', 'blush', 'love', 'shocked', 'determined', 'pout', 'smirk']: FV.get(n, FV['base']).save(CH + f'{key}_face_{n}.png')
    for suf in ('armor', 'armor_torn', 'armor_worn', 'mouth_1', 'mouth_2', 'mouth_3', 'mouth_4', 'mouth_5'):
        shutil.copy(CH + f'rhalix_{suf}.png', CH + f'{key}_{suf}.png')
    for fpath in glob.glob(RES + 'models/armor/rhalix_*.json'):
        shutil.copy(fpath, fpath.replace('rhalix_', f'{key}_'))
    return V

if __name__ == '__main__':
    for key in (sys.argv[1:] or S.CHARS):
        build(key); print(key, len(glob.glob(CH + key + '_*')))

    # the girls' skins in the slim layout (their body in the mod is slim - see tools/xw/female/slim.py)
    sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/female'); import slim; slim.convert_all()
