"""Detailed Origins 2 Hekublade: faceted white broadsword, beveled grey guard with a red X, white gem, orange wrapped grip."""
import json, math
from PIL import Image
RES = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
def C(h): h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
PAL = {'white': '#ece6e6', 'whiteD': '#d2cccc', 'steel': '#aaa4a6', 'line': '#3a3638', 'guard': '#6a686e', 'guardD': '#46444a',
       'guardL': '#8c8a90', 'red': '#cc1a1a', 'redD': '#8e1010', 'redL': '#f04a3a', 'gem': '#f6f8fc', 'gemD': '#b8c2d0',
       'orange': '#f07a1e', 'orangeD': '#b0500e', 'dark': '#2a2628', 'darkL': '#4a4648'}
img = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); POS = {}
for i, (k, h) in enumerate(PAL.items()):
    c = C(h)
    for j, f in enumerate((1.12, 1.0, 0.74)):
        p = i * 3 + j; img.putpixel((p % 16, p // 16), tuple(min(255, int(v * f)) for v in c) + (255,))
    POS[k] = i * 3
img.save(RES + 'textures/item/hekublade_origins2.png')
def uv(k, s):
    p = POS[k] + s; x, y = p % 16, p // 16
    return [x + .25, y + .25, x + .75, y + .75]
els = []
def el(frm, to, k, name='part', rotated=True):
    faces = {f: {'uv': uv(k, s), 'texture': '#0'} for f, s in (('north', 1), ('south', 1), ('east', 2), ('west', 0), ('up', 0), ('down', 2))}
    e = {'name': name, 'from': [round(v, 3) for v in frm], 'to': [round(v, 3) for v in to], 'faces': faces}
    if rotated: e['rotation'] = {'angle': -45, 'axis': 'z', 'origin': [8, 8, 8]}
    els.append(e)
def item_space(x, y):
    """A point in the blade's own frame (drawn along +y, then turned -45 about z) -> item space."""
    a = math.radians(-45); dx, dy = x - 8, y - 8
    return 8 + dx * math.cos(a) - dy * math.sin(a), 8 + dx * math.sin(a) + dy * math.cos(a)
def xbar(cx, cy, half_len, half_w, z0, z1, k, name, horizontal):
    # an unrotated bar in item space = at 45 degrees to the blade: two of them make the X
    X, Y = item_space(cx, cy)
    if horizontal: el([X - half_len, Y - half_w, z0], [X + half_len, Y + half_w, z1], k, name, rotated=False)
    else: el([X - half_w, Y - half_len, z0], [X + half_w, Y + half_len, z1], k, name, rotated=False)

# pommel and grip
el([7.0, -1.9, 7.0], [9.0, -0.3, 9.0], 'dark', 'pommel')
el([7.3, -2.3, 7.3], [8.7, -1.9, 8.7], 'darkL', 'pommel cap')
el([6.9, -0.6, 6.9], [9.1, -0.2, 9.1], 'darkL', 'pommel ring')
el([7.3, -0.2, 7.3], [8.7, 4.4, 8.7], 'orange', 'grip')
for i in range(7):
    y = 0.0 + i * 0.62
    xs = (7.2, 8.05) if i % 2 == 0 else (7.95, 8.8)
    el([xs[0], y, 7.2], [xs[1], y + 0.3, 8.8], 'orangeD', 'wrap')
el([7.1, 4.2, 7.1], [8.9, 4.6, 8.9], 'dark', 'collar')
# guard: beveled grey plate, darker border, lighter face
G = 7.15
el([4.7, 4.5, 7.15], [11.3, 9.9, 8.85], 'guardD', 'guard border')
el([5.0, 4.8, 7.05], [11.0, 9.6, 8.95], 'guard', 'guard')
el([5.3, 5.1, 7.0], [10.7, 9.3, 9.0], 'guardL', 'guard face')
# the red X (both faces), with darker edges
for z0, z1 in ((6.9, 7.0), (9.0, 9.1)):
    for horiz in (True, False):
        xbar(8, G + 0.1, 2.75, 0.62, z0 - 0.02 if z0 < 8 else z0, z1 + 0.02 if z1 > 8 else z1, 'redD', 'X edge', horiz)
        xbar(8, G + 0.1, 2.55, 0.42, z0 - 0.06 if z0 < 8 else z0, z1 + 0.06 if z1 > 8 else z1, 'red', 'X', horiz)
    X, Y = item_space(8, G + 0.1)
    el([X - 0.4, Y - 0.4, z0 - 0.1 if z0 < 8 else z0], [X + 0.4, Y + 0.4, z1 + 0.1 if z1 > 8 else z1], 'redL', 'X center', rotated=False)
# white diamond gem at the lower corner, red flame gem where the blade starts
el([4.9, 4.6, 6.8], [6.2, 5.9, 9.2], 'gem', 'gem')
el([5.2, 4.9, 6.75], [5.7, 5.4, 9.25], 'gemD', 'gem facet')
el([7.4, 9.7, 7.4], [8.6, 10.9, 8.6], 'red', 'red gem')
el([7.6, 10.9, 7.5], [8.4, 11.5, 8.5], 'redL', 'red gem tip')
# blade: two faceted halves split by a dark center line, outlined edges
el([6.0, 10.0, 7.6], [8.0, 25.0, 8.4], 'white', 'blade left')
el([8.0, 10.0, 7.6], [10.0, 25.0, 8.4], 'whiteD', 'blade right')
el([7.88, 10.4, 7.54], [8.12, 24.8, 8.46], 'line', 'center line')
el([5.8, 10.0, 7.68], [6.1, 25.0, 8.32], 'steel', 'edge')
el([9.9, 10.0, 7.68], [10.2, 25.0, 8.32], 'steel', 'edge')
el([6.1, 10.0, 7.56], [9.9, 10.6, 8.44], 'steel', 'shoulder')
for y in (14.0, 18.5, 23.0):                                      # faint facet glints
    el([6.4, y, 7.57], [7.2, y + 0.4, 8.43], 'gem', 'glint')
# faceted tip
el([6.4, 25.0, 7.62], [8.0, 26.4, 8.38], 'white', 'tip'); el([8.0, 25.0, 7.62], [9.6, 26.4, 8.38], 'whiteD', 'tip')
el([7.0, 26.4, 7.66], [8.0, 27.4, 8.34], 'white', 'tip'); el([8.0, 26.4, 7.66], [9.0, 27.4, 8.34], 'whiteD', 'tip')
el([7.6, 27.4, 7.72], [8.4, 28.1, 8.28], 'steel', 'point')
el([7.9, 25.0, 7.55], [8.1, 27.6, 8.45], 'line', 'tip line')
json.dump({'textures': {'0': 'xenowarriors:item/hekublade_origins2', 'particle': 'xenowarriors:item/hekublade_origins2'}, 'elements': els},
          open(RES + 'models/item/hekublade_origins2.json', 'w'), indent=1)
print(len(els), 'elements')
