"""<texture>_cel.png for NPC skins and detail atlases (townsfolk, Skylanders, Gov, SR, Order...)."""
import sys, os, glob
sys.path.insert(0, '/home/claude/xw/cel')
import cel
from PIL import Image
A = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
N = A + 'textures/entity/npc/'
s = d = 0
for f in sorted(glob.glob(N + '*.png')):
    n = os.path.basename(f)[:-4]
    if n.endswith('_cel') or 'face' in n or n.startswith('wing_') or n == 'armband': continue
    if n.endswith('_details'):
        base = n[:-8]
        if base.endswith('_dark'): continue
        model = A + f'models/detail/npc/{base}.json'
        if os.path.exists(model): cel.cel_details(model, f, N + n + '_cel.png'); d += 1
    elif Image.open(f).size == (64, 64) and not n.endswith('_dark'):
        cel.cel_skin(f, N + n + '_cel.png'); s += 1
print('npc skins', s, 'details', d)
