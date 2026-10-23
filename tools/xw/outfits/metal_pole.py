"""3D Metal Pole: a long steel staff - capped and banded ends, segmented shaft with rivets, a taped grip with red rings."""
import json
from PIL import Image
RES = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
def C(h): h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
PAL = {'steel': '#a8aeb8', 'steelL': '#d4d8de', 'steelD': '#767c86', 'cap': '#5c6068', 'capL': '#8a9098', 'tape': '#26252a',
       'tapeL': '#3a393f', 'red': '#b8262a', 'redL': '#e0484a', 'rivet': '#e8eaee', 'grip': '#4a3a30'}
img = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); POS = {}
for i, (k, h) in enumerate(PAL.items()):
    c = C(h)
    for j, f in enumerate((1.12, 1.0, 0.72)):
        p = i * 3 + j; img.putpixel((p % 16, p // 16), tuple(min(255, int(v * f)) for v in c) + (255,))
    POS[k] = i * 3
img.save(RES + 'textures/item/metal_pole_3d.png')
def uv(k, s):
    p = POS[k] + s; x, y = p % 16, p // 16
    return [x + .25, y + .25, x + .75, y + .75]
els = []
def el(frm, to, k, name='part'):
    faces = {f: {'uv': uv(k, s), 'texture': '#0'} for f, s in (('north', 1), ('south', 1), ('east', 2), ('west', 0), ('up', 0), ('down', 2))}
    els.append({'name': name, 'from': [round(v, 3) for v in frm], 'to': [round(v, 3) for v in to],
                'rotation': {'angle': -45, 'axis': 'z', 'origin': [8, 8, 8]}, 'faces': faces})
def ring(y0, y1, r, k, name):
    el([8 - r, y0, 8 - r], [8 + r, y1, 8 + r], k, name)

R = 0.65                                             # shaft half-thickness
Y0, Y1 = -13.0, 29.5                                 # end to end (the pole is ~2.6 blocks long held)
# shaft in segments with a thin highlight strip down one side
segs = [(Y0 + 1.4, -1.0), (4.6, 15.5), (15.5, Y1 - 1.4)]
for a, b in segs:
    ring(a, b, R, 'steel', 'shaft')
    el([8 - R - 0.02, a, 8 - 0.18], [8 - R + 0.12, b, 8 + 0.18], 'steelL', 'shine')
    el([8 + R - 0.12, a, 8 - 0.18], [8 + R + 0.02, b, 8 + 0.18], 'steelD', 'shade')
# joint collars between the segments, with rivets
for y in (15.5,):
    ring(y - 0.5, y + 0.5, R + 0.22, 'capL', 'collar')
    for dx, dz in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        cx, cz = 8 + dx * (R + 0.24), 8 + dz * (R + 0.24)
        el([cx - 0.15, y - 0.15, cz - 0.15], [cx + 0.15, y + 0.15, cz + 0.15], 'rivet', 'rivet')
# end caps: heavy ferrule, a band, a flat butt
for (a, b), sign in (((Y0, Y0 + 1.4), -1), ((Y1 - 1.4, Y1), 1)):
    ring(a, b, R + 0.3, 'cap', 'cap')
    ring((a if sign < 0 else b) - (0 if sign < 0 else 0.35), (a + 0.35 if sign < 0 else b), R + 0.18, 'capL', 'butt')
    band = b + 0.2 if sign < 0 else a - 0.55
    ring(band, band + 0.35, R + 0.2, 'capL', 'band')
# grip: taped section with diagonal wraps and red rings at both ends (where his hands go)
ring(-1.0, 4.6, R + 0.12, 'tape', 'grip tape')
for i in range(8):
    y = -0.85 + i * 0.68
    off = 0.15 if i % 2 else -0.15
    el([8 - R - 0.16, y, 8 - R - 0.16], [8 + R + 0.16, y + 0.28, 8 + R + 0.16], 'tapeL', 'wrap')
ring(-1.45, -0.95, R + 0.26, 'red', 'grip ring'); ring(-1.32, -1.08, R + 0.3, 'redL', 'grip ring shine')
ring(4.55, 5.05, R + 0.26, 'red', 'grip ring'); ring(4.68, 4.92, R + 0.3, 'redL', 'grip ring shine')
# a second short grip band higher up for two-handed swings
ring(19.0, 21.4, R + 0.1, 'tape', 'upper tape')
ring(18.7, 19.0, R + 0.2, 'red', 'ring'); ring(21.4, 21.7, R + 0.2, 'red', 'ring')

disp = {"thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.2, 0.5], "scale": [1.0, 1.0, 1.0]},
        "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.2, 0.5], "scale": [1.0, 1.0, 1.0]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.0, 1.13], "scale": [0.7, 0.7, 0.7]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.0, 1.13], "scale": [0.7, 0.7, 0.7]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
        "gui": {"rotation": [0, 0, 0], "translation": [-1.8, -1.8, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 180, 0], "translation": [-1.8, -1.8, 0], "scale": [0.5, 0.5, 0.5]}}
json.dump({'textures': {'0': 'xenowarriors:item/metal_pole_3d', 'particle': 'xenowarriors:item/metal_pole_3d'}, 'gui_light': 'front',
           'display': disp, 'elements': els}, open(RES + 'models/item/metal_pole.json', 'w'), indent=1)
print(len(els), 'elements')
