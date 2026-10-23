"""Shared helpers: cube specs, hair generator, box-UV atlas packing/painting, bbmodel writer."""
import json, base64, uuid, math, random, io
from PIL import Image

def U():
    return str(uuid.uuid4())

def hexc(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

class Model:
    """Collects detail cubes per bone. Coordinates are Blockbench space (y up, feet at 0, front = -z,
    character's right = +x)."""
    BONES = {
        'Head': [0, 24, 0], 'Body': [0, 24, 0],
        'RightArm': [5, 22, 0], 'LeftArm': [-5, 22, 0],
        'RightLeg': [1.9, 12, 0], 'LeftLeg': [-1.9, 12, 0],
    }

    def __init__(self, name):
        self.name = name
        self.cubes = []          # detail cubes
        self.extra_groups = []   # (name, origin, rotation, parent) for wings etc.

    def add(self, bone, name, frm, to, color, rot=None, origin=None, shade=None):
        frm = [round(v, 3) for v in frm]
        to = [round(v, 3) for v in to]
        lo = [min(a, b) for a, b in zip(frm, to)]
        hi = [max(a, b) for a, b in zip(frm, to)]
        self.cubes.append(dict(bone=bone, name=name, frm=lo, to=hi, color=color,
                               rot=rot or [0, 0, 0],
                               origin=origin or [(a + b) / 2 for a, b in zip(lo, hi)],
                               shade=shade))

    def group(self, name, origin, rotation, parent='Body'):
        self.extra_groups.append(dict(name=name, origin=origin, rot=rotation, parent=parent))

    def mirror_x(self, bone_from, bone_to, filt=None):
        """Copy cubes of one bone to its mirror bone (flip x)."""
        for c in list(self.cubes):
            if c['bone'] != bone_from or (filt and not filt(c)):
                continue
            frm = [-c['to'][0], c['frm'][1], c['frm'][2]]
            to = [-c['frm'][0], c['to'][1], c['to'][2]]
            o = [-c['origin'][0], c['origin'][1], c['origin'][2]]
            r = [c['rot'][0], -c['rot'][1], -c['rot'][2]]
            self.add(bone_to, c['name'].replace('Right', 'Left').replace('R ', 'L '), frm, to, c['color'], r, o, c['shade'])


# ---------------------------------------------------------------- hair
def hash01(*a):
    h = 0
    for v in a:
        h = (h * 73856093) ^ (int(round(v * 7)) * 19349663)
        h &= 0xFFFFFFFF
    return ((h * 2654435761) & 0xFFFFFFFF) / 0xFFFFFFFF

def gen_hair(center, radii, noise, hairline, color_fn, carve=None, ymax=99, seed=1, seg=3):
    """Voxel hair shell around the head. Returns list of (frm, to, color)."""
    cx, cy, cz = center
    cells = set()
    for x in range(-9, 9):
        for z in range(-9, 9):
            n = (hash01(x, z, seed) - 0.5) * 2 * noise
            for y in range(22, 40):
                px, py, pz = x + .5, y + .5, z + .5
                if py > ymax:
                    continue
                d = ((px - cx) / (radii[0] * (1 + n))) ** 2 + ((py - cy) / (radii[1] * (1 + n * .6))) ** 2 + \
                    ((pz - cz) / (radii[2] * (1 + n))) ** 2
                if d > 1:
                    continue
                if -4 < px < 4 and 24 < py < 32 and -4 < pz < 4:
                    continue  # inside the head
                if py < hairline(px, pz):
                    continue
                if carve and carve(x, y, z):
                    continue
                cells.add((x, y, z))

    def filled(c):
        x, y, z = c
        return c in cells or (-4 <= x < 4 and 24 <= y < 32 and -4 <= z < 4)

    surf = [c for c in cells if any(not filled((c[0] + dx, c[1] + dy, c[2] + dz))
                                     for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]
    # merge vertical runs of the same colour into columns
    cols = {}
    for x, y, z in surf:
        cols.setdefault((x, z), []).append(y)
    out = []
    for (x, z), ys in cols.items():
        ys.sort()
        run = [ys[0]]
        def flush(run):
            col = color_fn(x, run[0], z)
            out.append(([x, run[0], z], [x + 1, run[-1] + 1, z + 1], col))
        for y in ys[1:]:
            if y == run[-1] + 1 and len(run) < seg and color_fn(x, y, z) == color_fn(x, run[0], z):
                run.append(y)
            else:
                flush(run)
                run = [y]
        flush(run)
    return out


# ---------------------------------------------------------------- atlas
def pack_and_paint(cubes, size=256, seed=3):
    rnd = random.Random(seed)
    items = []
    for i, c in enumerate(cubes):
        w, h, d = [max(1, math.ceil(c['to'][k] - c['frm'][k] - 1e-6)) for k in range(3)]
        items.append((i, 2 * d + 2 * w, d + h, (w, h, d)))
    items.sort(key=lambda t: -t[2])
    while True:
        x = y = shelf = 0
        ok = True
        placed = {}
        for i, rw, rh, dims in items:
            if x + rw > size:
                x = 0; y += shelf + 1; shelf = 0
            if y + rh > size:
                ok = False; break
            placed[i] = (x, y)
            x += rw + 1
            shelf = max(shelf, rh)
        if ok:
            break
        size *= 2
    img = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    px = img.load()
    for i, rw, rh, (w, h, d) in items:
        u, v = placed[i]
        cubes[i]['uv'] = [u, v]
        base = hexc(cubes[i]['color'])
        shade = cubes[i].get('shade')
        for yy in range(rh):
            for xx in range(rw):
                f = 1.0 + (rnd.random() - 0.5) * 0.10
                if shade == 'grad':          # lighter towards the top
                    f *= 1.08 - 0.25 * (yy / max(1, rh - 1))
                if shade == 'edge' and (xx in (0, rw - 1) or yy in (0, rh - 1)):
                    f *= 0.8
                col = tuple(max(0, min(255, int(c * f))) for c in base)
                px[u + xx, v + yy] = col + (255,)
    return img, size


# ---------------------------------------------------------------- bbmodel
def box_faces(u, v, w, h, d, tex):
    return {"north": {"uv": [u + d, v + d, u + d + w, v + d + h], "texture": tex},
            "east": {"uv": [u, v + d, u + d, v + d + h], "texture": tex},
            "south": {"uv": [u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h], "texture": tex},
            "west": {"uv": [u + d + w, v + d, u + 2 * d + w, v + d + h], "texture": tex},
            "up": {"uv": [u + d + w, v + d, u + d, v], "texture": tex},
            "down": {"uv": [u + d + 2 * w, v, u + d + w, v + d], "texture": tex}}

def bb_cube(name, frm, to, uv, tex, inflate=0, rot=None, origin=None):
    w, h, d = [to[i] - frm[i] for i in range(3)]
    e = {"name": name, "box_uv": True, "rescale": False, "locked": False, "render_order": "default",
         "allow_mirror_modeling": True, "from": frm, "to": to, "autouv": 0, "color": 0,
         "origin": origin or [0, 0, 0], "uv_offset": uv, "inflate": inflate,
         "faces": box_faces(uv[0], uv[1], w, h, d, tex), "type": "cube", "uuid": U()}
    if rot and any(rot):
        e["rotation"] = rot
    return e

def bb_group(name, origin, children, rot=None):
    return {"name": name, "origin": origin, "rotation": rot or [0, 0, 0], "color": 0, "uuid": U(),
            "export": True, "mirror_uv": False, "isOpen": False, "locked": False, "visibility": True,
            "autouv": 0, "children": children}

def png_b64(img):
    b = io.BytesIO(); img.save(b, 'PNG')
    return 'data:image/png;base64,' + base64.b64encode(b.getvalue()).decode()

def bb_texture(name, img, i):
    return {"path": "", "name": name, "folder": "entity", "namespace": "xenowarriors", "id": str(i),
            "width": img.width, "height": img.height, "uv_width": img.width, "uv_height": img.height,
            "particle": False, "render_mode": "default", "render_sides": "auto", "visible": True,
            "internal": True, "saved": False, "uuid": U(), "source": png_b64(img)}

FACE_FROM, FACE_TO = [-4, 24, -4.08], [4, 32, -4.08]

BASE = [  # name, bone, from, to, uv, inflate, is_overlay
    ("Head", "Head", [-4, 24, -4], [4, 32, 4], [0, 0], 0, False),
    ("Hat", "Head", [-4, 24, -4], [4, 32, 4], [32, 0], 0.5, True),
    ("Body", "Body", [-4, 12, -2], [4, 24, 2], [16, 16], 0, False),
    ("Jacket", "Body", [-4, 12, -2], [4, 24, 2], [16, 32], 0.25, True),
    ("RightArm", "RightArm", [4, 12, -2], [8, 24, 2], [40, 16], 0, False),
    ("RightSleeve", "RightArm", [4, 12, -2], [8, 24, 2], [40, 32], 0.25, True),
    ("LeftArm", "LeftArm", [-8, 12, -2], [-4, 24, 2], [32, 48], 0, False),
    ("LeftSleeve", "LeftArm", [-8, 12, -2], [-4, 24, 2], [48, 48], 0.25, True),
    ("RightLeg", "RightLeg", [0, 0, -2], [4, 12, 2], [0, 16], 0, False),
    ("RightPants", "RightLeg", [0, 0, -2], [4, 12, 2], [0, 32], 0.25, True),
    ("LeftLeg", "LeftLeg", [-4, 0, -2], [0, 12, 2], [16, 48], 0, False),
    ("LeftPants", "LeftLeg", [-4, 0, -2], [0, 12, 2], [0, 48], 0.25, True),
]

def write_bbmodel(model, skin_img, detail_img, path, keep_overlays, face_img=None):
    elements, kids = [], {b: [] for b in Model.BONES}
    for g in model.extra_groups:
        kids[g['name']] = []
    for name, bone, frm, to, uv, inf, overlay in BASE:
        if overlay and name not in keep_overlays:
            continue
        e = bb_cube(name, frm, to, uv, 0, inf)
        elements.append(e); kids[bone].append(e['uuid'])
    for c in model.cubes:
        e = bb_cube(c['name'], c['frm'], c['to'], c['uv'], 1, 0, c['rot'], c['origin'])
        elements.append(e); kids[c['bone']].append(e['uuid'])
    if face_img is not None:
        e = bb_cube('Face HD', FACE_FROM, FACE_TO, [0, 0], 2)
        elements.append(e); kids['Head'].append(e['uuid'])
    groups = {}
    for b, o in Model.BONES.items():
        groups[b] = bb_group(b, o, kids[b])
    for g in model.extra_groups:
        grp = bb_group(g['name'], g['origin'], kids[g['name']], g['rot'])
        groups[g['parent']]['children'].append(grp)
    outliner = [groups[b] for b in Model.BONES]
    data = {"meta": {"format_version": "4.10", "model_format": "free", "box_uv": True},
            "name": model.name, "model_identifier": model.name, "visible_box": [1, 2, 0],
            "resolution": {"width": 64, "height": 64}, "elements": elements, "outliner": outliner,
            "textures": [bb_texture(model.name + ".png", skin_img, 0),
                         bb_texture(model.name + "_details.png", detail_img, 1)]}
    if face_img is not None:
        t = bb_texture(model.name + "_face.png", face_img, 2)
        t["uv_width"] = t["uv_height"] = 16
        data["textures"].append(t)
    json.dump(data, open(path, 'w'))
    return len(elements)


# ---------------------------------------------------------------- hair locks
def orient_dir(d):
    """Rotation [rx, 0, rz] (degrees) that points a cube's +y axis along d."""
    dx, dy, dz = d
    n = math.sqrt(dx * dx + dy * dy + dz * dz)
    dx, dy, dz = dx / n, dy / n, dz / n
    return [math.degrees(math.asin(max(-1, min(1, dz)))), 0, math.degrees(math.atan2(-dx, dy))]

def lock(m, bone, root, d, length, width, thick, colors, wide='x', taper=(0.62, 0.3), tip_len=(0.38, 0.22), name='Lock'):
    """A hair lock growing from `root` along `d`: a main piece plus 2 narrowing tip pieces.
    wide='x' -> the lock is wide along its local x, thin along z (lies flat when pointing front/back);
    wide='z' -> wide along local z (lies flat against the sides of the head).
    colors = (main, tip, point)."""
    rot = orient_dir(d)
    x, y, z = root
    segs = [(0, length * (1 - tip_len[0] - tip_len[1]), 1.0),
            (length * (1 - tip_len[0] - tip_len[1]), length * (1 - tip_len[1]), taper[0]),
            (length * (1 - tip_len[1]), length, taper[1])]
    for i, (a, b, f) in enumerate(segs):
        w = width * f
        t = thick * (1 if i == 0 else (0.85 if i == 1 else 0.7))
        hx, hz = (w / 2, t / 2) if wide == 'x' else (t / 2, w / 2)
        m.add(bone, name, [x - hx, y + a, z - hz], [x + hx, y + b, z + hz], colors[min(i, len(colors) - 1)], rot, [x, y, z])

def ray_end(root, d, length):
    n = math.sqrt(sum(c * c for c in d))
    return [root[i] + d[i] / n * length for i in range(3)]


def strand(m, bone, root, d, seg_lens, widths, thick, colors, wide='x', name='Strand'):
    """A hanging strand built from stacked segments along direction d (all share one rotation),
    e.g. twisted dreads or layered locks. seg_lens/widths/colors are per segment."""
    rot = orient_dir(d)
    x, y, z = root
    a = 0.0
    for i, ln in enumerate(seg_lens):
        w = widths[i]
        t = thick if wide == 'x' else w
        hx, hz = (w / 2, thick / 2) if wide == 'x' else (thick / 2, w / 2)
        m.add(bone, name, [x - hx, y + a, z - hz], [x + hx, y + a + ln, z + hz], colors[i % len(colors)], rot, [x, y, z])
        a += ln
