"""Writes <texture>_cel.png for every character skin and detail atlas (used when Cel-shading is on in Settings)."""
import sys, os, re, glob
sys.path.insert(0, '/home/claude/xw/cel')
import cel
from PIL import Image
A = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
CH = A + 'textures/entity/character/'
skins = details = skipped = 0
for f in sorted(glob.glob(CH + '*.png')):
    n = os.path.basename(f)[:-4]
    if n.endswith('_cel') or any(k in n for k in ('face', 'mouth', 'glow', 'armor')): continue
    im = Image.open(f)
    if '_details' in n:
        base = n.split('_details')[0]
        model = A + f'models/detail/{base}.json'
        if not os.path.exists(model): skipped += 1; continue
        cel.cel_details(model, f, CH + n + '_cel.png'); details += 1
    elif im.size == (64, 64):
        cel.cel_skin(f, CH + n + '_cel.png'); skins += 1
    else: skipped += 1
print('skins', skins, 'details', details, 'skipped', skipped)
