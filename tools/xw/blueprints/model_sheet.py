import sys, json
sys.path.insert(0, '/home/claude/xw/outfits')
from kit import *
from PIL import Image, ImageDraw
MD = RES + 'models/detail/'
out = []
for ch in ('mikiko', 'rhalix'):
    mj = json.load(open(MD + ch + '.json'))
    skin = Image.open(CHAR + ch + '.png').convert('RGBA')
    det = Image.open(CHAR + ch + '_details.png').convert('RGBA')
    face = Image.open(CHAR + ch + '_face.png').convert('RGBA')
    im = preview(mj, skin, det, face, yaws=(0, 40, 90, 180), scale=8)
    out.append(im); im.save(f'/tmp/sheet_{ch}.png')
print([o.size for o in out])
