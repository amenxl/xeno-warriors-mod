"""Pastes a hi-res drawn face onto any character's real model (front view) - generic version of drawn_preview."""
import sys, json
sys.path.insert(0, '/home/claude/xw/ultima')
import ultima as U
from PIL import Image, ImageChops
R = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
def shot(cid, face_tex, scale=30, model=None):
    skin = Image.open(R + f'textures/entity/character/{cid}.png').convert('RGBA')
    atlas = Image.open(R + f'textures/entity/character/{cid}_details.png').convert('RGBA')
    mj = json.load(open(R + f'models/detail/{model or cid}.json'))
    marker = Image.new('RGBA', (64, 64), (0, 0, 0, 0)); marker.paste((255, 0, 255, 255), (0, 0, 32, 32))
    m = U.preview(mj, skin, atlas, marker, yaws=(0,), scale=scale).convert('RGB')
    px = m.load(); pts = [(x, y) for y in range(m.height) for x in range(m.width) if px[x, y] == (255, 0, 255)]
    xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
    box = (min(xs), min(ys), max(xs) + 1, max(ys) + 1)
    base = U.preview(mj, skin, atlas, Image.new('RGBA', (64, 64)), yaws=(0,), scale=scale).convert('RGBA')
    hi = face_tex.crop((0, 0, 128, 128)).resize((box[2] - box[0], box[3] - box[1]), Image.LANCZOS)
    mask = Image.new('L', base.size, 0); mp = mask.load()
    for x, y in pts: mp[x, y] = 255
    layer = Image.new('RGBA', base.size); layer.paste(hi, box[:2])
    base.paste(layer, (0, 0), ImageChops.multiply(layer.split()[3], mask))
    cx, cy = (box[0] + box[2]) // 2, (box[1] + box[3]) // 2; s = int((box[2] - box[0]) * 0.95)
    return base.crop((cx - s, cy - int(s * 1.05), cx + s, cy + int(s * 0.9)))
