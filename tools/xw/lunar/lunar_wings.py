"""Lunar Rhalix: his feather wings become two crescent moons (dark with a pink rim, stars at the tips).
Writes models/detail/rhalix_lunar.json (copy of rhalix.json with new wing bones) and paints the UV spots
into rhalix_lunar_details.png."""
import json, math, copy
from PIL import Image
A = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
CH = A + 'textures/entity/character/'
CU, CV = 220, 228          # crescent segment UV
SU, SV = 220, 242          # star UV
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
    for (sx, sy, s) in [(cx + side * 3.0, cy - R - 1.0, 2.2), (cx + side * 3.5, cy + R + 1.0, 1.6), (cx + side * (R + 4.0), cy - 3, 1.4)]:
        for k, rz in enumerate((0.0, math.pi / 2, math.pi / 4)):           # little stars: crossed bars
            kids.append({'name': f'star{side}_{k}_{round(sy)}', 'pivot': [round(sx, 3), round(sy, 3), 0.6], 'rot': [0.0, 0.0, rz],
                         'cubes': [{'uv': [SU, SV], 'from': [-s / 2, -0.35 if k < 2 else -0.25, -0.35], 'size': [s, 0.7 if k < 2 else 0.5, 0.7]}], 'children': []})
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
    for y in range(SV, SV + 4):
        for x in range(SU, SU + 6): px[x, y] = (236, 228, 240, 255)
    im.save(CH + 'rhalix_lunar_details.png')
    print('ok')

if __name__ == '__main__':
    main()
