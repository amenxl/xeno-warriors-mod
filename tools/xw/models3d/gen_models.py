"""3D models for the mod's flat tools / weapons (Blockbench-style JSON elements, in-house gun-model style: small
patterned material textures, emissive "glow" parts via forge_data).

Each item gets:
  models/item/3d/<name>.json  - the 3D model (built standing upright along +Y, centred on x = z = 8; its display
                                 transforms are the vanilla handheld ones with Z turned -45 deg, so it sits exactly
                                 where the flat diagonal sprite sat - also on Epic Fight's hand joints)
  models/item/<name>.json     - forge:separate_transforms: the 3D model everywhere, the old flat sprite in the GUI.
usage: python3 -I gen_models.py <assets/xenowarriors dir> [preview_dir]"""
import json, os, sys, zlib

ASSETS = sys.argv[1]
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else None
NS = 'xenowarriors'
MODELS = []

# ------------------------------------------------------------------ materials

def T(p):
    return NS + ':item/' + p

HEKU = dict(head=T('forms/heku_gun_steel'), edge=T('forms/heku_gun_accent'), handle=T('forms/heku_gun_dark'),
            wrap=T('forms/heku_gun_grip'), dark=T('forms/heku_gun_dark'), glow=T('forms/heku_gun_glow'), trim=T('forms/heku_gun_accent'))
WIRE = dict(head=T('forms/wire_gun_steel'), edge=T('forms/wire_gun_accent'), handle=T('forms/wire_gun_dark'),
            wrap=T('forms/wire_gun_grip'), dark=T('forms/wire_gun_dark'), glow=T('forms/wire_gun_glow'), trim=T('forms/wire_gun_grip'))
SKY = dict(head=T('3d/sky_crystal'), edge=T('3d/sky_edge'), handle=T('3d/sky_handle'),
           wrap=T('3d/sky_metal'), dark=T('3d/sky_dark'), glow=T('3d/sky_glow'), trim=T('3d/sky_metal'))
CORR = dict(head=T('3d/corrupted_head'), edge=T('3d/corrupted_edge'), handle=T('3d/corrupted_dark'),
            wrap=T('3d/corrupted_bone'), dark=T('3d/corrupted_dark'), glow=T('3d/corrupted_glow'), trim=T('3d/corrupted_bone'))
DEMON = dict(head=T('3d/demonite_blade'), edge=T('3d/demonite_edge'), handle=T('3d/demonite_grip'),
             wrap=T('3d/demonite_grip'), dark=T('3d/demonite_dark'), glow=T('3d/demonite_glow'), trim=T('3d/demonite_dark'))

GLOW_KEYS = ('glow', 'glow2')

# ------------------------------------------------------------------ element helpers

def box(els, frm, to, mat, rot=None, name=None, faces=None):
    frm = [round(v, 3) for v in frm]; to = [round(v, 3) for v in to]
    dx, dy, dz = (to[i] - frm[i] for i in range(3))
    h = zlib.crc32(json.dumps([frm, to]).encode())
    def uv(w, hh, k):
        w = min(max(w, 0.25), 16); hh = min(max(hh, 0.25), 16)
        u0 = ((h >> (k * 4)) % 9) * (16 - w) / 8 if w < 16 else 0
        v0 = ((h >> (k * 4 + 2)) % 9) * (16 - hh) / 8 if hh < 16 else 0
        return [round(u0, 2), round(v0, 2), round(u0 + w, 2), round(v0 + hh, 2)]
    dims = {'north': (dx, dy), 'south': (dx, dy), 'east': (dz, dy), 'west': (dz, dy), 'up': (dx, dz), 'down': (dx, dz)}
    e = {'from': frm, 'to': to, 'faces': {}}
    for k, (f, (w, hh)) in enumerate(dims.items()):
        if faces and f not in faces: continue
        e['faces'][f] = {'uv': uv(w, hh, k), 'texture': '#' + mat}
    if rot: e['rotation'] = {'angle': rot[0], 'axis': rot[1], 'origin': list(rot[2])}
    if mat in GLOW_KEYS: e['forge_data'] = {'block_light': 15, 'sky_light': 15}
    if name: e['name'] = name
    els.append(e)
    return e


def cbox(els, cx, y0, y1, w, d, mat, cz=8.0, **kw):
    """Box centred on (cx, cz) from y0 to y1, w wide (x), d deep (z)."""
    return box(els, [cx - w / 2, y0, cz - d / 2], [cx + w / 2, y1, cz + d / 2], mat, **kw)


def mirror_x(e):
    """A copy of an element mirrored about x = 8."""
    f, t = e['from'], e['to']
    m = json.loads(json.dumps(e))
    m['from'] = [round(16 - t[0], 3), f[1], f[2]]; m['to'] = [round(16 - f[0], 3), t[1], t[2]]
    if 'rotation' in m:
        r = m['rotation']; r['origin'][0] = round(16 - r['origin'][0], 3)
        if r['axis'] in ('y', 'z'): r['angle'] = -r['angle']
    if 'east' in m['faces'] or 'west' in m['faces']:
        m['faces']['east'], m['faces']['west'] = e['faces'].get('west'), e['faces'].get('east')
        m['faces'] = {k: v for k, v in m['faces'].items() if v}
    return m


def handle(els, y0, y1, style, w=1.4):
    cbox(els, 8, y0, y1, w, w, 'handle', name='shaft')
    # wrap bands over the lower (grip) part
    y = y0 + 0.4
    while y + 0.55 < min(y1, y0 + 6.2):
        cbox(els, 8, y, y + 0.55, w + 0.3, w + 0.3, 'wrap', name='wrap')
        y += 1.1
    cbox(els, 8, y0 - 1.0, y0, w + 0.8, w + 0.8, 'dark', name='pommel')
    if style == 'heku':                                                     # fox ears on the pommel, a Netsu ember in it
        box(els, [7.0, y0 - 1.9, 7.6], [7.7, y0 - 1.0, 8.4], 'trim', name='ear_l')
        box(els, [8.3, y0 - 1.9, 7.6], [9.0, y0 - 1.0, 8.4], 'trim', name='ear_r')
        box(els, [7.6, y0 - 0.8, 7.0], [8.4, y0 - 0.2, 9.0], 'glow', name='ember')
    elif style == 'wire':                                                    # a loose wire end hanging from the pommel
        box(els, [7.75, y0 - 4.0, 7.75], [8.25, y0 - 1.0, 8.25], 'wrap', rot=(-22.5, 'z', (8, y0 - 1.0, 8)), name='wire_tail')
        box(els, [7.6, y0 - 4.6, 7.6], [8.4, y0 - 3.9, 8.4], 'glow', rot=(-22.5, 'z', (8, y0 - 1.0, 8)), name='wire_tip')
    elif style == 'sky':
        cbox(els, 8, y0 - 1.6, y0 - 1.0, 1.0, 1.0, 'glow', name='gem')
    elif style == 'corr':
        box(els, [6.6, y0 - 1.6, 7.7], [7.2, y0 - 0.4, 8.3], 'edge', name='spike_l')
        box(els, [8.8, y0 - 1.6, 7.7], [9.4, y0 - 0.4, 8.3], 'edge', name='spike_r')


def wire_coil(els, y0, y1, w=1.4):
    """Rhalix's wires spiralling up a shaft (alternating thin glowing turns)."""
    y = y0
    k = 0
    while y + 0.35 < y1:
        box(els, [8 - w / 2 - 0.25, y, 8 - w / 2 - 0.25], [8 + w / 2 + 0.25, y + 0.35, 8 + w / 2 + 0.25],
            'glow' if k % 2 == 0 else 'wrap', rot=(22.5 if k % 2 == 0 else -22.5, 'z', (8, y + 0.17, 8)), name='coil')
        y += 1.3; k += 1

# ------------------------------------------------------------------ display transforms

def tool_display():
    """Vanilla item/handheld with Z turned -45 deg (the model stands upright; the sprite is drawn diagonally)."""
    return {
        'thirdperson_righthand': {'rotation': [0, -90, 10], 'translation': [0, 4.0, 0.5], 'scale': [0.85, 0.85, 0.85]},
        'thirdperson_lefthand': {'rotation': [0, 90, -10], 'translation': [0, 4.0, 0.5], 'scale': [0.85, 0.85, 0.85]},
        'firstperson_righthand': {'rotation': [0, -90, -20], 'translation': [1.13, 2.6, 1.13], 'scale': [0.6, 0.6, 0.6]},
        'firstperson_lefthand': {'rotation': [0, 90, 20], 'translation': [1.13, 2.6, 1.13], 'scale': [0.6, 0.6, 0.6]},
        'ground': {'rotation': [0, 0, -45], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
        'fixed': {'rotation': [0, 180, -45], 'translation': [0, 0, 0], 'scale': [0.85, 0.85, 0.85]},
        'head': {'rotation': [0, 180, -45], 'translation': [0, 13, 7], 'scale': [1, 1, 1]},
        'gui': {'rotation': [0, 0, -45], 'translation': [0, 0, 0], 'scale': [0.62, 0.62, 0.62]},
    }


def gun_display():
    """The in-house gun models' transforms (forms/gun_rifle.json): barrel along -Z."""
    return {
        'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 1.2, -2.6], 'scale': [0.7, 0.7, 0.7]},
        'thirdperson_lefthand': {'rotation': [0, 0, 0], 'translation': [0, 1.2, -2.6], 'scale': [0.7, 0.7, 0.7]},
        'firstperson_righthand': {'rotation': [0, 4, 0], 'translation': [-4, 4, 0], 'scale': [0.75, 0.75, 0.75]},
        'firstperson_lefthand': {'rotation': [0, -4, 0], 'translation': [-4, 4, 0], 'scale': [0.75, 0.75, 0.75]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.45, 0.45, 0.45]},
        'fixed': {'rotation': [0, -90, 0], 'translation': [0, 0, 0], 'scale': [0.7, 0.7, 0.7]},
        'head': {'rotation': [0, -90, 0], 'translation': [0, 6, 0], 'scale': [0.8, 0.8, 0.8]},
        'gui': {'rotation': [15, -90, 0], 'translation': [0.5, 1, 0], 'scale': [1.0, 1.0, 1.0]},
    }

# ------------------------------------------------------------------ the shapes (upright, head at the top)

def sword(style):
    els = []
    handle(els, -2.0, 3.6, style, w=1.3)
    if style == 'wire':
        # Rhalix's wire sword: a dark narrow blade wound with wire, hooked guard
        cbox(els, 8, 3.6, 4.9, 6.5, 1.8, 'dark', name='guard')
        box(els, [4.2, 4.9, 7.3], [5.2, 6.4, 8.7], 'dark', name='guard_hook_l')
        box(els, [10.8, 2.3, 7.3], [11.8, 3.6, 8.7], 'dark', name='guard_hook_r')
        cbox(els, 8, 3.9, 4.6, 1.2, 2.0, 'glow', name='guard_core')
        cbox(els, 8, 4.9, 17.6, 2.4, 0.9, 'head', name='blade')
        cbox(els, 6.55, 5.4, 17.2, 0.5, 0.5, 'edge', name='edge_l')
        cbox(els, 9.45, 5.4, 17.2, 0.5, 0.5, 'edge', name='edge_r')
        cbox(els, 8, 5.4, 16.0, 0.5, 1.05, 'glow', name='fuller')
        wire_coil(els, 5.2, 9.5, w=2.4)
        cbox(els, 8, 17.6, 18.6, 1.8, 0.8, 'head', name='tip1')
        cbox(els, 8, 18.6, 19.6, 1.0, 0.6, 'edge', name='tip2')
        return els
    if style == 'demon':
        # demonite: broad serrated red blade, horned guard, ember fuller
        cbox(els, 8, 3.6, 5.0, 7.0, 2.2, 'dark', name='guard')
        box(els, [4.5, 5.0, 7.4], [5.5, 6.8, 8.6], 'dark', name='horn_l'); box(els, [10.5, 5.0, 7.4], [11.5, 6.8, 8.6], 'dark', name='horn_r')
        box(els, [4.7, 6.8, 7.6], [5.3, 7.6, 8.4], 'edge', name='horn_tip_l'); box(els, [10.7, 6.8, 7.6], [11.3, 7.6, 8.4], 'edge', name='horn_tip_r')
        cbox(els, 8, 3.8, 4.8, 1.4, 2.4, 'glow', name='gem')
        cbox(els, 8, 5.0, 17.0, 3.4, 1.0, 'head', name='blade')
        for i, y in enumerate(range(6, 17, 2)):                                   # serrations
            box(els, [5.8, y, 7.65], [6.3, y + 1.0, 8.35], 'edge', name='tooth')
            box(els, [9.7, y + 1, 7.65], [10.2, y + 2.0, 8.35], 'edge', name='tooth')
        cbox(els, 8, 5.8, 15.5, 0.8, 1.1, 'glow', name='fuller')
        cbox(els, 8, 17.0, 18.2, 2.4, 0.9, 'head', name='tip1')
        cbox(els, 8, 18.2, 19.2, 1.4, 0.7, 'edge', name='tip2')
        cbox(els, 8, 19.2, 19.9, 0.6, 0.5, 'edge', name='tip3')
        return els
    # sky / corrupted: classic sword
    cbox(els, 8, 3.6, 4.9, 8.0, 1.8, 'trim' if style == 'sky' else 'dark', name='guard')
    cbox(els, 8, 3.8, 4.7, 1.2, 2.1, 'glow', name='gem')
    if style == 'sky':                                                          # little wings on the guard
        box(els, [3.4, 4.9, 7.5], [5.0, 6.0, 8.5], 'edge', name='wing_l'); box(els, [11.0, 4.9, 7.5], [12.6, 6.0, 8.5], 'edge', name='wing_r')
        box(els, [2.6, 5.6, 7.6], [3.6, 6.8, 8.4], 'edge', name='wing_l2'); box(els, [12.4, 5.6, 7.6], [13.4, 6.8, 8.4], 'edge', name='wing_r2')
    cbox(els, 8, 4.9, 17.2, 3.0, 1.0, 'head', name='blade')
    cbox(els, 6.25, 5.3, 17.0, 0.5, 0.6, 'edge', name='edge_l')
    cbox(els, 9.75, 5.3, 17.0, 0.5, 0.6, 'edge', name='edge_r')
    cbox(els, 8, 5.6, 15.8, 0.7, 1.1, 'glow', name='fuller')
    if style == 'corr':                                                         # jagged corruption spikes
        for y in (7.0, 11.0, 14.5):
            box(els, [5.4, y, 7.7], [6.0, y + 1.3, 8.3], 'edge', rot=(22.5, 'z', (6.0, y, 8)), name='spike')
            box(els, [10.0, y + 1.5, 7.7], [10.6, y + 2.8, 8.3], 'edge', rot=(-22.5, 'z', (10.0, y + 1.5, 8)), name='spike')
    cbox(els, 8, 17.2, 18.3, 2.2, 0.9, 'head', name='tip1')
    cbox(els, 8, 18.3, 19.2, 1.3, 0.7, 'edge', name='tip2')
    cbox(els, 8, 19.2, 19.9, 0.6, 0.5, 'edge', name='tip3')
    return els


def pickaxe(style):
    els = []
    handle(els, -2.0, 14.0, style)
    if style == 'wire': wire_coil(els, 6.5, 13.0)
    cbox(els, 8, 13.4, 16.8, 3.2, 2.6, 'dark', name='socket')
    cbox(els, 8, 14.6, 15.6, 3.5, 2.8, 'glow', name='socket_core')
    half = []
    box(half, [3.2, 14.0, 7.0], [6.4, 16.6, 9.0], 'head', name='arm1')
    box(half, [0.4, 13.0, 7.2], [3.2, 15.6, 8.8], 'head', name='arm2')
    box(half, [-1.4, 11.6, 7.4], [0.4, 14.0, 8.6], 'edge', name='arm3')
    box(half, [-2.0, 10.6, 7.6], [-0.6, 11.8, 8.4], 'edge', name='tip')
    box(half, [3.6, 16.6, 7.6], [6.0, 16.9, 8.4], 'glow' if style != 'corr' else 'edge', name='inlay')
    els += half + [mirror_x(e) for e in half]
    if style == 'heku':                                                        # a flame fin on top of the socket
        cbox(els, 8, 16.8, 18.0, 1.2, 1.0, 'trim', name='fin'); cbox(els, 8, 18.0, 18.7, 0.6, 0.6, 'glow', name='fin_tip')
    if style == 'corr':
        cbox(els, 8, 16.8, 18.4, 0.8, 0.8, 'edge', name='spike')
    return els


def axe(style):
    els = []
    handle(els, -2.0, 17.5, style)
    if style == 'wire': wire_coil(els, 6.5, 11.5)
    cbox(els, 8, 11.8, 17.2, 2.6, 2.6, 'dark', name='socket')
    box(els, [3.4, 11.4, 7.1], [6.7, 17.6, 8.9], 'head', name='blade1')
    box(els, [1.8, 10.4, 7.3], [3.4, 18.6, 8.7], 'head', name='blade2')
    box(els, [1.0, 9.8, 7.55], [1.8, 19.2, 8.45], 'edge', name='edge')
    box(els, [3.8, 14.1, 6.95], [6.4, 14.9, 9.05], 'glow', name='inlay')
    box(els, [9.3, 13.4, 7.3], [11.0, 16.0, 8.7], 'head', name='poll')
    box(els, [11.0, 14.0, 7.5], [12.0, 15.4, 8.5], 'edge', name='poll_tip')
    cbox(els, 8, 17.2, 18.0, 1.0, 1.0, 'trim', name='cap')
    if style == 'heku':
        box(els, [2.2, 18.6, 7.6], [3.2, 19.6, 8.4], 'trim', name='flame1'); box(els, [2.4, 8.8, 7.6], [3.2, 10.4, 8.4], 'trim', name='flame2')
    if style == 'corr':
        box(els, [12.0, 14.3, 7.7], [13.4, 15.1, 8.3], 'edge', name='spike')
    return els


def shovel(style):
    els = []
    handle(els, -2.0, 12.0, style)
    if style == 'wire': wire_coil(els, 6.5, 11.0)
    cbox(els, 8, 11.6, 13.2, 2.2, 2.2, 'dark', name='collar')
    cbox(els, 8, 13.2, 18.6, 5.6, 1.2, 'head', name='blade')
    cbox(els, 8, 18.6, 19.6, 4.4, 1.0, 'head', name='blade_tip')
    cbox(els, 8, 19.6, 20.3, 2.6, 0.8, 'edge', name='tip')
    cbox(els, 5.0, 13.6, 18.6, 0.4, 0.9, 'edge', name='rim_l')
    cbox(els, 11.0, 13.6, 18.6, 0.4, 0.9, 'edge', name='rim_r')
    cbox(els, 8, 13.6, 17.4, 0.8, 1.35, 'glow', name='spine')
    if style == 'heku':
        box(els, [6.6, 12.6, 6.8], [7.3, 13.6, 9.2], 'trim', name='ear_l'); box(els, [8.7, 12.6, 6.8], [9.4, 13.6, 9.2], 'trim', name='ear_r')
    return els


def hoe(style):
    els = []
    handle(els, -2.0, 17.0, style)
    if style == 'wire': wire_coil(els, 6.5, 14.0)
    box(els, [6.8, 14.4, 6.8], [9.6, 17.6, 9.2], 'dark', name='socket')
    box(els, [3.0, 15.2, 7.2], [6.8, 17.2, 8.8], 'head', name='arm')
    box(els, [1.4, 12.2, 7.3], [3.4, 17.2, 8.7], 'head', name='blade')
    box(els, [1.2, 11.4, 7.55], [3.6, 12.2, 8.45], 'edge', name='edge')
    box(els, [3.4, 15.8, 7.0], [6.4, 16.6, 9.0], 'glow', name='inlay')
    box(els, [9.6, 15.4, 7.5], [10.8, 16.6, 8.5], 'trim', name='back')
    if style == 'corr':
        box(els, [10.8, 15.6, 7.7], [12.0, 16.4, 8.3], 'edge', name='spike')
    return els


def rhalix_wires():
    """Rhalix's wires: a short grip with a coil of glowing wire looped above it (the whip, rolled up)."""
    els = []
    handle(els, -1.0, 6.0, 'wire', w=1.6)
    cbox(els, 8, 6.0, 7.0, 3.0, 2.4, 'dark', name='ring')
    cbox(els, 8, 6.2, 6.8, 3.2, 2.6, 'glow', name='ring_core')
    # two loops of wire (diamonds made of four bars turned 45 deg), one behind the other, and the loose end
    for (cy, r, z, mat) in ((12.0, 4.6, 7.35, 'wrap'), (12.4, 3.5, 8.65, 'glow'), (12.2, 2.3, 8.0, 'edge')):
        t = 0.6
        s = r * 0.7071                                                          # half side of the diamond's edges
        for (ox, oy, ang) in ((-1, 1, 45), (1, 1, -45), (1, -1, 45), (-1, -1, -45)):
            mx, my = 8 + ox * r / 2, cy + oy * r / 2
            box(els, [mx - s, my - t / 2, z - t / 2], [mx + s, my + t / 2, z + t / 2], mat, rot=(ang, 'z', (mx, my, z)), name='loop')
    box(els, [7.7, 6.8, 7.7], [8.3, 9.0, 8.3], 'wrap', name='lead')
    box(els, [10.6, 15.0, 8.7], [11.2, 18.0, 9.3], 'wrap', rot=(-22.5, 'z', (10.9, 15.0, 9.0)), name='end')
    box(els, [10.5, 18.0, 8.6], [11.3, 18.8, 9.4], 'glow', rot=(-22.5, 'z', (10.9, 15.0, 9.0)), name='end_tip')
    return els

# ------------------------------------------------------------------ DragonMineZ guns (barrel along -Z, like forms/gun_*.json)

def blaster_rifle():
    els = []
    box(els, [7.0, 7.4, 3.5], [9.0, 10.6, 11.5], 'steel', name='receiver')
    box(els, [6.9, 8.6, 4.5], [9.1, 9.2, 10.5], 'glow', name='side_glow')
    box(els, [7.2, 7.6, -1.0], [8.8, 10.2, 3.5], 'dark', name='handguard')
    for z in (-0.5, 1.0, 2.5):
        box(els, [7.05, 7.8, z], [8.95, 10.0, z + 0.5], 'glow', name='coil')
    box(els, [7.55, 8.4, -7.0], [8.45, 9.3, -1.0], 'dark', name='barrel')
    box(els, [7.2, 8.05, -8.0], [8.8, 9.65, -7.0], 'steel', name='emitter')
    box(els, [7.5, 8.35, -8.1], [8.5, 9.35, -8.0], 'glow', name='muzzle')
    box(els, [7.3, 7.0, 11.5], [8.7, 9.8, 17.5], 'dark', name='stock')
    box(els, [7.2, 6.4, 16.6], [8.8, 10.2, 17.8], 'grip', name='butt')
    box(els, [7.4, 3.2, 6.0], [8.6, 7.4, 8.4], 'grip', rot=(22.5, 'x', (8, 7.4, 7)), name='grip')
    box(els, [7.6, 6.4, 8.4], [8.4, 7.4, 9.8], 'dark', name='guard')
    box(els, [7.5, 10.6, 4.5], [8.5, 11.2, 10.5], 'dark', name='rail')
    box(els, [7.3, 11.2, 5.5], [8.7, 12.4, 9.5], 'steel', name='scope')
    box(els, [7.5, 11.4, 5.4], [8.5, 12.2, 5.5], 'glow', name='lens')
    return els


def burst_cannon():
    els = []
    box(els, [6.4, 6.8, 3.0], [9.6, 11.2, 11.0], 'steel', name='body')
    box(els, [6.3, 8.4, 4.0], [9.7, 9.6, 10.0], 'glow', name='band')
    box(els, [6.8, 7.2, -3.5], [9.2, 10.8, 3.0], 'dark', name='drum')
    for z in (-3.0, -1.0, 1.0):
        box(els, [6.6, 7.0, z], [9.4, 11.0, z + 0.8], 'glow', name='ring')
    box(els, [7.1, 7.5, -6.5], [8.9, 10.5, -3.5], 'steel', name='barrel')
    box(els, [6.7, 7.1, -7.5], [9.3, 10.9, -6.5], 'dark', name='muzzle_brake')
    box(els, [7.3, 7.7, -7.6], [8.7, 10.3, -7.5], 'glow', name='muzzle')
    box(els, [7.0, 7.4, 11.0], [9.0, 10.4, 14.0], 'dark', name='back')
    box(els, [7.4, 2.8, 6.5], [8.6, 6.8, 9.0], 'grip', rot=(22.5, 'x', (8, 6.8, 7.5)), name='grip')
    box(els, [7.4, 3.4, 0.0], [8.6, 6.8, 1.6], 'grip', name='fore_grip')
    box(els, [7.6, 6.0, 9.0], [8.4, 6.8, 10.4], 'dark', name='guard')
    box(els, [7.6, 11.2, 0.5], [8.4, 12.0, 10.0], 'dark', name='handle_top')
    box(els, [7.6, 11.2, 0.5], [8.4, 12.6, 1.3], 'dark', name='handle_post_a')
    box(els, [7.6, 11.2, 9.2], [8.4, 12.6, 10.0], 'dark', name='handle_post_b')
    box(els, [7.6, 12.6, 0.5], [8.4, 13.2, 10.0], 'steel', name='handle_bar')
    return els


def blaster_mortar():
    els = []
    box(els, [6.2, 7.0, -4.0], [9.8, 10.6, 12.0], 'steel', name='tube')
    box(els, [6.0, 6.8, -5.5], [10.0, 10.8, -4.0], 'dark', name='mouth')
    box(els, [6.6, 7.4, -5.6], [9.4, 10.2, -5.5], 'glow', name='muzzle')
    for z in (-2.5, 2.0, 6.5):
        box(els, [6.05, 6.85, z], [9.95, 10.75, z + 1.0], 'dark', name='hoop')
    box(els, [6.1, 8.4, 0.0], [9.9, 9.2, 10.0], 'glow', name='charge_strip')
    box(els, [6.6, 7.4, 12.0], [9.4, 10.2, 14.5], 'dark', name='breech')
    box(els, [7.0, 7.8, 14.5], [9.0, 9.8, 15.2], 'glow', name='breech_core')
    box(els, [7.4, 2.8, 6.5], [8.6, 7.0, 9.0], 'grip', rot=(22.5, 'x', (8, 7.0, 7.5)), name='grip')
    box(els, [7.4, 3.6, 1.0], [8.6, 7.0, 2.6], 'grip', name='fore_grip')
    box(els, [7.6, 6.2, 9.0], [8.4, 7.0, 10.4], 'dark', name='guard')
    box(els, [9.8, 9.6, 3.0], [10.8, 11.4, 7.0], 'dark', name='sight')
    box(els, [10.0, 10.4, 2.9], [10.6, 11.0, 3.0], 'glow', name='sight_lens')
    return els

# ------------------------------------------------------------------ writing

def write(name, els, mats, display, sprite):
    base = {'credit': 'Xeno Warriors Recarnet - 3D held model (gen_models.py)', 'gui_light': 'front', 'render_type': 'minecraft:cutout',
            'texture_size': [16, 16], 'textures': dict(mats, particle=mats.get('head') or mats.get('steel') or next(iter(mats.values()))), 'display': display, 'elements': els}
    os.makedirs(os.path.join(ASSETS, 'models', 'item', '3d'), exist_ok=True)
    with open(os.path.join(ASSETS, 'models', 'item', '3d', name + '.json'), 'w') as f:
        json.dump(base, f, indent=1)
    wrapper = {
        'loader': 'forge:separate_transforms',
        'gui_light': 'front',
        'render_type': 'minecraft:cutout',
        'textures': {'particle': sprite},
        'base': {'parent': NS + ':item/3d/' + name},
        'perspectives': {'gui': {'parent': 'minecraft:item/generated', 'textures': {'layer0': sprite}}},
    }
    with open(os.path.join(ASSETS, 'models', 'item', name + '.json'), 'w') as f:
        json.dump(wrapper, f, indent=1)
    MODELS.append((name, els, base['textures']))


def used(mats, els):
    keys = {f['texture'][1:] for e in els for f in e['faces'].values()}
    return {k: v for k, v in mats.items() if k in keys}


TOOLS = {'sword': sword, 'pickaxe': pickaxe, 'axe': axe, 'shovel': shovel, 'hoe': hoe}
for prefix, style, mats, sprite_dir, kinds in (
        ('heku', 'heku', HEKU, 'forms/', ('pickaxe', 'axe', 'shovel', 'hoe')),
        ('wire', 'wire', WIRE, 'forms/', ('sword', 'pickaxe', 'axe', 'shovel', 'hoe')),
        ('sky', 'sky', SKY, '', ('sword', 'pickaxe', 'axe', 'shovel', 'hoe')),
        ('corrupted', 'corr', CORR, '', ('sword', 'pickaxe', 'axe', 'shovel', 'hoe'))):
    for k in kinds:
        els = TOOLS[k](style)
        write(prefix + '_' + k, els, used(mats, els), tool_display(), T(sprite_dir + prefix + '_' + k))
els = sword('demon'); write('demonite_sword', els, used(DEMON, els), tool_display(), T('demonite_sword'))
els = rhalix_wires(); write('rhalix_wires', els, used(WIRE, els), tool_display(), T('forms/rhalix_wires'))
DMZ = dict(steel=T('3d/dmz_gun_steel'), dark=T('3d/dmz_gun_dark'), grip=T('3d/dmz_gun_grip'))
for name, fn, glow in (('blaster_rifle', blaster_rifle, 'cyan'), ('burst_cannon', burst_cannon, 'green'), ('blaster_mortar', blaster_mortar, 'orange')):
    els = fn(); mats = dict(DMZ, glow=T('3d/dmz_glow_' + glow))
    d = gun_display()
    write(name, els, used(mats, els), d, T(name))
print('wrote', len(MODELS), 'models:', ' '.join(m[0] for m in MODELS))

# ------------------------------------------------------------------ preview (front + side orthographic, painter's order)
if PREVIEW:
    from PIL import Image, ImageDraw
    import math
    os.makedirs(PREVIEW, exist_ok=True)
    cache = {}
    def colour(tex):
        if tex not in cache:
            p = os.path.join(ASSETS, 'textures', 'item', tex.split(':', 1)[1].split('item/', 1)[1] + '.png')
            im = Image.open(p).convert('RGBA').resize((1, 1), Image.BOX)
            cache[tex] = im.getpixel((0, 0))
        return cache[tex]
    def corners(e):
        f, t = e['from'], e['to']
        pts = [[x, y, z] for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])]
        r = e.get('rotation')
        if r:
            a = math.radians(r['angle']); o = r['origin']; ax = r['axis']
            out = []
            for p in pts:
                q = [p[i] - o[i] for i in range(3)]
                if ax == 'z': q = [q[0] * math.cos(a) - q[1] * math.sin(a), q[0] * math.sin(a) + q[1] * math.cos(a), q[2]]
                elif ax == 'x': q = [q[0], q[1] * math.cos(a) - q[2] * math.sin(a), q[1] * math.sin(a) + q[2] * math.cos(a)]
                else: q = [q[0] * math.cos(a) + q[2] * math.sin(a), q[1], -q[0] * math.sin(a) + q[2] * math.cos(a)]
                out.append([q[i] + o[i] for i in range(3)])
            pts = out
        return pts
    S = 5
    PER = 7
    rows = (len(MODELS) + PER - 1) // PER
    sheet = Image.new('RGBA', (PER * 2 * 30 * S, rows * 34 * S), (40, 40, 48, 255))
    for i, (name, els, texs) in enumerate(MODELS):
        for v in range(2):
            im = Image.new('RGBA', (30 * S, 34 * S), (40, 40, 48, 255))
            dr = ImageDraw.Draw(im)
            # v0: front (x right, y up, looking -z -> sort by z), v1: side (z right)
            order = sorted(els, key=lambda e: (e['from'][2] + e['to'][2]) if v == 0 else -(e['from'][0] + e['to'][0]))
            for e in order:
                tex = next(iter(e['faces'].values()))['texture'][1:]
                c = colour(texs[tex])
                pts = corners(e)
                proj = [((p[0] if v == 0 else p[2]) + 7.5, 26 - p[1]) for p in pts]
                # convex hull of projected corners
                import functools
                ps = sorted(set((round(a, 3), round(b, 3)) for a, b in proj))
                def cross(o, a, b): return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
                lo, hi = [], []
                for p in ps:
                    while len(lo) >= 2 and cross(lo[-2], lo[-1], p) <= 0: lo.pop()
                    lo.append(p)
                for p in reversed(ps):
                    while len(hi) >= 2 and cross(hi[-2], hi[-1], p) <= 0: hi.pop()
                    hi.append(p)
                hull = lo[:-1] + hi[:-1]
                if len(hull) >= 3:
                    dr.polygon([(a * S, b * S) for a, b in hull], fill=c, outline=(0, 0, 0, 255))
            sheet.paste(im, (((i % PER) * 2 + v) * 30 * S, (i // PER) * 34 * S))
        ImageDraw.Draw(sheet).text(((i % PER) * 2 * 30 * S + 4, (i // PER) * 34 * S + 4), name, fill=(255, 255, 255, 255))
    sheet.save(os.path.join(PREVIEW, 'models_preview.png'))
    print('preview', os.path.join(PREVIEW, 'models_preview.png'))
