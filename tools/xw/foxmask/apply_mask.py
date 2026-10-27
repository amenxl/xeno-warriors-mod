"""Puts the chosen Fox King mask (option A: the 3D kitsune side mask tied on with a red cord) on every Fox King
model (his normal look and each outfit's Fox King version). The detail atlases grow to 512x512: the old atlas stays in
the top-left, the mask goes in below. Cel and hair-mask textures are grown to match. Run once (it checks)."""
import sys, json, os
sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/foxmask')
from foxmask import *

CH = RES + 'textures/entity/character/'
OPTION = 'side'

def grow(path, paste=None):
    if not os.path.exists(path): return
    im = Image.open(path).convert('RGBA')
    if im.size == (512, 512): return
    big = Image.new('RGBA', (512, 512), (0, 0, 0, 0)); big.paste(im, (0, 0))
    if paste is not None: big.paste(paste.crop((0, 256, 512, 512)), (0, 256))
    big.save(path)

for name in ['mikiko_foxking', 'mikiko_origins1_foxking', 'mikiko_origins2_foxking', 'mikiko_s3_foxking', 'mikiko_s3b_foxking']:
    jp = RES + f'models/detail/{name}.json'
    src = json.load(open(jp))
    if src['texture_size'] == 512: print('already', name); continue
    old = Image.open(CH + f'{name}_details.png').convert('RGBA')
    hairg = src['bones']['head']['children'][0]
    n0 = len(hairg['cubes'])
    hairg['cubes'] = [c for c in hairg['cubes'] if c['from'][0] >= -4.95]
    fit = Fit(); OPTIONS[OPTION](fit)
    atlas = Image.new('RGBA', (512, 512), (0, 0, 0, 0)); atlas.paste(old, (0, 0))
    pack(fit, atlas, region=(0, 256, 512, 512))
    part = npcs.export(fit, '/tmp/foxmask_part.json')
    hairg['cubes'] += part['bones']['head']['cubes']
    hairg['children'] = hairg.get('children', []) + part['bones']['head']['children']
    src['texture_size'] = 512
    json.dump(src, open(jp, 'w'), separators=(',', ':'))
    atlas.save(CH + f'{name}_details.png')
    grow(CH + f'{name}_details_cel.png', atlas)
    grow(CH + f'{name}_details_hairmask.png')
    grow(CH + f'{name}_details_hairmask_cel.png')
    print(name, 'removed', n0 - len([c for c in hairg['cubes']]) + len(part['bones']['head']['cubes']), 'old mask cubes')
