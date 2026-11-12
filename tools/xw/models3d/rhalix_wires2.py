"""Rhalix's Wires, held model v2 (previewed in Blockbench): a wrapped handle with a guard ring, a big double coil of
wire wound above it (two offset octagons = a round-looking coil), and three loose strands hanging off the coil that
curl outwards, each ending in a glowing tip. Java block model (angles limited to -45..45 in 22.5 steps)."""
import json, math
OUT = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/models/item/3d/rhalix_wires.json'
OLD = json.load(open(OUT))
E = []

def box(name, frm, to, tex, rot=None):
    faces = {}
    for f in ('north', 'south', 'east', 'west', 'up', 'down'):
        w = abs(to[0] - frm[0]) if f in ('north', 'south', 'up', 'down') else abs(to[2] - frm[2])
        h = abs(to[1] - frm[1]) if f not in ('up', 'down') else abs(to[2] - frm[2])
        u0 = (len(E) * 3) % 12; v0 = (len(E) * 5) % 12
        faces[f] = {'uv': [u0, v0, round(min(16, u0 + max(w, 0.5)), 3), round(min(16, v0 + max(h, 0.5)), 3)], 'texture': '#' + tex}
    e = {'name': name, 'from': [round(x, 3) for x in frm], 'to': [round(x, 3) for x in to], 'faces': faces}
    if rot: e['rotation'] = {'angle': rot[0], 'axis': rot[1], 'origin': [round(x, 3) for x in rot[2]]}
    E.append(e)

def seg(name, c, length, ang, thick, tex, z=8.0, depth=None):
    """A straight piece of wire centred on c (x, y), `length` long, at angle `ang` degrees from horizontal (in the x-y plane)."""
    d = depth or thick
    a = ang % 180
    if a in (0, 22.5, 45, 157.5, 135):                     # lying along x, rotated about z
        r = a if a <= 45 else a - 180
        frm = [c[0] - length / 2, c[1] - thick / 2, z - d / 2]; to = [c[0] + length / 2, c[1] + thick / 2, z + d / 2]
    else:                                                   # standing along y (90 +- 22.5 / 45)
        r = a - 90
        frm = [c[0] - thick / 2, c[1] - length / 2, z - d / 2]; to = [c[0] + thick / 2, c[1] + length / 2, z + d / 2]
    box(name, frm, to, tex, (r, 'z', [c[0], c[1], z]) if r else None)

# ---- handle
box('pommel', [6.7, -2.2, 6.7], [9.3, -1.0, 9.3], 'dark')
box('pommel_gem', [7.4, -2.6, 7.4], [8.6, -2.1, 8.6], 'glow')
box('shaft', [7.2, -1.0, 7.2], [8.8, 6.0, 8.8], 'handle')
for i, y in enumerate([-0.6, 0.5, 1.6, 2.7, 3.8, 4.9]):
    box('wrap', [7.05, y, 7.05], [8.95, y + 0.55, 8.95], 'wrap')
box('guard', [5.8, 6.0, 6.6], [10.2, 7.0, 9.4], 'dark')
box('guard_core', [5.6, 6.25, 6.5], [10.4, 6.75, 9.5], 'glow')
box('lead', [7.65, 7.0, 7.65], [8.35, 8.4, 8.35], 'wrap')

# ---- the coil: two octagons (one turned 22.5) side by side in depth = a round coil of several turns
cx, cy, R = 8.0, 12.6, 4.4
for k, (z, off, tex) in enumerate([(7.25, 0.0, 'wrap'), (8.0, 22.5, 'edge'), (8.75, 0.0, 'wrap'), (8.0, 22.5, 'wrap')]):
    rr = R - (0.55 if k == 3 else 0)
    side = 2 * rr * math.tan(math.pi / 8) + 0.35
    for i in range(8):
        a = off + i * 45                                   # direction from the centre to this side's middle
        mx, my = cx + rr * math.cos(math.radians(a)), cy + rr * math.sin(math.radians(a))
        seg('coil', (mx, my), side, (a + 90) % 180, 0.62, tex, z=z, depth=0.62)
box('coil_glow', [cx - 0.5, cy - R - 0.35, 7.0], [cx + 0.5, cy - R + 0.35, 9.0], 'glow')     # where the lead meets it

# ---- three loose strands curling out from the bottom of the coil, glowing tips
def strand(x0, y0, z, steps, tex='wrap'):
    x, y = x0, y0
    for ln, ang in steps:
        dx, dy = ln * math.cos(math.radians(ang)), ln * math.sin(math.radians(ang))
        seg('strand', (x + dx / 2, y + dy / 2), ln + 0.3, ang % 180, 0.5, tex, z=z, depth=0.5)
        x, y = x + dx, y + dy
    box('strand_tip', [x - 0.45, y - 0.45, z - 0.45], [x + 0.45, y + 0.45, z + 0.45], 'glow')
strand(cx + 3.2, cy - 3.0, 8.9, [(2.2, -45), (2.4, -67.5), (2.2, -90), (1.8, -112.5)])
strand(cx - 3.2, cy - 3.0, 7.1, [(2.0, -135), (2.6, -112.5), (2.0, -90), (1.6, -67.5)])
strand(cx + 2.4, cy + 3.2, 8.4, [(2.0, 45), (2.4, 22.5), (2.0, 0), (1.6, -22.5)])

model = {'credit': 'Xeno Warriors Recarnet - Rhalix\'s Wires v2 (rhalix_wires2.py)', 'gui_light': OLD.get('gui_light', 'front'),
         'render_type': OLD.get('render_type', 'minecraft:cutout'), 'texture_size': [16, 16], 'textures': OLD['textures'],
         'display': OLD['display'], 'elements': E}
model['display']['thirdperson_righthand'] = {'rotation': [0, -90, 10], 'translation': [0, 4.5, 0.5], 'scale': [0.95, 0.95, 0.95]}
model['display']['thirdperson_lefthand'] = {'rotation': [0, 90, -10], 'translation': [0, 4.5, 0.5], 'scale': [0.95, 0.95, 0.95]}
json.dump(model, open(OUT, 'w'), indent=1)
print(len(E), 'elements')
