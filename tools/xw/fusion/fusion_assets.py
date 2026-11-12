"""Puts the picked fusion models into the mod: model, skin, detail atlas, a face for every expression, talking mouths.
Rhaliko (Rhalix + Mikiko) = draft 1 "Storm Coat"; Mikuri (Mikiko + Yuri) = draft 1 "Lab Fox"."""
import sys, os, glob, shutil, json
sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/fusion'); sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/sp')
import fusion_chars as F
import sp_chars as S, sp_assets as A
RES = S.RES; CH = RES + 'textures/entity/character/'
PICKS = {'fusion_rhaliko': 'fusion_rm_a', 'fusion_mikuri': 'fusion_mikuri_a'}

def build(mod_id, key):
    mj, fit, atlas, skin = S.export(key, RES + f'models/detail/{mod_id}.json')
    skin.save(CH + f'{mod_id}.png'); atlas.save(CH + f'{mod_id}_details.png')
    V = A.faces(key)
    V['base'].save(CH + f'{mod_id}_face.png')
    for fpath in glob.glob(CH + 'rhalix_face_*.png'):
        expr = os.path.basename(fpath)[len('rhalix_face_'):-4]
        if expr in ('damage1', 'damage2'): continue
        V[A.MAP.get(expr, 'base')].save(CH + f'{mod_id}_face_{expr}.png')
    for n in ('wince', 'hurt_heavy'):
        if not os.path.exists(CH + f'{mod_id}_face_{n}.png'): V['hurt'].save(CH + f'{mod_id}_face_{n}.png')
    for i in range(1, 6): shutil.copy(CH + f'rhalix_mouth_{i}.png', CH + f'{mod_id}_mouth_{i}.png')
    return len(fit.cubes)

if __name__ == '__main__':
    for mod_id, key in PICKS.items(): print(mod_id, build(mod_id, key))
