"""Skylander wing styles: each a full wing model (right wing built, left mirrored), same mount as Rhalix's wings,
painted in light greys so the game can tint them per Skylander. Exports models/detail/npc/wing_<style>.json and
textures/entity/npc/wing_<style>_details.png into the mod, plus a preview sheet."""
import sys, json, math, random, copy
sys.path.insert(0, '/home/claude/xw/detail')
from core import Model, pack_and_paint
import export_mod
from PIL import Image

MOD = '/home/claude/xeno-warriors-mod/src/main/resources/assets/xenowarriors/'
O = (3.4, 21.8, 3.4)                     # wing root (Blockbench space, right wing; +x = outward, +y = up)
W1, W2, W3, W4, BONE = '#f6f6f6', '#e2e2e2', '#cbcbcb', '#b2b2b2', '#9c9c9c'

def seg(m, root, ang, length, width, thick=0.7, col=W2, name='F', z=None):
    """A box from root along direction `ang` (degrees, 0 = straight out, 90 = up), `width` across it."""
    x, y, zz = root
    zz = O[2] if z is None else z
    m.add('RightWing', name, [x, y - width / 2, zz], [x + length, y + width / 2, zz + thick], col, [0, 0, ang], [x, y, zz])
    a = math.radians(ang)
    return (x + math.cos(a) * length, y + math.sin(a) * length, zz)

def along(p, q, t):
    return tuple(p[i] + (q[i] - p[i]) * t for i in range(3))

def arm(m, pts_angles):
    """Wing bones: a chain of segments; returns the joint points."""
    pts = [O]
    for ang, ln, w in pts_angles:
        pts.append(seg(m, pts[-1], ang, ln, w, 1.0, BONE, 'Bone'))
    return pts

def feathered(m, pts, n, lens, angs, width, cols, z_off=0.0, rnd=None, ragged=0.0):
    """Feathers hanging off the arm: n spread along it (base to tip), length/angle interpolated."""
    total = len(pts) - 1
    for i in range(n):
        t = (i + 0.5) / n
        k = min(total - 1, int(t * total)); f = t * total - k
        root = along(pts[k], pts[k + 1], f)
        ln = lens[0] + (lens[1] - lens[0]) * t
        ang = angs[0] + (angs[1] - angs[0]) * t
        if rnd and ragged:
            if rnd.random() < ragged * 0.35: continue                  # a feather torn out
            ln *= 1 - rnd.random() * ragged * 0.5
            ang += rnd.uniform(-8, 8) * ragged
        seg(m, (root[0], root[1], O[2] + z_off), ang, ln, width, 0.6, cols[i % len(cols)])
        seg(m, (root[0], root[1], O[2] + z_off), ang, ln * 0.82 + 0.6, width * 0.55, 0.62, W1 if cols[0] != W1 else W2)   # quill/shine

def angel(m, small=1.0, z=0.0, down=0):
    pts = arm(m, [(58 - down, 10 * small, 1.6), (22 - down, 9 * small, 1.4), (-6 - down, 6 * small, 1.1)])
    feathered(m, pts, 14, (12 * small, 17 * small), (-86 - down, -38 - down), 2.4 * small, (W1, W2, W1, W3), z)
    feathered(m, pts, 9, (5 * small, 8 * small), (-75 - down, -40 - down), 2.6 * small, (W2, W1), z - 0.5)

def hawk(m):
    pts = arm(m, [(40, 9, 1.5), (14, 9, 1.3), (0, 5, 1.0)])
    feathered(m, pts[:3], 6, (5, 7), (-80, -60), 2.4, (W2, W3, W2), 0)
    for i, (ang, ln) in enumerate(((-38, 13), (-28, 14), (-18, 15), (-8, 14), (2, 12))):   # long fingered primaries
        seg(m, (pts[-1][0] - 1.5 + i * 0.4, pts[-1][1], O[2]), ang, ln, 1.4, 0.6, (W3, W2, W4)[i % 3])
    feathered(m, pts, 8, (4, 5), (-70, -30), 2.5, (W1, W2), -0.5)

def falcon(m):
    pts = arm(m, [(30, 9, 1.4), (8, 10, 1.2), (-6, 5, 1.0)])
    feathered(m, pts, 12, (7, 12), (-60, -16), 1.8, (W3, W2, W3, W4), 0)
    feathered(m, pts, 8, (4, 5), (-55, -22), 2.2, (W2, W1), -0.5)

def bat(m):
    pts = arm(m, [(55, 9, 1.2), (20, 7, 1.0)])
    wrist = pts[-1]
    fingers = [(48, 12), (20, 14), (-10, 13), (-42, 11)]
    tips = []
    for ang, ln in fingers:
        tips.append(seg(m, wrist, ang, ln, 0.6, 0.9, BONE, 'Bone'))
    # membrane: thin panels fanned between the fingers, and down to the body
    edge = [O] + [tips[3]] + [tips[2]] + [tips[1]] + [tips[0]]
    for a0, a1, ln0, ln1 in ((48, 20, 12, 14), (20, -10, 14, 13), (-10, -42, 13, 11)):
        for k in range(5):
            t = (k + 0.5) / 5
            seg(m, wrist, a0 + (a1 - a0) * t, (ln0 + (ln1 - ln0) * t) * 0.92, 2.6, 0.3, W3 if k % 2 else W4, 'Membrane')
    for k in range(6):                                                 # from the arm down toward the back
        t = (k + 0.5) / 6
        root = along(pts[0], pts[2], t)
        seg(m, root, -70 - 15 * t, 6 + 6 * t, 2.8, 0.3, W4 if k % 2 else W3, 'Membrane')

def butterfly(m):
    # upper wing: a big rounded lobe; lower: a smaller teardrop; spots
    for k in range(9):
        ang = 70 - k * 12
        ln = 12 + 4 * math.sin(math.pi * k / 8)
        seg(m, O, ang, ln, 3.0, 0.4, (W1, W2, W1)[k % 3], 'Scale')
    for k in range(6):
        ang = -45 - k * 9
        ln = 8 + 2.5 * math.sin(math.pi * k / 5)
        seg(m, O, ang, ln, 2.6, 0.4, (W2, W3)[k % 2], 'Scale')
    for ang, d, s in ((40, 10, 1.6), (15, 11, 1.4), (-55, 6, 1.2)):        # eye spots
        a = math.radians(ang)
        c = (O[0] + math.cos(a) * d, O[1] + math.sin(a) * d)
        m.add('RightWing', 'Spot', [c[0] - s, c[1] - s, O[2] - 0.12], [c[0] + s, c[1] + s, O[2] + 0.52], W4)
    seg(m, O, 35, 14, 0.4, 0.5, BONE, 'Vein'); seg(m, O, -10, 13, 0.4, 0.5, BONE, 'Vein'); seg(m, O, -60, 8, 0.4, 0.5, BONE, 'Vein')

def crystal(m):
    rnd = random.Random(4)
    pts = arm(m, [(30, 7, 1.4), (5, 7, 1.2)])
    for i in range(11):
        t = i / 10
        root = along(pts[1], pts[2], t) if t > 0.3 else along(pts[0], pts[1], t / 0.3)
        ang = -85 + 70 * t + rnd.uniform(-6, 6)
        ln = 6 + 9 * math.sin(math.pi * (0.2 + 0.7 * t))
        seg(m, root, ang, ln, 1.8 - 0.4 * t, 1.2, (W1, W2, '#ffffff')[i % 3], 'Shard')
        seg(m, root, ang + 4, ln * 0.6, 1.0, 1.4, W1, 'Shard')
    for ang, ln in ((60, 7), (40, 9), (18, 8)):
        seg(m, pts[2], ang, ln, 1.2, 1.2, '#ffffff', 'Shard')

def seraph(m):
    angel(m, 0.85, 0.0, 0)
    angel(m, 0.7, -0.7, 55)        # a second, smaller pair angled down underneath

def tattered(m):
    rnd = random.Random(9)
    pts = arm(m, [(55, 10, 1.6), (20, 9, 1.4), (-8, 6, 1.1)])
    feathered(m, pts, 14, (12, 17), (-86, -38), 2.4, (W3, W4, W3, W2), 0, rnd, 1.0)
    feathered(m, pts, 9, (5, 8), (-75, -40), 2.6, (W4, W3), -0.5, rnd, 0.8)

def export(spec, path):
    E = export_mod
    out = {'texture_size': spec['size'], 'bones': {}}
    nodes = {}
    for bb, (mod, bbp, jp) in E.BONES.items():
        nodes[bb] = {'pivot': jp, 'cubes': [], 'children': [], '_origin': bbp}
        out['bones'][mod] = nodes[bb]
    for g in spec['groups']:
        parent = nodes[g['parent']]
        node = {'name': E.WINGS.get(g['name'], g['name'].lower()), 'pivot': E.rel(parent['_origin'], g['origin']),
                'rot': E.rot(g['rot']), 'cubes': [], 'children': [], '_origin': g['origin']}
        parent['children'].append(node); nodes[g['name']] = node
    for i, c in enumerate(spec['cubes']):
        parent = nodes[c['bone']]
        frm, size = E.box(c['origin'], c['frm'], c['to'])
        parent['children'].append({'name': f'c{i}', 'pivot': E.rel(parent['_origin'], c['origin']), 'rot': E.rot(c['rot']),
                                   'cubes': [{'uv': c['uv'], 'from': frm, 'size': size}], 'children': []})
    def strip(n):
        n.pop('_origin', None)
        for ch in n['children']: strip(ch)
    for n in out['bones'].values(): strip(n)
    json.dump(out, open(path, 'w'), separators=(',', ':'))

def kyrie(m):
    # Kyrie's: a compact dark wing held out to the side - a solid spread with a spiky lower edge and dark spots along the top
    arm(m, [(22, 6.0, 1.3), (4, 5.5, 1.1)])
    for k in range(10):
        ang = 30 - k * 9.5
        ln = (11.5 if k % 2 == 0 else 8.5) * (1.0 - 0.3 * max(0, k - 5) / 4)
        seg(m, O, ang, ln * 0.82, 2.8, 0.5, W2 if k % 2 else W3, 'Membrane')
        tip_root = (O[0] + math.cos(math.radians(ang)) * ln * 0.8, O[1] + math.sin(math.radians(ang)) * ln * 0.8, O[2])
        seg(m, tip_root, ang - 6, ln * 0.22, 1.1, 0.5, W3, 'Spike')                 # the spiky points
    for ang, d in ((26, 6.5), (18, 8.0), (10, 8.8), (2, 8.0)):                       # the dark spots near the top edge
        a = math.radians(ang); c = (O[0] + math.cos(a) * d, O[1] + math.sin(a) * d)
        m.add('RightWing', 'Spot', [c[0] - 0.6, c[1] - 0.45, O[2] - 0.12], [c[0] + 0.6, c[1] + 0.45, O[2] + 0.62], '#4a4a4a')

def demon(m):
    # a demon's bat wing (Ai-oni): bony arm, three long fingers, red membrane scalloped between them
    pts = arm(m, [(50, 7, 1.2), (24, 6, 1.0)])
    wrist = pts[-1]
    fingers = [(46, 10), (12, 12), (-24, 11)]
    for ang, ln in fingers: seg(m, wrist, ang, ln, 0.6, 0.9, BONE, 'Bone')
    for a0, a1, l0, l1 in ((46, 12, 10, 12), (12, -24, 12, 11)):
        for k in range(6):
            t = (k + 0.5) / 6
            seg(m, wrist, a0 + (a1 - a0) * t, (l0 + (l1 - l0) * t) * (0.86 - 0.12 * math.sin(math.pi * t)), 2.4, 0.3, W3 if k % 2 else W2, 'Membrane')
    for k in range(6):
        t = (k + 0.5) / 6
        root = along(pts[0], pts[2], t)
        seg(m, root, -72 - 14 * t, 4 + 6 * t, 2.6, 0.3, W2 if k % 2 else W3, 'Membrane')

class _Scaled:
    """Wraps a Model so a style comes out smaller (everything shrunk toward the wing root)."""
    def __init__(self, m, k): self.m, self.k = m, k
    def add(self, bone, name, frm, to, col, rot=None, origin=None, *a, **kw):
        sc = lambda p: [O[i] + (p[i] - O[i]) * self.k if i < 2 else p[i] for i in range(3)]
        return self.m.add(bone, name, sc(frm), sc(to), col, rot, sc(origin) if origin else origin, *a, **kw)
def scaled(fn, k): return lambda m: fn(_Scaled(m, k))

STYLES = {'angel': angel, 'hawk': hawk, 'falcon': falcon, 'bat': bat, 'butterfly': butterfly,
          'crystal': crystal, 'seraph': seraph, 'tattered': tattered,
          'kyrie': kyrie, 'demon': demon, 'angel_small': scaled(angel, 0.6), 'falcon_small': scaled(falcon, 0.5), 'hawk_small': scaled(hawk, 0.6)}

def build(style):
    m = Model('wing_' + style)
    m.group('RightWing', list(O), [0, -20, 0], 'Body')
    m.group('LeftWing', [-O[0], O[1], O[2]], [0, 20, 0], 'Body')
    STYLES[style](m)
    m.mirror_x('RightWing', 'LeftWing')
    return m

if __name__ == '__main__':
    export_mod.BANGS_UNDER_HOOD['x'] = False
    for style in (sys.argv[1:] or STYLES):
        m = build(style)
        img, size = pack_and_paint(m.cubes)
        spec = {'size': size, 'cubes': m.cubes, 'groups': m.extra_groups, 'keep': []}
        json.dump(spec, open(f'/home/claude/xw/wings/wing_{style}_spec.json', 'w'))
        img.save(MOD + f'textures/entity/npc/wing_{style}_details.png')
        export(spec, MOD + f'models/detail/npc/wing_{style}.json')
        print(style, len(m.cubes), 'cubes', size)
