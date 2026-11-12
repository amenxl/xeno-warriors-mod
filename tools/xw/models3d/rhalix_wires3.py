"""Rhalix's Wires, held model v3: no handle - the wires come out from under his sleeve. A few turns of wire wrapped
round his wrist, and loose grayish-white strands hanging off it, swaying apart at the ends. Built along +y from the
wrist (y = 0); the display transforms turn +y down along his arm so they hang from the sleeve past his hand."""
import json, math
OUT = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/models/item/3d/rhalix_wires.json'
OLD = json.load(open(OUT))
E = []

def box(name, frm, to, tex, rot=None):
    faces = {}
    for i, f in enumerate(('north', 'south', 'east', 'west', 'up', 'down')):
        u0 = (len(E) * 3 + i * 2) % 12; v0 = (len(E) * 5 + i) % 12
        faces[f] = {'uv': [u0, v0, u0 + 3, v0 + 3], 'texture': '#' + tex}
    e = {'name': name, 'from': [round(x, 3) for x in frm], 'to': [round(x, 3) for x in to], 'faces': faces}
    if rot: e['rotation'] = {'angle': rot[0], 'axis': rot[1], 'origin': [round(x, 3) for x in rot[2]]}
    E.append(e)

def seg(c, length, ang, th, tex, z, axis='z'):
    """A piece of wire centred on c, `length` long at `ang` degrees from horizontal, turned about `axis`."""
    a = ang % 180
    if a in (0, 22.5, 45, 135, 157.5):
        r = a if a <= 45 else a - 180
        if axis == 'z': frm, to = [c[0] - length / 2, c[1] - th / 2, z - th / 2], [c[0] + length / 2, c[1] + th / 2, z + th / 2]
        else: frm, to = [z - th / 2, c[1] - th / 2, c[0] - length / 2], [z + th / 2, c[1] + th / 2, c[0] + length / 2]
    else:
        r = a - 90
        if axis == 'z': frm, to = [c[0] - th / 2, c[1] - length / 2, z - th / 2], [c[0] + th / 2, c[1] + length / 2, z + th / 2]
        else: frm, to = [z - th / 2, c[1] - length / 2, c[0] - th / 2], [z + th / 2, c[1] + length / 2, c[0] + th / 2]
    origin = [c[0], c[1], z] if axis == 'z' else [z, c[1], c[0]]
    box('wire', frm, to, tex, (r if axis == 'z' else -r, axis, origin) if r else None)

# ---- wrapped round the wrist: three turns (square loops, the middle one turned 45 so it reads round)
for k, (y, off) in enumerate([(0.2, 0), (1.0, 45), (1.8, 0)]):
    s, t = 2.6, 0.5
    if off == 0:
        box('wrap', [8 - s / 2 - t, y, 8 - s / 2 - t], [8 + s / 2 + t, y + t, 8 - s / 2], 'steel')
        box('wrap', [8 - s / 2 - t, y, 8 + s / 2], [8 + s / 2 + t, y + t, 8 + s / 2 + t], 'dark')
        box('wrap', [8 - s / 2 - t, y, 8 - s / 2], [8 - s / 2, y + t, 8 + s / 2], 'steel')
        box('wrap', [8 + s / 2, y, 8 - s / 2], [8 + s / 2 + t, y + t, 8 + s / 2], 'dark')
    else:
        box('wrap', [8 - 2.0, y, 8 - 2.0], [8 + 2.0, y + t, 8 + 2.0], 'steel', (45, 'y', [8, y, 8]))

# ---- loose strands hanging off it (x-y plane strands, and two in the z-y plane), each a chain of short pieces
def strand(x0, z0, steps, axis='z', tip=True):
    x, y = x0, 2.3
    for ln, ang in steps:
        dx, dy = ln * math.cos(math.radians(ang)), ln * math.sin(math.radians(ang))
        seg((x + dx / 2, y + dy / 2), ln + 0.25, ang, 0.42, 'steel', z0, axis)
        x, y = x + dx, y + dy
    if tip:
        if axis == 'z': box('tip', [x - 0.3, y - 0.3, z0 - 0.3], [x + 0.3, y + 0.3, z0 + 0.3], 'tip')
        else: box('tip', [z0 - 0.3, y - 0.3, x - 0.3], [z0 + 0.3, y + 0.3, x + 0.3], 'tip')

strand(7.2, 7.3, [(3.0, 90), (3.0, 112.5), (3.2, 90), (3.0, 67.5), (2.6, 90), (2.2, 112.5)])
strand(8.8, 8.7, [(3.0, 90), (3.2, 67.5), (3.0, 90), (3.0, 112.5), (2.4, 90)])
strand(8.0, 8.0, [(3.4, 90), (3.4, 90), (3.0, 67.5), (2.8, 45)])
strand(7.3, 8.6, [(3.0, 90), (2.8, 112.5), (3.0, 135), (2.0, 112.5)], axis='x')
strand(8.7, 7.4, [(2.8, 90), (3.0, 67.5), (2.6, 90), (2.4, 67.5), (2.0, 90)], axis='x')

textures = {'steel': 'xenowarriors:item/forms/rhalix_wire_steel', 'dark': 'xenowarriors:item/forms/rhalix_wire_dark',
            'tip': 'xenowarriors:item/forms/rhalix_wire_tip', 'particle': 'xenowarriors:item/forms/rhalix_wire_steel'}
# +y runs from the wrist down along his arm: they hang from the sleeve past his hand
display = {
    'thirdperson_righthand': {'rotation': [-90, 0, 0], 'translation': [0, -1.5, -3.0], 'scale': [0.9, 0.9, 0.9]},
    'thirdperson_lefthand': {'rotation': [-90, 0, 0], 'translation': [0, -1.5, -3.0], 'scale': [0.9, 0.9, 0.9]},
    'firstperson_righthand': {'rotation': [-60, 0, 0], 'translation': [1.13, -3.0, 1.13], 'scale': [0.6, 0.6, 0.6]},
    'firstperson_lefthand': {'rotation': [-60, 0, 0], 'translation': [1.13, -3.0, 1.13], 'scale': [0.6, 0.6, 0.6]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
    'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [0.85, 0.85, 0.85]},
    'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7], 'scale': [1, 1, 1]},
    'gui': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [0.62, 0.62, 0.62]}}
model = {'credit': "Xeno Warriors Recarnet - Rhalix's Wires v3 (rhalix_wires3.py)", 'gui_light': 'front',
         'render_type': OLD.get('render_type', 'minecraft:cutout'), 'texture_size': [16, 16], 'textures': textures,
         'display': display, 'elements': E}
json.dump(model, open(OUT, 'w'), indent=1)
print(len(E), 'elements', 'ymax', max(e['to'][1] for e in E))
