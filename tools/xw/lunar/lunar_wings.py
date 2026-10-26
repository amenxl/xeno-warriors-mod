"""Lunar Rhalix: his feather wings become two crescent moons (dark with a pink rim, stars at the tips).
Writes models/detail/rhalix_lunar.json (copy of rhalix.json with new wing bones) and paints the UV spots
into rhalix_lunar_details.png."""
import json, math, copy
from PIL import Image
A = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
CH = A + 'textures/entity/character/'
CU, CV = 220, 228          # crescent segment UV
SU, SV = 220, 242          # star UV (grey)
OU, OV = 232, 242          # star outline UV (dark)
KU, KV = 244, 242          # cord UV
SEG_L, SEG_T, SEG_D = 4.0, 5.0, 1.2

def find(n, name):
    if n.get('name') == name: return n
    for c in n.get('children', []):
        r = find(c, name)
        if r: return r

def crescent(side):
    """Children for one wing bone. side=-1 right wing (extends -x), +1 left wing. A big '(' / ')' crescent:
    bulging outward, tips curving back in, thick in the middle."""
    kids = []
    cx, cy, R = 5.0 * side, -8.0, 11.0
    N = 18
    a0, a1 = math.radians(-120), math.radians(120)
    for i in range(N):
        t = (i + .5) / N; a = a0 + (a1 - a0) * t
        x = cx + side * math.cos(a) * R; y = cy + math.sin(a) * R          # outer edge point
        inx, iny = cx - x, cy - y; L = math.hypot(inx, iny); inx /= L; iny /= L
        thick = 1.0 + 3.6 * math.sin(math.pi * t) ** 1.3
        seg = 2 * R * math.sin((a1 - a0) / N / 2) * 1.2
        ang = math.atan2(-inx, iny)                                          # local +y -> toward the center (inside)
        kids.append({'name': f'moon{side}_{i}', 'pivot': [round(x, 3), round(y, 3), 0.6], 'rot': [0.0, 0.0, round(ang, 4)],
                     'cubes': [{'uv': [CU, CV], 'from': [round(-seg / 2, 3), 0.0, -SEG_D / 2],
                                'size': [round(seg, 3), round(thick, 3), SEG_D]}], 'children': []})
    # grey stars hanging off the moon on short dark cords (like the art): an outlined 5-point star at each cord's end
    for j, t in enumerate((0.1, 0.4, 0.7, 0.86)):
        a = a0 + (a1 - a0) * t
        x = cx + side * math.cos(a) * (R + 0.3); y = cy + math.sin(a) * (R + 0.3)
        cord = (1.6, 2.6, 2.2, 1.4)[j]
        sz = (4.2, 3.6, 3.9, 4.6)[j]
        kids.append({'name': f'cord{side}_{j}', 'pivot': [round(x, 3), round(y, 3), 0.6], 'rot': [0.0, 0.0, 0.0],
                     'cubes': [{'uv': [KU, KV], 'from': [-0.2, 0.0, -0.2], 'size': [0.4, cord, 0.4]},
                               {'uv': [KU, KV], 'from': [-0.6, -0.4, -0.45], 'size': [1.2, 0.8, 0.9]}], 'children': []})
        sx, sy = x, y + cord + sz * 0.45
        for k in range(5):
            rz = round(k * 2 * math.pi / 5 + (0.25 * side if j % 2 else 0.0), 4)
            kids.append({'name': f'star{side}_{j}_{k}', 'pivot': [round(sx, 3), round(sy, 3), 0.6], 'rot': [0.0, 0.0, rz],
                         'cubes': [{'uv': [SU, SV], 'from': [round(-sz * 0.15, 3), round(-sz * 0.55, 3), -0.35], 'size': [round(sz * 0.3, 3), round(sz * 0.55, 3), 0.7]},
                                   {'uv': [OU, OV], 'from': [round(-sz * 0.21, 3), round(-sz * 0.64, 3), -0.25], 'size': [round(sz * 0.42, 3), round(sz * 0.66, 3), 0.5]}], 'children': []})
    return kids

def main():
    m = json.load(open(A + 'models/detail/rhalix.json'))
    out = copy.deepcopy(m)
    for name, side in (('right_wing', -1), ('left_wing', 1)):
        w = find(out['bones']['body'], name)
        w['cubes'] = []; w['children'] = crescent(side)
    json.dump(out, open(A + 'models/detail/rhalix_lunar.json', 'w'))
    im = Image.open(CH + 'rhalix_lunar_details.png').convert('RGBA'); px = im.load()
    W, H, D = math.ceil(SEG_L) + 1, math.ceil(SEG_T) + 1, 2
    for y in range(CV, CV + D + H + 1):
        for x in range(CU, CU + 2 * D + 2 * W + 2):
            px[x, y] = (18, 12, 26, 255)
    for x in range(CU, CU + 2 * D + 2 * W + 2):                  # outer edge (local -y side = top row) glows pink
        px[x, CV + D] = (220, 95, 185, 255)
        for y in range(CV, CV + D): px[x, y] = (220, 95, 185, 255)
    for y in range(SV, SV + 6):
        for x in range(SU, SU + 10): px[x, y] = (150, 150, 156, 255) if (x + y) % 3 else (128, 128, 134, 255)
        for x in range(OU, OU + 10): px[x, y] = (22, 20, 26, 255)
        for x in range(KU, KU + 8): px[x, y] = (14, 12, 16, 255)
    im.save(CH + 'rhalix_lunar_details.png')
    print('ok')

if __name__ == '__main__':
    main()
