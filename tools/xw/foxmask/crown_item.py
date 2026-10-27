"""The Fox King Crown item: the same 3D kitsune mask (option A) as a held/inventory item model."""
import sys, json, math
sys.path.insert(0, '/home/claude/xeno-warriors-mod/tools/xw/foxmask')
from foxmask import *

fit = Fit()
mask(Place(fit, [8.0, 7.2, 8.0], s=2.3, bone='head'))
atlas = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
pack(fit, atlas, region=(0, 0, 128, 128))
atlas.save(RES + 'textures/item/fox_king_crown_3d.png')
K = 16 / 128
els = []
for c in fit.cubes:
    lo, hi = c['lo'], c['hi']
    w, h, d = [hi[k] - lo[k] for k in range(3)]
    u, v = c['uv']
    rects = {'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h), 'west': (u + d + w, v + d, d, h),
             'south': (u + 2 * d + w, v + d, w, h), 'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d)}
    # model space -> item space: the mask faces north (-z) like a block model's front; x flipped so its right is on the right
    frm = [16 - hi[0], lo[1], lo[2]]; to = [16 - lo[0], hi[1], hi[2]]
    faces = {}
    for f, (x, y, ww, hh) in rects.items():
        faces[f] = {'uv': [round(x * K, 4), round(y * K, 4), round((x + max(ww, 0.01)) * K, 4), round((y + max(hh, 0.01)) * K, 4)], 'texture': '#t'}
    els.append({'from': [round(a, 4) for a in frm], 'to': [round(a, 4) for a in to], 'faces': faces})
disp = {"gui": {"rotation": [0, 180, 0], "scale": [0.85, 0.85, 0.85]},
        "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 180, 0], "scale": [0.9, 0.9, 0.9]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 0, -1], "scale": [1.0, 1.0, 1.0]},
        "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 2, 1], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_lefthand": {"rotation": [0, 180, 0], "translation": [0, 2, 1], "scale": [0.5, 0.5, 0.5]},
        "firstperson_righthand": {"rotation": [0, 160, 0], "translation": [1, 3, 0], "scale": [0.45, 0.45, 0.45]},
        "firstperson_lefthand": {"rotation": [0, 200, 0], "translation": [1, 3, 0], "scale": [0.45, 0.45, 0.45]}}
json.dump({'textures': {'t': 'xenowarriors:item/fox_king_crown_3d', 'particle': 'xenowarriors:item/fox_king_crown'},
           'elements': els, 'display': disp}, open(RES + 'models/item/fox_king_crown.json', 'w'), indent=1)
v = [a for e in els for a in e['from'] + e['to']]; print(len(els), min(v), max(v))
