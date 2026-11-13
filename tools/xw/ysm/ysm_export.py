"""Mikiko as a Yes Steve Model (YSM) model package, from the mod's own model (tools: run with python3 -I).

python3 -I tools/xw/ysm/ysm_export.py <out_dir> [character] [outfit_json]

Writes <out_dir>/<character>/ (ysm.json, models/main.json + arm.json, textures/<character>.png) - a YSM 2.6
folder model (Bedrock geometry). Bones follow the names YSM and EpicYSM read (AllBody > UpBody > Head, arms with
ForeArm / Hand / HandLocator, legs with LowerLeg), so with EpicYSM installed Epic Fight's moves play on it.

Coordinates: the mod's model is in Java ModelPart space (y down, the head pivot at y 0 = 24 px above the feet).
A Bedrock file keeps x and z and flips y: B = (x, 24 - y, z); rotations carry over with the same signs, in degrees.
"""
import json, math, os, sys
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..')
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/xenowarriors')
TEXDIR = os.path.join(ASSETS, 'textures/entity/character')

# atlas (texture units): the details texture (256 x 256) at the top, the 64 x 64 skin under it, the 16-unit face beside
SKIN_V, FACE_U, FACE_V, ATLAS_W, ATLAS_H, SCALE = 256, 64, 256, 256, 320, 4   # SCALE: pixels per unit in the png


def deg(r): return round(math.degrees(r), 4)


class Bone:
    def __init__(self, name, pivot, parent=None, rot=(0, 0, 0)):
        self.name, self.pivot, self.parent, self.rot, self.cubes = name, list(pivot), parent, list(rot), []

    def json(self):
        j = {"name": self.name, "pivot": [round(v, 4) for v in self.pivot]}
        if self.parent: j["parent"] = self.parent
        if any(abs(r) > 1e-6 for r in self.rot): j["rotation"] = [deg(r) for r in self.rot]
        if self.cubes: j["cubes"] = self.cubes
        return j


def bcube(jfrom, size, uv, inflate=0.0):
    """A box-UV cube from absolute Java-space corner jfrom (the min corner) and size."""
    x, y, z = jfrom
    w, h, d = size
    c = {"origin": [round(x, 4), round(24 - (y + h), 4), round(z, 4)], "size": [round(w, 4), round(h, 4), round(d, 4)],
         "uv": [round(uv[0], 4), round(uv[1], 4)]}
    if inflate: c["inflate"] = inflate
    return c


def faces_cube(jfrom, size, uv, v0, v1, inflate=0.0, top=True, bottom=True):
    """A cube with per-face UVs showing only rows v0..v1 (texture units, measured from the side faces' top edge) of a box
    layout at uv - one half of a limb cut at the elbow or knee."""
    x, y, z = jfrom
    w, h, d = size
    u, v = uv
    sv = v + d + v0
    sh = v1 - v0
    f = {"east": {"uv": [u, sv], "uv_size": [d, sh]}, "north": {"uv": [u + d, sv], "uv_size": [w, sh]},
         "west": {"uv": [u + d + w, sv], "uv_size": [d, sh]}, "south": {"uv": [u + d + w + d, sv], "uv_size": [w, sh]},
         "up": {"uv": [u + d, v], "uv_size": [w, d]}, "down": {"uv": [u + d + w, v + d], "uv_size": [w, -d]}}
    if not top: f.pop("up")
    if not bottom: f.pop("down")
    c = {"origin": [round(x, 4), round(24 - (y + h), 4), round(z, 4)], "size": [round(w, 4), round(h, 4), round(d, 4)], "uv": f}
    if inflate: c["inflate"] = inflate
    return c


def build(char='mikiko', outfit=None):
    det = json.load(open(outfit or os.path.join(ASSETS, f'models/detail/{char}.json')))
    assert det['texture_size'] == 256
    bones = {}

    def add(name, pivot_java, parent=None, rot=(0, 0, 0)):
        b = Bone(name, (pivot_java[0], 24 - pivot_java[1], pivot_java[2]), parent, rot)
        bones[name] = b
        return b

    # skeleton (Java-space pivots)
    add('AllBody', (0, 24, 0))
    add('UpBody', (0, 12, 0), 'AllBody')
    add('Head', (0, 0, 0), 'UpBody')
    add('RightArm', (-5, 2, 0), 'UpBody')
    add('RightForeArm', (-5, 6, 0), 'RightArm')
    add('RightHand', (-6, 12, 0), 'RightForeArm')
    add('RightHandLocator', (-6, 12, 0), 'RightHand')
    add('LeftArm', (5, 2, 0), 'UpBody')
    add('LeftForeArm', (5, 6, 0), 'LeftArm')
    add('LeftHand', (6, 12, 0), 'LeftForeArm')
    add('LeftHandLocator', (6, 12, 0), 'LeftHand')
    add('RightLeg', (-1.9, 12, 0), 'AllBody')
    add('RightLowerLeg', (-1.9, 18, 0), 'RightLeg')
    add('LeftLeg', (1.9, 12, 0), 'AllBody')
    add('LeftLowerLeg', (1.9, 18, 0), 'LeftLeg')

    S = (0, SKIN_V)    # skin offset in the atlas

    def skin(u, v): return (u + S[0], v + S[1])

    # ---- the skin: the player body and its outer layer (Mikiko: wide arms)
    hb = bones['Head']
    hb.cubes += [bcube((-4, -8, -4), (8, 8, 8), skin(0, 0)), bcube((-4, -8, -4), (8, 8, 8), skin(32, 0), 0.5)]
    ub = bones['UpBody']
    ub.cubes += [bcube((-4, 0, -2), (8, 12, 4), skin(16, 16)), bcube((-4, 0, -2), (8, 12, 4), skin(16, 32), 0.25)]

    def limb(upper, lower, px, py, uv_base, uv_over, x0, split):
        # Java-space: the limb is 4 x 12 x 4 at (px + x0, py, -2); cut at `split` below its pivot
        for uv, inf in ((uv_base, 0.0), (uv_over, 0.25)):
            uv = skin(*uv)
            bones[upper].cubes.append(faces_cube((px + x0, py, -2), (4, split, 4), uv, 0, split, inf, top=True, bottom=False))
            bones[lower].cubes.append(faces_cube((px + x0, py + split, -2), (4, 12 - split, 4), uv, split, 12, inf, top=False, bottom=True))

    limb('RightArm', 'RightForeArm', -5, 2, (40, 16), (40, 32), -3, 4)
    limb('LeftArm', 'LeftForeArm', 5, 2, (32, 48), (48, 48), -1, 4)
    limb('RightLeg', 'RightLowerLeg', -1.9, 12, (0, 16), (0, 32), -2, 6)
    limb('LeftLeg', 'LeftLowerLeg', 1.9, 12, (16, 48), (0, 48), -2, 6)

    # ---- the face (its own 4x texture, put in the atlas at FACE_U, FACE_V)
    fc = det.get('face')
    if fc:
        fr, fs = fc['from'], fc['size']
        fu, fv = fc['uv']
        c = {"origin": [fr[0], round(24 - (fr[1] + fs[1]), 4), round(fr[2], 4)], "size": [fs[0], fs[1], 0],
             "uv": {"north": {"uv": [FACE_U + fu, FACE_V + fv], "uv_size": [fs[0], fs[1]]}}}
        hb.cubes.append(c)

    # ---- the 3D details (hair, clothes, ...)
    owner = {'head': ('Head', None, 0), 'body': ('UpBody', None, 0), 'right_arm': ('RightArm', 'RightForeArm', 4),
             'left_arm': ('LeftArm', 'LeftForeArm', 4), 'right_leg': ('RightLeg', 'RightLowerLeg', 6),
             'left_leg': ('LeftLeg', 'LeftLowerLeg', 6)}
    jpiv = {'head': (0, 0, 0), 'body': (0, 0, 0), 'right_arm': (-5, 2, 0), 'left_arm': (5, 2, 0),
            'right_leg': (-1.9, 12, 0), 'left_leg': (1.9, 12, 0)}
    count = [0]

    def walk(node, abs_piv, parent, split, lower):
        for c in node.get('cubes', []):
            f, s = c['from'], c['size']
            jf = (abs_piv[0] + f[0], abs_piv[1] + f[1], abs_piv[2] + f[2])
            tgt = parent
            if split and lower and parent == owner_up and (f[1] + s[1] / 2.0) >= split:
                tgt = lower
            bones[tgt].cubes.append(bcube(jf, s, c['uv']))
            count[0] += 1
        for ch in node.get('children', []):
            p = ch['pivot']
            ap = (abs_piv[0] + p[0], abs_piv[1] + p[1], abs_piv[2] + p[2])
            par = parent
            if split and lower and parent == owner_up and p[1] >= split:
                par = lower
            name = f"{par}_{ch['name']}"
            i = 1
            while name in bones: name = f"{par}_{ch['name']}_{i}"; i += 1
            add(name, ap, par, ch.get('rot', (0, 0, 0)))
            walk(ch, ap, name, 0, None)

    for key, node in det['bones'].items():
        if key not in owner: continue
        up, low, split = owner[key]
        owner_up = up
        walk(node, jpiv[key], up, split, low)

    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{char}", "texture_width": ATLAS_W, "texture_height": ATLAS_H,
                        "visible_bounds_width": 3, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1.5, 0]},
        "bones": [b.json() for b in bones.values()]}]}
    return geo, bones, count[0]


def arm_geo(geo):
    """arm.json: the two arms (with everything under them) at the root, hanging straight down."""
    bones = geo["minecraft:geometry"][0]["bones"]
    keep = {"RightArm", "LeftArm"}
    out = []
    for b in bones:
        p = b.get("parent")
        if b["name"] in keep or p in keep or (p and any(x["name"] == p for x in out)):
            nb = dict(b)
            if b["name"] in keep:
                nb.pop("parent", None)
                nb.pop("rotation", None)
            out.append(nb)
    g = json.loads(json.dumps(geo))
    g["minecraft:geometry"][0]["description"]["identifier"] = "geometry.arm"
    g["minecraft:geometry"][0]["bones"] = out
    return g


def atlas(char):
    im = Image.new('RGBA', (ATLAS_W * SCALE, ATLAS_H * SCALE), (0, 0, 0, 0))
    d = Image.open(os.path.join(TEXDIR, f'{char}_details.png')).convert('RGBA')
    im.paste(d.resize((256 * SCALE, 256 * SCALE), Image.NEAREST), (0, 0))
    s = Image.open(os.path.join(TEXDIR, f'{char}.png')).convert('RGBA')
    im.paste(s.resize((64 * SCALE, 64 * SCALE), Image.NEAREST), (0, SKIN_V * SCALE))
    fp = os.path.join(TEXDIR, f'{char}_face.png')
    if os.path.exists(fp):
        f = Image.open(fp).convert('RGBA')          # 64 px = 16 units: exactly SCALE
        im.paste(f, (FACE_U * SCALE, FACE_V * SCALE))
    return im


if __name__ == '__main__':
    out = sys.argv[1]
    char = sys.argv[2] if len(sys.argv) > 2 else 'mikiko'
    geo, bones, n = build(char)
    d = os.path.join(out, char)
    for sub in ('models', 'textures'): os.makedirs(os.path.join(d, sub), exist_ok=True)
    json.dump(geo, open(os.path.join(d, 'models/main.json'), 'w'), indent=1)
    json.dump(arm_geo(geo), open(os.path.join(d, 'models/arm.json'), 'w'), indent=1)
    atlas(char).save(os.path.join(d, f'textures/{char}.png'))
    ysm = {"spec": 2,
           "metadata": {"name": char.capitalize() + " (Xeno Warriors Recarnet)",
                        "tips": "Mikiko from the Xeno Warriors Recarnet mod. Characters by Recarnetwork Gacha.",
                        "license": {"type": "All Rights Reserved", "desc": "Made for the Xeno Warriors Recarnet mod; not for redistribution."},
                        "authors": [{"name": "Xeno Warriors Recarnet", "role": "Model"}]},
           "files": {"player": {"model": {"main": "models/main.json", "arm": "models/arm.json"},
                                "texture": [f"textures/{char}.png"]}}}
    json.dump(ysm, open(os.path.join(d, 'ysm.json'), 'w'), indent=2, ensure_ascii=False)
    print(f"{char}: {len(bones)} bones, {n} detail cubes -> {d}")
