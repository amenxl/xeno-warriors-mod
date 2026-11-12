"""Yuri's Super Saiyan hair: DMZ-style strands (dmz_hair) over her bob, in its own model (models/detail/yuri_ssjhair.json,
textures/entity/character/yuri_ssjhair.png). Only drawn while she's in a DragonMineZ Super Saiyan form: the game styles the
strands for each form (spikes up for SSJ, sharper for SSJ2, long down her back for SSJ3, a mane for SSJ4 - DetailModel
.animateHair) and tints them the form's colour, so the atlas is light greys. Also writes the masks that tint her own bob
(yuri[_c1pjs]_details_hairmask.png) so no black shows under the gold."""
import sys, json, math, random
sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/dmzhair')
import dmz_hair as H
from dmz_hair import Strand
from PIL import Image

RES = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
CH = RES + 'textures/entity/character/'
L1, L2, L3, L4 = '#f4f4f4', '#e2e2e2', '#cdcdcd', '#b8b8b8'          # (tinted gold / blue / dark red in game)

def strands():
    rnd = random.Random(11)
    out = []
    # fringe: her bangs, a little longer at the sides
    for i, (dx, ln) in enumerate(((-0.45, 2), (-0.15, 2), (0.15, 2), (0.45, 2))):
        out.append(Strand('front', i, (dx, -0.75, -0.65), ln, w=2.7, h=1.7, d=1.2, curve=(-20, 0, dx * 16), colors=(L1, L2)))
    # top: the crown, leaning back and out
    for i in range(16):
        row, col = divmod(i, 4)
        gx, gz = H.GRID[col], H.GRID[row]
        d = (gx * 0.3 + rnd.uniform(-0.06, 0.06), 0.4 + rnd.uniform(-0.05, 0.08), 0.3 + gz * 0.12 + rnd.uniform(-0.06, 0.06))
        if row == 0: d = (gx * 0.25, 0.5, -0.55)
        out.append(Strand('top', i, d, 3, w=2.4, h=1.7, d=1.7, curve=(30 if row else -28, 0, -gx * 10), colors=(L1, L1, L2)))
    # back: down to the nape (her bob)
    for i in range(12):
        row, col = divmod(i, 4)
        gx = H.GRID[col]
        out.append(Strand('back', i, (gx * 0.06, -0.95, 0.28), 3 - row, w=2.4, h=1.7, d=1.3, curve=(8, 0, gx * 2), colors=(L2, L3)))
    # sides: hanging to her jaw
    for face, sx in (('left', -1), ('right', 1)):
        for i in range(12):
            row, col = divmod(i, 4)
            out.append(Strand(face, i, (sx * 0.25, -0.96, 0.1), 3 - row, w=1.3, h=1.7, d=2.3, curve=(6, 0, -sx * 7), colors=(L2, L3)))
    return out

def build():
    atlas = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    sw = {}
    for (u, v), col in zip(((0, 0), (64, 0), (0, 64), (64, 64)), (L1, L2, L3, L4)):
        c = tuple(int(col[i:i + 2], 16) for i in (1, 3, 5))
        for y in range(v, v + 64):
            for x in range(u, u + 64):
                k = 1.0 + ((x * 7 + y * 13) % 5 - 2) * 0.03                 # a little grain so it isn't flat
                atlas.putpixel((x, y), tuple(min(255, int(ch * k)) for ch in c) + (255,))
        sw[col] = (u, v)
    st = strands()
    nodes = H.bake(st, sw, 'ys')
    def box(lo, hi, col, n):
        u, v = sw[col]
        return {'name': n, 'pivot': [0, 0, 0], 'rot': [0, 0, 0], 'children': [],
                'cubes': [{'uv': [u, v], 'from': [round(-hi[0], 3), round(-(hi[1] - 24), 3), round(lo[2], 3)], 'size': [round(hi[i] - lo[i], 3) for i in range(3)]}]}
    cap = [box([-4.75, 32.95, -4.75], [4.75, 33.75, 4.75], L1, 'cap_top')]     # (just over her own bob's top)
    yuri = json.load(open(RES + 'models/detail/yuri.json'))
    bones = {'head': {'pivot': [0, 0, 0], 'cubes': [], 'children': [
        {'name': 'hair', 'pivot': [0, 0, 0], 'rot': [0, 0, 0], 'cubes': [], 'children': cap + nodes}]}}
    for b in ('body', 'right_arm', 'left_arm', 'right_leg', 'left_leg'):      # (the loader wants every bone, even empty)
        bones[b] = {'pivot': yuri['bones'][b]['pivot'], 'cubes': [], 'children': []}
    model = {'texture_size': 128, 'bones': bones}
    json.dump(model, open(RES + 'models/detail/yuri_ssjhair.json', 'w'), separators=(',', ':'))
    atlas.save(CH + 'yuri_ssjhair.png')
    return sum(len(s.cubes()) for s in st)

def mask(model_name, atlas_name):
    """Her bob (the head's own hair cubes - not the headphones) as bright greys, for tinting in a form."""
    mj = json.load(open(RES + 'models/detail/' + model_name + '.json'))
    atlas = Image.open(CH + atlas_name + '.png').convert('RGBA'); px = atlas.load()
    out = Image.new('RGBA', atlas.size, (0, 0, 0, 0)); po = out.load(); n = 0
    for c in mj['bones']['head']['cubes'][:5]:                              # cap, back, two sides, fringe
        w, h, d = [max(1, math.ceil(v - 1e-6)) for v in c['size']]
        u, v = c['uv']
        for x in range(u, u + 2 * d + 2 * w):
            for y in range(v, v + d + h):
                if x >= atlas.width or y >= atlas.height or px[x, y][3] == 0: continue
                r, g, b, a = px[x, y]
                lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255
                gv = int(255 * (0.7 + 0.3 * min(1, lum * 4)))
                po[x, y] = (gv, gv, gv, 255); n += 1
    out.save(CH + atlas_name + '_hairmask.png')
    return n

if __name__ == '__main__':
    print(build(), 'cubes')
    print(mask('yuri', 'yuri_details'), mask('yuri_c1pjs', 'yuri_c1pjs_details'), 'mask pixels')
    if '--preview' in sys.argv:
        sys.path.insert(0, '/home/claude/xw/ultima')
        import ultima as U
        base = json.load(open(RES + 'models/detail/yuri.json'))
        hair = json.load(open(RES + 'models/detail/yuri_ssjhair.json'))
        # (preview: her model with the strands over it, all on her own atlas - the strands get a gold swatch)
        atl = Image.open(CH + 'yuri_details.png').convert('RGBA')
        big = Image.new('RGBA', (384, 384), (0, 0, 0, 0)); big.paste(atl, (0, 0))
        for y in range(256, 384):
            for x in range(0, 128):
                k = 1.0 + ((x * 7 + y * 13) % 5 - 2) * 0.03
                big.putpixel((x, y), (min(255, int(250 * k)), min(255, int(210 * k)), min(255, int(70 * k)), 255))
        def shift(n):
            for c in n.get('cubes', []): c['uv'] = [c['uv'][0] % 64, 256 + c['uv'][1] % 64]
            for ch in n.get('children', []): shift(ch)
        shift(hair['bones']['head']['children'][0])
        base['texture_size'] = 384
        base['bones']['head']['children'] = base['bones']['head'].get('children', []) + [hair['bones']['head']['children'][0]]
        im = U.preview(base, Image.open(CH + 'yuri.png').convert('RGBA'), big, Image.open(CH + 'yuri_face.png').convert('RGBA'),
                       yaws=(0, 35, 90, 200), scale=10, wide=24, tall=22, top=7)
        im.save(sys.argv[-1]); print(im.size)
